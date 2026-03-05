package com.buas.build

import groovy.json.JsonSlurper

/**
 * CMake build module.
 * Supports both manual flag-based builds and CMakePresets.json preset-based builds.
 * When CMakePresets.json is found in the workspace (from a previous build) or preset
 * lists are provided via overrides, preset choice parameters are shown instead of
 * manual generator/config/platform parameters.
 */
class CMake implements Serializable {
    def steps
    private Map presets = [:]

    CMake(steps) {
        this.steps = steps
    }

    /**
     * Discover presets from overrides or by reading CMakePresets.json from the workspace.
     * Returns a map with keys: configurePresets, buildPresets, testPresets, packagePresets, workflowPresets.
     */
    Map discoverPresets(String sourceDir, Map overrides) {
        presets = [
            configurePresets: overrides.CMAKE_CONFIGURE_PRESETS as List ?: [],
            buildPresets:     overrides.CMAKE_BUILD_PRESETS as List ?: [],
            testPresets:      overrides.CMAKE_TEST_PRESETS as List ?: [],
            packagePresets:   overrides.CMAKE_PACKAGE_PRESETS as List ?: [],
            workflowPresets:  overrides.CMAKE_WORKFLOW_PRESETS as List ?: []
        ]

        if (presets.values().any { it }) {
            return presets
        }

        try {
            steps.node('Windows') {
                def wsBase = "C:\\Jenkins\\${steps.env.JOB_NAME}"
                def presetsDir = sourceDir == '.' ? wsBase : "${wsBase}\\${sourceDir}"
                def presetsFile = "${presetsDir}\\CMakePresets.json"
                def output = steps.bat(script: "@if exist \"${presetsFile}\" type \"${presetsFile}\"", returnStdout: true).trim()
                if (output) {
                    def json = new JsonSlurper().parseText(output)
                    presets.configurePresets = extractPresetNames(json.configurePresets)
                    presets.buildPresets     = extractPresetNames(json.buildPresets)
                    presets.testPresets      = extractPresetNames(json.testPresets)
                    presets.packagePresets   = extractPresetNames(json.packagePresets)
                    presets.workflowPresets  = extractPresetNames(json.workflowPresets)
                }
            }
        } catch (Exception e) {
            steps.echo "Note: Could not read CMakePresets.json: ${e.message}"
        }

        return presets
    }

    private static List<String> extractPresetNames(List presetList) {
        if (!presetList) return []
        return presetList.findAll { !(it.hidden ?: false) }.collect { it.name }
    }

    boolean hasPresets() {
        return presets.configurePresets || presets.buildPresets
    }

    Map getPresets() {
        return presets
    }

    def pipelineParams(Map overrides = [:]) {
        def prev = steps.params ?: [:]
        def sourceDir = overrides.CMAKE_SOURCE_DIR ?: prev.CMAKE_SOURCE_DIR ?: '.'

        discoverPresets(sourceDir, overrides)

        def paramList = [
            steps.string(name: 'CMAKE_SOURCE_DIR', defaultValue: sourceDir,
                   description: 'Path to CMakeLists.txt directory')
        ]

        if (hasPresets()) {
            if (presets.configurePresets) {
                paramList << steps.choice(name: 'CMAKE_CONFIGURE_PRESET',
                    choices: presets.configurePresets,
                    description: 'CMake configure preset (from CMakePresets.json)')
            }
            if (presets.buildPresets) {
                paramList << steps.choice(name: 'CMAKE_BUILD_PRESET',
                    choices: presets.buildPresets,
                    description: 'CMake build preset (from CMakePresets.json)')
            }
        } else {
            paramList.addAll([
                steps.string(name: 'CMAKE_BUILD_DIR', defaultValue: overrides.CMAKE_BUILD_DIR ?: prev.CMAKE_BUILD_DIR ?: 'build',
                       description: 'Build output directory'),
                steps.choice(name: 'CMAKE_GENERATOR',
                       choices: overrides.CMAKE_GENERATOR_CHOICES ?: ['Ninja', 'Visual Studio 17 2022', 'Visual Studio 16 2019', 'Unix Makefiles'],
                       description: 'CMake generator'),
                steps.string(name: 'CMAKE_CONFIG', defaultValue: overrides.CMAKE_CONFIG ?: prev.CMAKE_CONFIG ?: 'Debug',
                       description: 'Build configuration (Debug, Release, RelWithDebInfo, MinSizeRel)'),
                steps.string(name: 'CMAKE_PLATFORM', defaultValue: overrides.CMAKE_PLATFORM ?: prev.CMAKE_PLATFORM ?: 'x64',
                       description: 'Target platform')
            ])
        }

        paramList.addAll([
            steps.string(name: 'CMAKE_ARGS', defaultValue: overrides.CMAKE_ARGS ?: prev.CMAKE_ARGS ?: '',
                   description: 'Additional CMake configure arguments'),
            steps.string(name: 'CMAKE_BUILD_ARGS', defaultValue: overrides.CMAKE_BUILD_ARGS ?: prev.CMAKE_BUILD_ARGS ?: '',
                   description: 'Additional CMake build arguments')
        ])

        return paramList
    }

    def testPipelineParams(Map overrides = [:]) {
        if (presets.testPresets) {
            return [
                steps.choice(name: 'CMAKE_TEST_PRESET',
                    choices: presets.testPresets,
                    description: 'CTest preset (from CMakePresets.json)')
            ]
        }
        return [
            steps.choice(name: 'CMAKE_TEST_FRAMEWORK',
                   choices: overrides.CMAKE_TEST_FRAMEWORK_CHOICES ?: ['CTest', 'GoogleTest'],
                   description: 'Test framework to use'),
            steps.string(name: 'CMAKE_TEST_EXECUTABLE', defaultValue: overrides.CMAKE_TEST_EXECUTABLE ?: '',
                   description: 'Path to test executable (GoogleTest only)')
        ]
    }

    def packagePipelineParams() {
        if (!presets.packagePresets) return []
        return [
            steps.choice(name: 'CMAKE_PACKAGE_PRESET',
                choices: presets.packagePresets,
                description: 'CPack preset (from CMakePresets.json)')
        ]
    }

    def workflowPipelineParams() {
        if (!presets.workflowPresets) return []
        return [
            steps.choice(name: 'CMAKE_WORKFLOW_PRESET',
                choices: presets.workflowPresets,
                description: 'CMake workflow preset (from CMakePresets.json)')
        ]
    }

    def execute(Map params, Map ctx) {
        if (params.CMAKE_CONFIGURE_PRESET) {
            configureWithPreset(
                preset: params.CMAKE_CONFIGURE_PRESET,
                args:   params.CMAKE_ARGS
            )
            buildWithPreset(
                preset:    params.CMAKE_BUILD_PRESET,
                buildArgs: params.CMAKE_BUILD_ARGS
            )
            ctx.cmakeConfigurePreset = params.CMAKE_CONFIGURE_PRESET
            ctx.cmakeBuildPreset = params.CMAKE_BUILD_PRESET
        } else {
            configure(
                sourceDir: params.CMAKE_SOURCE_DIR,
                buildDir:  params.CMAKE_BUILD_DIR,
                generator: params.CMAKE_GENERATOR,
                config:    params.CMAKE_CONFIG,
                platform:  params.CMAKE_PLATFORM,
                args:      params.CMAKE_ARGS
            )
            build(
                buildDir:  params.CMAKE_BUILD_DIR,
                config:    params.CMAKE_CONFIG,
                buildArgs: params.CMAKE_BUILD_ARGS
            )
            ctx.buildConfig = params.CMAKE_CONFIG
            ctx.buildPlatform = params.CMAKE_PLATFORM
            ctx.cmakeBuildDir = params.CMAKE_BUILD_DIR
        }
        ctx.buildEngine = 'CMake'
    }

    // -- Preset-based methods --

    def configureWithPreset(Map config) {
        def preset = config.preset
        def extraArgs = config.args ?: ''

        def cmd = "cmake --preset \"${preset}\""
        if (extraArgs) {
            cmd += " ${extraArgs}"
        }
        steps.bat(label: "CMake configure (preset: ${preset})", script: cmd)
    }

    def buildWithPreset(Map config) {
        def preset = config.preset
        def buildArgs = config.buildArgs ?: ''

        def cmd = "cmake --build --preset \"${preset}\""
        if (buildArgs) {
            cmd += " ${buildArgs}"
        }
        steps.bat(label: "CMake build (preset: ${preset})", script: cmd)
    }

    def testWithPreset(Map config) {
        def preset = config.preset

        steps.bat(label: "CTest (preset: ${preset})",
            script: "ctest --preset \"${preset}\" --output-on-failure --output-junit \"${config.resultsFile ?: 'test_results.xml'}\"")
    }

    def packageWithPreset(Map config) {
        def preset = config.preset

        steps.bat(label: "CPack (preset: ${preset})",
            script: "cpack --preset \"${preset}\"")
    }

    def workflowWithPreset(Map config) {
        def preset = config.preset

        steps.bat(label: "CMake workflow (preset: ${preset})",
            script: "cmake --workflow --preset \"${preset}\"")
    }

    // -- Manual methods --

    def configure(Map config) {
        def sourceDir = config.sourceDir ?: '.'
        def buildDir = config.buildDir ?: 'build'
        def generator = config.generator ?: 'Ninja'
        def buildConfig = config.config ?: 'Debug'
        def platform = config.platform ?: 'x64'
        def extraArgs = config.args ?: ''

        def cmd = "cmake -S \"${sourceDir}\" -B \"${buildDir}\" -G \"${generator}\" -DCMAKE_BUILD_TYPE=${buildConfig}"

        if (generator.startsWith('Visual Studio')) {
            cmd += " -A ${platform}"
        }

        if (extraArgs) {
            cmd += " ${extraArgs}"
        }

        steps.bat(label: "CMake configure", script: cmd)
    }

    def build(Map config) {
        def buildDir = config.buildDir ?: 'build'
        def buildConfig = config.config ?: 'Debug'
        def buildArgs = config.buildArgs ?: ''

        def cmd = "cmake --build \"${buildDir}\" --config ${buildConfig}"

        if (buildArgs) {
            cmd += " ${buildArgs}"
        }

        steps.bat(label: "CMake build", script: cmd)
    }

    def install(Map config) {
        def buildDir = config.buildDir ?: 'build'
        def buildConfig = config.config ?: 'Debug'
        def prefix = config.prefix ?: ''

        def cmd = "cmake --install \"${buildDir}\" --config ${buildConfig}"

        if (prefix) {
            cmd += " --prefix \"${prefix}\""
        }

        steps.bat(label: "CMake install", script: cmd)
    }
}
