package com.buas.build

import groovy.json.JsonSlurper
import com.buas.vcs.Git
import com.buas.vcs.Perforce

/**
 * CMake build module.
 * Supports both manual flag-based builds and CMakePresets.json preset-based builds.
 * Discovers presets from the workspace or by fetching from the VCS remote (Git or
 * Perforce, detected from pipeline params). Falls back to manual generator/config/
 * platform parameters when no presets are found.
 */
class CMake implements Serializable {
    def steps
    private Map presets = [:]
    private static final VSWHERE = '%ProgramFiles(x86)%\\Microsoft Visual Studio\\Installer\\vswhere.exe'
    private static final VCVARSALL_ARCH = [
        'x64':   'x64',
        'Win32': 'x86',
        'ARM64': 'amd64_arm64'
    ]

    private static String vsEnvPrefix(String platform) {
        def arch = VCVARSALL_ARCH[platform] ?: 'x64'
        "for /f \"tokens=*\" %%i in ('\"${VSWHERE}\" -latest -property installationPath') do " +
        "call \"%%i\\VC\\Auxiliary\\Build\\vcvarsall.bat\" ${arch} >nul 2>&1"
    }

    CMake(steps) {
        this.steps = steps
    }

    /**
     * Discover presets from overrides or by reading CMakePresets.json.
     *
     * Discovery order:
     * 1. Override lists (e.g. cmake.build(CMAKE_BUILD_PRESETS: ['debug', 'release']))
     * 2. Read from workspace (fast path — works when workspace persists between builds)
     * 3. Fetch from VCS remote (Git or Perforce, detected from pipeline params)
     *
     * Returns a map with keys: configurePresets, buildPresets, testPresets, packagePresets, workflowPresets.
     */
    Map discoverPresets(String srcDir, Map overrides) {
        def sourceDir = srcDir ?: '.'
        presets = [
            configurePresets:   [],
            buildPresets:       [],
            testPresets:        [],
            packagePresets:     [],
            workflowPresets:    [],
            buildConfigureMap:        [:],
            buildConfigurationMap:   [:],
            testConfigureMap:        [:],
            packageConfigureMap:     [:],
            packageConfigurationsMap: [:],
            packageOutputDirMap:     [:],
            configureBinaryDirMap:   [:]
        ]

        if (applyOverridePresets(overrides)) {
            return presets
        }

        def presetsPath = sourceDir == '.' ? 'CMakePresets.json' : "${sourceDir}/CMakePresets.json"

        def presetsContent = ''
        try {
            presetsContent = fetchFileContent(presetsPath, overrides)
        } catch (Exception e) {
            steps.echo "Note: Could not discover presets from ${presetsPath}: ${e.message}"
        }

        if (presetsContent) {
            parsePresets(presetsContent)
        }

        return presets
    }

    private boolean applyOverridePresets(Map overrides) {
        def found = false
        ['configure', 'build', 'test', 'package', 'workflow'].each { type ->
            def key = "CMAKE_${type.toUpperCase()}_PRESETS"
            if (overrides[key]) {
                presets["${type}Presets"] = overrides[key] as List<String>
                found = true
            }
        }
        return found
    }

    private String fetchFileContent(String path, Map overrides) {
        def content = ''
        steps.node('Windows') {
            content = fetchFromVcs(path, overrides)
            if (!content) {
                steps.ws("C:\\Jenkins\\${steps.env.JOB_NAME}") {
                    if (steps.fileExists(path)) {
                        content = steps.readFile(file: path, encoding: 'UTF-8').trim()
                    }
                }
            }
        }
        return content
    }

    private String fetchFromVcs(String path, Map overrides) {
        def prev = steps.params ?: [:]

        def repoUrl = overrides.GIT_REPO_URL ?: prev.GIT_REPO_URL ?: ''
        if (repoUrl) {
            return new Git(steps).fetchFile(
                path:          path,
                url:           repoUrl,
                credentialsId: overrides.GIT_CREDENTIALS_ID ?: prev.GIT_CREDENTIALS_ID ?: '',
                branch:        overrides.GIT_BRANCH ?: prev.GIT_BRANCH ?: 'main'
            )
        }

        def p4Cred = overrides.P4_CREDENTIAL ?: prev.P4_CREDENTIAL ?: ''
        if (p4Cred) {
            return new Perforce(steps).fetchFile(
                path:       path,
                credential: p4Cred,
                host:       overrides.P4_HOST ?: prev.P4_HOST ?: '',
                workspace:  overrides.P4_WORKSPACE ?: prev.P4_WORKSPACE ?: ''
            )
        }

        return ''
    }

    private void parsePresets(String content) {
        def json = new JsonSlurper().parseText(content)
        presets.configurePresets = extractPresetNames(json.configurePresets)
        presets.buildPresets     = extractPresetNames(json.buildPresets)

        // Store configure preset → binaryDir mapping
        def configureBinaryDirMap = [:]
        def rawConfigurePresets = json.configurePresets ?: []
        for (int i = 0; i < rawConfigurePresets.size(); i++) {
            def cp = rawConfigurePresets[i]
            if (!(cp.hidden ?: false) && cp.binaryDir) {
                configureBinaryDirMap[cp.name] = cp.binaryDir
            }
        }
        presets.configureBinaryDirMap = configureBinaryDirMap
        presets.testPresets      = extractPresetNames(json.testPresets)
        presets.packagePresets   = extractPresetNames(json.packagePresets)
        presets.workflowPresets  = extractPresetNames(json.workflowPresets)

        // Store build preset → configurePreset and configuration mappings
        def buildConfigMap = [:]
        def buildConfigurationMap = [:]
        def rawBuildPresets = json.buildPresets ?: []
        for (int i = 0; i < rawBuildPresets.size(); i++) {
            def bp = rawBuildPresets[i]
            if (!(bp.hidden ?: false) && bp.configurePreset) {
                buildConfigMap[bp.name] = bp.configurePreset
                if (bp.configuration) {
                    buildConfigurationMap[bp.name] = bp.configuration
                }
            }
        }
        presets.buildConfigureMap = buildConfigMap
        presets.buildConfigurationMap = buildConfigurationMap

        // Store test preset → configurePreset mapping for configure+build+test flow
        def testConfigMap = [:]
        def rawTestPresets = json.testPresets ?: []
        for (int i = 0; i < rawTestPresets.size(); i++) {
            def tp = rawTestPresets[i]
            if (!(tp.hidden ?: false) && tp.configurePreset) {
                testConfigMap[tp.name] = tp.configurePreset
            }
        }
        presets.testConfigureMap = testConfigMap

        // Store package preset → configurePreset and configurations mappings
        def packageConfigMap = [:]
        def packageConfigurationsMap = [:]
        def packageOutputDirMap = [:]
        def rawPackagePresets = json.packagePresets ?: []
        for (int i = 0; i < rawPackagePresets.size(); i++) {
            def pp = rawPackagePresets[i]
            if (!(pp.hidden ?: false) && pp.configurePreset) {
                packageConfigMap[pp.name] = pp.configurePreset
                if (pp.configurations) {
                    packageConfigurationsMap[pp.name] = pp.configurations as List<String>
                }
                if (pp.outputDirectory) {
                    packageOutputDirMap[pp.name] = pp.outputDirectory
                }
            }
        }
        presets.packageConfigureMap = packageConfigMap
        presets.packageConfigurationsMap = packageConfigurationsMap
        presets.packageOutputDirMap = packageOutputDirMap
    }

    private static List<String> extractPresetNames(List presetList) {
        if (!presetList) return []
        return presetList.findAll { !(it.hidden ?: false) }.collect { it.name }
    }

    private def batWithVsEnv(Map args) {
        def arch = args.arch ?: 'x64'
        def returnStdout = args.returnStdout ?: false
        def script = "${vsEnvPrefix(arch)}\n${args.script}"
        return steps.bat(label: args.label, script: script, returnStdout: returnStdout)
    }

    private static List<String> reorderChoices(List<String> choices, String previous) {
        if (!previous || !choices.contains(previous)) return choices
        return [previous] + choices.findAll { it != previous }
    }

    boolean hasPresets() {
        return presets.buildPresets as boolean
    }

    Map getPresets() {
        return presets
    }

    def buildPipelineParams(Map overrides = [:]) {
        def prev = steps.params ?: [:]
        def sourceDir = overrides.CMAKE_SOURCE_DIR ?: prev.CMAKE_SOURCE_DIR ?: '.'

        discoverPresets(sourceDir, overrides)

        def paramList = [
            steps.string(name: 'CMAKE_SOURCE_DIR', defaultValue: sourceDir,
                   description: 'Path to CMakeLists.txt directory')
        ]

        if (hasPresets()) {
            paramList << steps.choice(name: 'CMAKE_BUILD_PRESET',
                choices: reorderChoices(presets.buildPresets, prev.CMAKE_BUILD_PRESET),
                description: 'CMake build preset (from CMakePresets.json)')
        } else {
            paramList.addAll([
                steps.string(name: 'CMAKE_BUILD_DIR', defaultValue: overrides.CMAKE_BUILD_DIR ?: prev.CMAKE_BUILD_DIR ?: 'build',
                       description: 'Build output directory'),
                steps.choice(name: 'CMAKE_GENERATOR',
                       choices: reorderChoices(overrides.CMAKE_GENERATOR_CHOICES ?: ['Ninja', 'Visual Studio 18 2026', 'Visual Studio 17 2022', 'Visual Studio 16 2019', 'Unix Makefiles'], prev.CMAKE_GENERATOR),
                       description: 'CMake generator'),
                steps.choice(name: 'CMAKE_CONFIG',
                       choices: reorderChoices(overrides.CMAKE_CONFIG_CHOICES ?: ['Debug', 'Release', 'RelWithDebInfo', 'MinSizeRel'], prev.CMAKE_CONFIG),
                       description: 'Build configuration'),
                steps.choice(name: 'CMAKE_PLATFORM',
                       choices: reorderChoices(overrides.CMAKE_PLATFORM_CHOICES ?: ['x64', 'Win32', 'ARM64'], prev.CMAKE_PLATFORM),
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
        def prev = steps.params ?: [:]
        if (!presets.buildPresets && !presets.testPresets) {
            def sourceDir = overrides.CMAKE_SOURCE_DIR ?: prev.CMAKE_SOURCE_DIR ?: '.'
            discoverPresets(sourceDir, overrides)
        }
        if (presets.testPresets) {
            return [
                steps.choice(name: 'CMAKE_TEST_PRESET',
                    choices: reorderChoices(presets.testPresets, prev.CMAKE_TEST_PRESET),
                    description: 'CTest preset (from CMakePresets.json)')
            ]
        }
        return []
    }

    def packagePipelineParams(Map overrides = [:]) {
        def prev = steps.params ?: [:]
        if (!presets.buildPresets && !presets.packagePresets) {
            def sourceDir = overrides.CMAKE_SOURCE_DIR ?: prev.CMAKE_SOURCE_DIR ?: '.'
            discoverPresets(sourceDir, overrides)
        }
        if (presets.packagePresets) {
            return [
                steps.choice(name: 'CMAKE_PACKAGE_PRESET',
                    choices: reorderChoices(presets.packagePresets, prev.CMAKE_PACKAGE_PRESET),
                    description: 'CPack preset (from CMakePresets.json)')
            ]
        }
        return [
            steps.choice(name: 'CMAKE_CPACK_GENERATOR',
                choices: reorderChoices(['ZIP', 'NSIS', 'WIX', 'NuGet', '7Z', 'TGZ'], prev.CMAKE_CPACK_GENERATOR),
                description: 'CPack generator'),
            steps.string(name: 'CMAKE_CPACK_ARGS', defaultValue: prev.CMAKE_CPACK_ARGS ?: '',
                description: 'Additional CPack arguments')
        ]
    }

    def workflowPipelineParams(Map overrides = [:]) {
        def prev = steps.params ?: [:]
        if (!presets.buildPresets && !presets.workflowPresets) {
            def sourceDir = overrides.CMAKE_SOURCE_DIR ?: prev.CMAKE_SOURCE_DIR ?: '.'
            discoverPresets(sourceDir, overrides)
        }
        if (!presets.workflowPresets) return []
        return [
            steps.choice(name: 'CMAKE_WORKFLOW_PRESET',
                choices: reorderChoices(presets.workflowPresets, prev.CMAKE_WORKFLOW_PRESET),
                description: 'CMake workflow preset (from CMakePresets.json)')
        ]
    }

    def execute(Map params, Map ctx) {
        if (params.CMAKE_BUILD_PRESET) {
            def configPreset = presets.buildConfigureMap[params.CMAKE_BUILD_PRESET]
            if (configPreset) {
                configureWithPreset(
                    preset: configPreset,
                    args:   params.CMAKE_ARGS
                )
            }
            buildWithPreset(
                preset:    params.CMAKE_BUILD_PRESET,
                buildArgs: params.CMAKE_BUILD_ARGS
            )
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
                platform:  params.CMAKE_PLATFORM,
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
        batWithVsEnv(label: "CMake configure (preset: ${preset})", script: cmd)
    }

    def buildWithPreset(Map config) {
        def preset = config.preset
        def buildArgs = config.buildArgs ?: ''

        def cmd = "cmake --build --preset \"${preset}\""
        if (buildArgs) {
            cmd += " ${buildArgs}"
        }
        batWithVsEnv(label: "CMake build (preset: ${preset})", script: cmd)
    }

    String getBuildConfigurePreset(String buildPreset) {
        return presets.buildConfigureMap[buildPreset]
    }

    String getTestConfigurePreset(String testPreset) {
        return presets.testConfigureMap[testPreset]
    }

    String getPackageConfigurePreset(String packagePreset) {
        return presets.packageConfigureMap[packagePreset]
    }

    String getPackageOutputDir(String packagePreset) {
        def configPreset = presets.packageConfigureMap[packagePreset]
        def outputDir = presets.packageOutputDirMap[packagePreset]
        if (outputDir) {
            return resolveCMakeVars(outputDir, packagePreset)
        }
        // CPack defaults to the configure preset's binaryDir
        if (configPreset) {
            def binaryDir = presets.configureBinaryDirMap[configPreset]
            if (binaryDir) return resolveCMakeVars(binaryDir, configPreset)
        }
        return null
    }

    private static String resolveCMakeVars(String path, String presetName) {
        return path
            .replace('${sourceDir}', '.')
            .replace('${presetName}', presetName)
            .replace('${sourceParentDir}', '..')
    }

    private List<String> findBuildPresets(String configPreset, List<String> configurations) {
        def configMap = presets.buildConfigureMap
        def configurationMap = presets.buildConfigurationMap
        def result = []
        def keys = configMap.keySet().toList()
        for (int i = 0; i < keys.size(); i++) {
            def bp = keys[i]
            if (configMap[bp] != configPreset) continue
            if (configurations) {
                def bpConfig = configurationMap[bp]
                if (bpConfig && !configurations.contains(bpConfig)) continue
            }
            result.add(bp)
        }
        return result
    }

    private void configureAndBuildIfNeeded(String configPreset, List<String> configurations = null) {
        configureWithPreset(preset: configPreset)

        def buildPresets = findBuildPresets(configPreset, configurations)
        if (buildPresets) {
            for (int i = 0; i < buildPresets.size(); i++) {
                buildWithPreset(preset: buildPresets[i])
            }
        } else {
            steps.error "No build preset found for configure preset '${configPreset}'" +
                (configurations ? " with configurations ${configurations}" : '') +
                ' in CMakePresets.json'
        }
    }

    def configureAndBuildForTest(Map config) {
        def configPreset = presets.testConfigureMap[config.preset]
        if (!configPreset) {
            steps.error "Test preset '${config.preset}' has no configurePreset defined in CMakePresets.json"
        }
        configureAndBuildIfNeeded(configPreset)
    }

    def configureAndBuildForPack(Map config) {
        def configPreset = presets.packageConfigureMap[config.preset]
        if (!configPreset) {
            steps.error "Package preset '${config.preset}' has no configurePreset defined in CMakePresets.json"
        }
        def configurations = presets.packageConfigurationsMap[config.preset]
        configureAndBuildIfNeeded(configPreset, configurations)
    }

    def testWithPreset(Map config) {
        def preset = config.preset

        steps.bat(label: "CTest (preset: ${preset})",
            script: "ctest --preset \"${preset}\" --output-on-failure --output-junit \"${config.resultsFile ?: 'test_results.xml'}\"")
    }

    String packageWithPreset(Map config) {
        def preset = config.preset

        def output = steps.bat(label: "CPack (preset: ${preset})",
            script: "@cpack --preset \"${preset}\"",
            returnStdout: true).trim()

        return parsePackagePath(output)
    }

    String workflowWithPreset(Map config) {
        def preset = config.preset

        def output = batWithVsEnv(label: "CMake workflow (preset: ${preset})",
            script: "@cmake --workflow --preset \"${preset}\"",
            returnStdout: true).trim()

        return parsePackagePath(output)
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

        batWithVsEnv(label: "CMake configure", script: cmd, arch: platform)
    }

    def build(Map config) {
        def buildDir = config.buildDir ?: 'build'
        def buildConfig = config.config ?: 'Debug'
        def platform = config.platform ?: 'x64'
        def buildArgs = config.buildArgs ?: ''

        def cmd = "cmake --build \"${buildDir}\" --config ${buildConfig}"

        if (buildArgs) {
            cmd += " ${buildArgs}"
        }

        batWithVsEnv(label: "CMake build", script: cmd, arch: platform)
    }

    String pack(Map config) {
        def buildDir = config.buildDir ?: 'build'
        def buildConfig = config.config ?: 'Debug'
        def generator = config.generator ?: 'ZIP'
        def extraArgs = config.args ?: ''

        def cmd = "@cpack -G \"${generator}\" -B \"${buildDir}\" -C ${buildConfig}"

        if (extraArgs) {
            cmd += " ${extraArgs}"
        }

        def output = steps.bat(label: "CPack (${generator})", script: cmd,
            returnStdout: true).trim()

        return parsePackagePath(output)
    }

    private static final String CPACK_PKG_PREFIX = 'CPack: - package: '
    private static final String CPACK_PKG_SUFFIX = ' generated.'

    private static String parsePackagePath(String cpackOutput) {
        def lines = cpackOutput.split('\n')
        for (int i = 0; i < lines.size(); i++) {
            def line = lines[i].trim()
            int start = line.indexOf(CPACK_PKG_PREFIX)
            int end = line.indexOf(CPACK_PKG_SUFFIX)
            if (start >= 0 && end > start) {
                return line.substring(start + CPACK_PKG_PREFIX.length(), end).trim().replace('/', '\\')
            }
        }
        return null
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
