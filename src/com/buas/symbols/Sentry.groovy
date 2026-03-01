package com.buas.symbols

/**
 * Sentry debug symbol upload.
 * Gracefully handles failures since Sentry being offline is a known non-fatal condition.
 */
class Sentry implements Serializable {
    def steps

    Sentry(steps) {
        this.steps = steps
    }

    def pipelineParams(Map overrides = [:]) {
        def prev = steps.params ?: [:]
        return [
            steps.string(name: 'SENTRY_CLI_PATH', defaultValue: overrides.SENTRY_CLI_PATH ?: prev.SENTRY_CLI_PATH ?: '',
                   description: 'Path to sentry-cli.exe'),
            steps.string(name: 'SENTRY_AUTH_TOKEN_ID', defaultValue: overrides.SENTRY_AUTH_TOKEN_ID ?: prev.SENTRY_AUTH_TOKEN_ID ?: '',
                   description: 'Jenkins credential ID for Sentry auth token'),
            steps.string(name: 'SENTRY_ORG', defaultValue: overrides.SENTRY_ORG ?: prev.SENTRY_ORG ?: '',
                   description: 'Sentry organization slug'),
            steps.string(name: 'SENTRY_PROJECT', defaultValue: overrides.SENTRY_PROJECT ?: prev.SENTRY_PROJECT ?: '',
                   description: 'Sentry project slug')
        ]
    }

    def execute(Map params, Map ctx) {
        steps.withCredentials([steps.string(credentialsId: params.SENTRY_AUTH_TOKEN_ID, variable: 'SENTRY_TOKEN')]) {
            upload(
                cliPath:   params.SENTRY_CLI_PATH,
                authToken: steps.env.SENTRY_TOKEN,
                org:       params.SENTRY_ORG,
                project:   params.SENTRY_PROJECT,
                outputDir: ctx.outputDir
            )
        }
    }

    def upload(Map config) {
        def cliPath = config.cliPath
        def authToken = config.authToken
        def org = config.org
        def project = config.project
        def outputDir = config.outputDir

        try {
            steps.bat(label: "Upload debug symbols to Sentry",
                script: "\"${cliPath}\" --auth-token ${authToken} upload-dif -o ${org} -p ${project} ${outputDir}")
        } catch (err) {
            steps.log.error("Sentry upload failed: ${err}. This could be due to Sentry being offline.")
        }
    }
}
