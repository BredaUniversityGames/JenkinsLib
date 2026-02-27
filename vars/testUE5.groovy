/**
 * UE5 Test module.
 * Runs Unreal Engine automated tests and publishes JUnit results.
 * Delegates to buildUE5's existing test methods.
 */

// ── Module registration ──

def call(Map overrides = [:]) {
    buasPipeline.registerModule(
        category: 'test',
        name: 'Test',
        params: pipelineParams(overrides),
        ref: this,
        cleanup: false
    )
}

def pipelineParams(Map overrides = [:]) {
    return [
        choice(name: 'UE5_TEST_MODE',
               choices: overrides.UE5_TEST_MODE_CHOICES ?: ['RunAll', 'RunNamed', 'RunFiltered'],
               description: 'UE5 test execution mode'),
        string(name: 'UE5_TEST_NAMES', defaultValue: overrides.UE5_TEST_NAMES ?: '',
               description: 'Semicolon-separated test names (for RunNamed mode)'),
        choice(name: 'UE5_TEST_FILTER',
               choices: overrides.UE5_TEST_FILTER_CHOICES ?: ['Product', 'Smoke', 'Engine', 'Stress', 'Perf'],
               description: 'Test filter category (for RunFiltered mode)')
    ]
}

def execute(Map params, Map ctx) {
    buildUE5.runTests(
        engineRoot: ctx.engineRoot ?: params.UE5_ENGINE_ROOT,
        project:    ctx.projectPath ?: params.UE5_PROJECT_PATH,
        mode:       params.UE5_TEST_MODE,
        testNames:  params.UE5_TEST_NAMES,
        testFilter: params.UE5_TEST_FILTER,
        config:     ctx.buildConfig ?: params.BUILD_CONFIG,
        platform:   ctx.buildPlatform ?: params.BUILD_PLATFORM
    )

    // Publish test results
    def testJson = buildUE5.getTestResults()
    def junitXml = buildUE5.getJUnitXMLFromJSON(testJson)
    writeFile file: 'Logs/UnitTestsReport/junit.xml', text: junitXml
    junit testResults: 'Logs/UnitTestsReport/junit.xml', allowEmptyResults: true

    // Store in context for notification modules
    ctx.testResults = testJson
}
