/**
 * Test modules.
 * Usage: test.ue5(), test.vs()
 *
 * Thin wrappers that delegate to build module test methods and testRunner.
 */

def ue5(Map overrides = [:]) {
    def testParams = [
        choice(name: 'UE5_TEST_MODE',
               choices: overrides.UE5_TEST_MODE_CHOICES ?: ['RunAll', 'RunNamed', 'RunFiltered'],
               description: 'UE5 test execution mode'),
        string(name: 'UE5_TEST_NAMES', defaultValue: overrides.UE5_TEST_NAMES ?: '',
               description: 'Semicolon-separated test names (for RunNamed mode)'),
        choice(name: 'UE5_TEST_FILTER',
               choices: overrides.UE5_TEST_FILTER_CHOICES ?: ['Product', 'Smoke', 'Engine', 'Stress', 'Perf'],
               description: 'Test filter category (for RunFiltered mode)')
    ]

    stages.registerModule(
        category: 'test',
        name: 'Test',
        params: testParams,
        execute: { params, ctx ->
            build.runTests(
                engineRoot: ctx.engineRoot ?: params.UE5_ENGINE_ROOT,
                project:    ctx.projectPath ?: params.UE5_PROJECT_PATH,
                mode:       params.UE5_TEST_MODE,
                testNames:  params.UE5_TEST_NAMES,
                testFilter: params.UE5_TEST_FILTER,
                config:     ctx.buildConfig ?: params.BUILD_CONFIG,
                platform:   ctx.buildPlatform ?: params.BUILD_PLATFORM
            )

            def testJson = build.getTestResults()
            def junitXml = build.getJUnitXMLFromJSON(testJson)
            writeFile file: 'Logs/UnitTestsReport/junit.xml', text: junitXml
            junit testResults: 'Logs/UnitTestsReport/junit.xml', allowEmptyResults: true

            ctx.testResults = testJson
        },
        hasCleanup: false
    )
}

def vs(Map overrides = [:]) {
    def testParams = [
        choice(name: 'VS_TEST_FRAMEWORK',
               choices: overrides.VS_TEST_FRAMEWORK_CHOICES ?: ['CTest', 'GoogleTest'],
               description: 'VS test framework to use'),
        string(name: 'VS_TEST_EXECUTABLE', defaultValue: overrides.VS_TEST_EXECUTABLE ?: '',
               description: 'Path to test executable (GoogleTest) or CTest build dir')
    ]

    stages.registerModule(
        category: 'test',
        name: 'Test',
        params: testParams,
        execute: { params, ctx ->
            testRunner.run(
                framework:  params.VS_TEST_FRAMEWORK,
                executable: params.VS_TEST_EXECUTABLE,
                config:     ctx.buildConfig ?: params.VS_CONFIG
            )
        },
        hasCleanup: false
    )
}
