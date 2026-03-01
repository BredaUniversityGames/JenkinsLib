/**
 * Unreal Engine 5 module.
 * Usage: ue5.build(), ue5.test()
 *
 * Direct-use methods are available after registration:
 * ue5.runTests(), ue5.getTestResults(), ue5.getJUnitXMLFromJSON(), ue5.fixupRedirects()
 */

import groovy.transform.Field
import com.buas.ModuleRegistry
import com.buas.build.UE5

@Field def _impl = null

def build(Map overrides = [:]) {
    _impl = new UE5(this)
    ModuleRegistry.register(
        category: 'build',
        name: 'Build',
        params: _impl.pipelineParams(overrides),
        execute: { params, ctx -> _impl.execute(params, ctx) },
        hasCleanup: false
    )
}

def test(Map overrides = [:]) {
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

    ModuleRegistry.register(
        category: 'test',
        name: 'Test',
        params: testParams,
        execute: { params, ctx ->
            _impl.runTests(
                engineRoot: ctx.engineRoot ?: params.UE5_ENGINE_ROOT,
                project:    ctx.projectPath ?: params.UE5_PROJECT_PATH,
                mode:       params.UE5_TEST_MODE,
                testNames:  params.UE5_TEST_NAMES,
                testFilter: params.UE5_TEST_FILTER,
                config:     ctx.buildConfig ?: params.UE5_BUILD_CONFIG,
                platform:   ctx.buildPlatform ?: params.UE5_BUILD_PLATFORM
            )

            def testJson = _impl.getTestResults()
            def junitXml = _impl.getJUnitXMLFromJSON(testJson)
            writeFile file: 'Logs/UnitTestsReport/junit.xml', text: junitXml
            junit testResults: 'Logs/UnitTestsReport/junit.xml', allowEmptyResults: true

            ctx.testResults = testJson
        },
        hasCleanup: false
    )
}

// Direct-use methods
def runTests(Map config) { _impl.runTests(config) }
def getTestResults() { return _impl.getTestResults() }
def getJUnitXMLFromJSON(String jsonContent) { return _impl.getJUnitXMLFromJSON(jsonContent) }
def fixupRedirects(Map config) { _impl.fixupRedirects(config) }
