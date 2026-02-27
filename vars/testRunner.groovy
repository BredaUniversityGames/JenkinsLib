/**
 * Generic test runner for VS/C++ projects.
 * Supports CTest and GoogleTest. Parses JUnit XML for Jenkins reporting.
 */

def run(Map config) {
    def framework = config.framework
    def executable = config.executable
    def buildConfig = config.config ?: 'Debug'
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
}
