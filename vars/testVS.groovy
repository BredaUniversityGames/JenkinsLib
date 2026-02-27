/**
 * Visual Studio Test module.
 * Supports CTest and GoogleTest frameworks.
 * Delegates to testRunner's existing run method.
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
        choice(name: 'VS_TEST_FRAMEWORK',
               choices: overrides.VS_TEST_FRAMEWORK_CHOICES ?: ['CTest', 'GoogleTest'],
               description: 'VS test framework to use'),
        string(name: 'VS_TEST_EXECUTABLE', defaultValue: overrides.VS_TEST_EXECUTABLE ?: '',
               description: 'Path to test executable (GoogleTest) or CTest build dir')
    ]
}

def execute(Map params, Map ctx) {
    testRunner.run(
        framework:  params.VS_TEST_FRAMEWORK,
        executable: params.VS_TEST_EXECUTABLE,
        config:     ctx.buildConfig ?: params.VS_CONFIG
    )
}
