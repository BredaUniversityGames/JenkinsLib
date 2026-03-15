/**
 * CMake build module.
 * Usage: cmake.build(), cmake.test(), cmake.package(), cmake.workflow()
 *
 * Automatically detects CMakePresets.json from the workspace (previous build)
 * and populates pipeline parameters with available presets. Falls back to
 * manual generator/config/platform parameters when no presets are found.
 *
 * Preset lists can also be provided explicitly via overrides:
 *   cmake.build(CMAKE_BUILD_PRESETS: ['debug-build', 'release-build'])
 */

import groovy.transform.Field
import com.buas.ModuleRegistry
import com.buas.build.CMake

@Field def _impl = null

private def getImpl() {
    if (_impl == null) {
        _impl = new CMake(this)
    }
    return _impl
}

def build(Map overrides = [:]) {
    def impl = getImpl()
    ModuleRegistry.register(
        category: 'build',
        name: 'CMake Build',
        params: impl.buildPipelineParams(overrides),
        execute: { params, ctx -> impl.execute(params, ctx) },
        hasCleanup: false
    )
}

def test(Map overrides = [:]) {
    def impl = getImpl()
    ModuleRegistry.register(
        category: 'test',
        name: 'CMake Test',
        params: impl.testPipelineParams(overrides),
        execute: { params, ctx ->
            def resultsDir = "${env.WORKSPACE}\\TestResults"
            bat(label: "Create test results directory", script: "if not exist \"${resultsDir}\" mkdir \"${resultsDir}\"")

            if (params.CMAKE_TEST_PRESET) {
                // Configure and build for the test preset if its configure preset
                // differs from what was already built
                def testConfigPreset = impl.getTestConfigurePreset(params.CMAKE_TEST_PRESET)
                def buildConfigPreset = ctx.cmakeBuildPreset ? impl.getBuildConfigurePreset(ctx.cmakeBuildPreset) : null
                if (testConfigPreset && testConfigPreset != buildConfigPreset) {
                    impl.configureAndBuildForTest(preset: params.CMAKE_TEST_PRESET)
                }
                impl.testWithPreset(
                    preset: params.CMAKE_TEST_PRESET,
                    resultsFile: "${resultsDir}\\ctest_results.xml"
                )
            } else {
                if (!ctx.buildEngine) {
                    error "cmake.test() requires cmake.build() or cmake.workflow() to run first"
                }
                def buildDir = ctx.cmakeBuildDir ?: params.CMAKE_BUILD_DIR ?: 'build'
                def buildConfig = ctx.buildConfig ?: params.CMAKE_CONFIG ?: 'Debug'
                def result = bat(label: "Run CTest",
                    script: "cd /d \"${buildDir}\" && ctest -C ${buildConfig} --output-on-failure --output-junit \"${resultsDir}\\ctest_results.xml\"",
                    returnStatus: true)
                if (result != 0) {
                    unstable "Some CTest tests did not pass!"
                }
            }
            junit testResults: 'TestResults/ctest_results.xml', allowEmptyResults: true
        },
        hasCleanup: false
    )
}

def pack(Map overrides = [:]) {
    def impl = getImpl()
    def packParams = impl.packagePipelineParams(overrides)
    ModuleRegistry.register(
        category: 'pack',
        name: 'CMake Package',
        params: packParams,
        execute: { params, ctx ->
            if (params.CMAKE_PACKAGE_PRESET) {
                // Configure+build if not already done for this configure preset
                def packConfigPreset = impl.getPackageConfigurePreset(params.CMAKE_PACKAGE_PRESET)
                def buildConfigPreset = ctx.cmakeBuildPreset ? impl.getBuildConfigurePreset(ctx.cmakeBuildPreset) : null
                if (packConfigPreset && packConfigPreset != buildConfigPreset) {
                    impl.configureAndBuildForPack(preset: params.CMAKE_PACKAGE_PRESET)
                }
                ctx.outputDir = impl.packageWithPreset(preset: params.CMAKE_PACKAGE_PRESET)
            } else {
                if (!ctx.buildEngine) {
                    error "cmake.pack() requires cmake.build() to run first when not using presets"
                }
                def buildDir = ctx.cmakeBuildDir ?: params.CMAKE_BUILD_DIR ?: 'build'
                def buildConfig = ctx.buildConfig ?: params.CMAKE_CONFIG ?: 'Debug'
                ctx.outputDir = impl.pack(
                    buildDir:  buildDir,
                    config:    buildConfig,
                    generator: params.CMAKE_CPACK_GENERATOR,
                    args:      params.CMAKE_CPACK_ARGS
                )
            }
        },
        hasCleanup: false
    )
}

def workflow(Map overrides = [:]) {
    def impl = getImpl()
    def workflowParams = impl.workflowPipelineParams(overrides)
    if (!workflowParams) {
        log.warning("cmake.workflow() requires workflow presets in CMakePresets.json")
        return
    }
    ModuleRegistry.register(
        category: 'build',
        name: 'CMake Workflow',
        params: workflowParams,
        execute: { params, ctx ->
            def packagePath = impl.workflowWithPreset(preset: params.CMAKE_WORKFLOW_PRESET)
            ctx.buildEngine = 'CMake'
            if (packagePath) {
                ctx.outputDir = packagePath
            }
        },
        hasCleanup: false
    )
}

// Direct-use methods
def configure(Map config) { getImpl().configure(config) }
def configureWithPreset(Map config) { getImpl().configureWithPreset(config) }
def buildProject(Map config) { getImpl().build(config) }
def buildWithPreset(Map config) { getImpl().buildWithPreset(config) }
def install(Map config) { getImpl().install(config) }
def testWithPreset(Map config) { getImpl().testWithPreset(config) }
def packageProject(Map config) { getImpl().pack(config) }
def packageWithPreset(Map config) { getImpl().packageWithPreset(config) }
def workflowWithPreset(Map config) { getImpl().workflowWithPreset(config) }
