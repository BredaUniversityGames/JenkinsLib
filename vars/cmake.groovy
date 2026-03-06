/**
 * CMake build module.
 * Usage: cmake.build(), cmake.test(), cmake.package(), cmake.workflow()
 *
 * Automatically detects CMakePresets.json from the workspace (previous build)
 * and populates pipeline parameters with available presets. Falls back to
 * manual generator/config/platform parameters when no presets are found.
 *
 * Preset lists can also be provided explicitly via overrides:
 *   cmake.build(CMAKE_CONFIGURE_PRESETS: ['debug', 'release'],
 *               CMAKE_BUILD_PRESETS: ['debug-build', 'release-build'])
 */

import groovy.transform.Field
import com.buas.ModuleRegistry
import com.buas.build.CMake

@Field def _impl = null

def build(Map overrides = [:]) {
    _impl = new CMake(this)
    ModuleRegistry.register(
        category: 'build',
        name: 'CMake Build',
        params: _impl.buildPipelineParams(overrides),
        execute: { params, ctx -> _impl.execute(params, ctx) },
        hasCleanup: false
    )
}

def test(Map overrides = [:]) {
    ModuleRegistry.register(
        category: 'test',
        name: 'CMake Test',
        params: _impl.testPipelineParams(overrides),
        execute: { params, ctx ->
            if (params.CMAKE_TEST_PRESET) {
                def resultsDir = "${env.WORKSPACE}\\TestResults"
                bat(label: "Create test results directory", script: "if not exist \"${resultsDir}\" mkdir \"${resultsDir}\"")

                _impl.testWithPreset(
                    preset: params.CMAKE_TEST_PRESET,
                    resultsFile: "${resultsDir}\\ctest_results.xml"
                )
                junit testResults: 'TestResults/ctest_results.xml', allowEmptyResults: true
            } else {
                def framework = params.CMAKE_TEST_FRAMEWORK
                def buildDir = ctx.cmakeBuildDir ?: params.CMAKE_BUILD_DIR ?: 'build'
                def buildConfig = ctx.buildConfig ?: params.CMAKE_CONFIG ?: 'Debug'
                def resultsDir = "${env.WORKSPACE}\\TestResults"

                bat(label: "Create test results directory", script: "if not exist \"${resultsDir}\" mkdir \"${resultsDir}\"")

                switch (framework) {
                    case 'CTest':
                        def result = bat(label: "Run CTest",
                            script: "cd /d \"${buildDir}\" && ctest -C ${buildConfig} --output-on-failure --output-junit \"${resultsDir}\\ctest_results.xml\"",
                            returnStatus: true)
                        junit testResults: 'TestResults/ctest_results.xml', allowEmptyResults: true
                        if (result != 0) {
                            unstable "Some CTest tests did not pass!"
                        }
                        break

                    case 'GoogleTest':
                        def executable = params.CMAKE_TEST_EXECUTABLE
                        def result = bat(label: "Run GoogleTest",
                            script: "\"${executable}\" --gtest_output=xml:\"${resultsDir}\\gtest_results.xml\"",
                            returnStatus: true)
                        junit testResults: 'TestResults/gtest_results.xml', allowEmptyResults: true
                        if (result != 0) {
                            unstable "Some GoogleTest tests did not pass!"
                        }
                        break

                    default:
                        log.warning("Unknown test framework: ${framework}. Supported: CTest, GoogleTest")
                }
            }
        },
        hasCleanup: false
    )
}

def pack(Map overrides = [:]) {
    def packParams = _impl.packagePipelineParams()
    if (!packParams) {
        log.warning("cmake.pack() requires package presets in CMakePresets.json")
        return
    }
    ModuleRegistry.register(
        category: 'deploy',
        name: 'CMake Package',
        params: packParams,
        execute: { params, ctx ->
            _impl.packageWithPreset(preset: params.CMAKE_PACKAGE_PRESET)
        },
        hasCleanup: false
    )
}

def workflow(Map overrides = [:]) {
    def workflowParams = _impl.workflowPipelineParams()
    if (!workflowParams) {
        log.warning("cmake.workflow() requires workflow presets in CMakePresets.json")
        return
    }
    ModuleRegistry.register(
        category: 'build',
        name: 'CMake Workflow',
        params: workflowParams,
        execute: { params, ctx ->
            _impl.workflowWithPreset(preset: params.CMAKE_WORKFLOW_PRESET)
            ctx.buildEngine = 'CMake'
        },
        hasCleanup: false
    )
}

// Direct-use methods
def configure(Map config) { _impl.configure(config) }
def configureWithPreset(Map config) { _impl.configureWithPreset(config) }
def buildProject(Map config) { _impl.build(config) }
def buildWithPreset(Map config) { _impl.buildWithPreset(config) }
def install(Map config) { _impl.install(config) }
def testWithPreset(Map config) { _impl.testWithPreset(config) }
def packageWithPreset(Map config) { _impl.packageWithPreset(config) }
def workflowWithPreset(Map config) { _impl.workflowWithPreset(config) }
