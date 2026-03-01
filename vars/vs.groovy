/**
 * Visual Studio / MSBuild module.
 * Usage: vs.build(), vs.test()
 *
 * Testing supports CTest and GoogleTest frameworks.
 */

import groovy.transform.Field
import com.buas.ModuleRegistry
import com.buas.build.VS

@Field def _impl = null

def build(Map overrides = [:]) {
    _impl = new VS(this)
    ModuleRegistry.register(
        category: 'build',
        name: 'VS Build',
        params: _impl.pipelineParams(overrides),
        execute: { params, ctx -> _impl.execute(params, ctx) },
        hasCleanup: false
    )
}

def test(Map overrides = [:]) {
    def testParams = [
        choice(name: 'VS_TEST_FRAMEWORK',
               choices: overrides.VS_TEST_FRAMEWORK_CHOICES ?: ['CTest', 'GoogleTest'],
               description: 'VS test framework to use'),
        string(name: 'VS_TEST_EXECUTABLE', defaultValue: overrides.VS_TEST_EXECUTABLE ?: '',
               description: 'Path to test executable (GoogleTest) or CTest build dir')
    ]

    ModuleRegistry.register(
        category: 'test',
        name: 'VS Test',
        params: testParams,
        execute: { params, ctx ->
            def framework = params.VS_TEST_FRAMEWORK
            def executable = params.VS_TEST_EXECUTABLE
            def buildConfig = ctx.buildConfig ?: params.VS_CONFIG ?: 'Debug'
            def resultsDir = "${env.WORKSPACE}\\TestResults"

            bat(label: "Create test results directory", script: "if not exist \"${resultsDir}\" mkdir \"${resultsDir}\"")

            switch (framework) {
                case 'CTest':
                    def result = bat(label: "Run CTest",
                        script: "cd /d \"${executable}\" && ctest -C ${buildConfig} --output-on-failure --output-junit \"${resultsDir}\\ctest_results.xml\"",
                        returnStatus: true)
                    junit testResults: 'TestResults/ctest_results.xml', allowEmptyResults: true
                    if (result != 0) {
                        unstable "Some CTest tests did not pass!"
                    }
                    break

                case 'GoogleTest':
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
        },
        hasCleanup: false
    )
}
