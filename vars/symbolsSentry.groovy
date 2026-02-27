/**
 * Sentry debug symbol upload.
 * Gracefully handles failures since Sentry being offline is a known non-fatal condition.
 * Stateless - all configuration passed via Map parameters.
 */

// ── Module registration ──

def call(Map overrides = [:]) {
    buasPipeline.registerModule(
        category: 'symbols',
        name: 'Debug Symbols',
        params: pipelineParams(overrides),
        ref: this,
        cleanup: false
    )
}

def pipelineParams(Map overrides = [:]) {
    return [
        string(name: 'SENTRY_CLI_PATH', defaultValue: overrides.SENTRY_CLI_PATH ?: '',
               description: 'Path to sentry-cli.exe'),
        string(name: 'SENTRY_AUTH_TOKEN_ID', defaultValue: overrides.SENTRY_AUTH_TOKEN_ID ?: '',
               description: 'Jenkins credential ID for Sentry auth token'),
        string(name: 'SENTRY_ORG', defaultValue: overrides.SENTRY_ORG ?: '',
               description: 'Sentry organization slug'),
        string(name: 'SENTRY_PROJECT', defaultValue: overrides.SENTRY_PROJECT ?: '',
               description: 'Sentry project slug')
    ]
}

def execute(Map params, Map ctx) {
    withCredentials([string(credentialsId: params.SENTRY_AUTH_TOKEN_ID, variable: 'SENTRY_TOKEN')]) {
        upload(
            cliPath:   params.SENTRY_CLI_PATH,
            authToken: SENTRY_TOKEN,
            org:       params.SENTRY_ORG,
            project:   params.SENTRY_PROJECT,
            outputDir: ctx.outputDir
        )
    }
}

// ── Direct-use methods ──

def upload(Map config) {
    def cliPath = config.cliPath
    def authToken = config.authToken
    def org = config.org
    def project = config.project
    def outputDir = config.outputDir

    try {
        bat(label: "Upload debug symbols to Sentry",
            script: "\"${cliPath}\" --auth-token ${authToken} upload-dif -o ${org} -p ${project} ${outputDir}")
    } catch (err) {
        log.error("Sentry upload failed: ${err}. This could be due to Sentry being offline.")
    }
}
