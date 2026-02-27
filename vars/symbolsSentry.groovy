/**
 * Sentry debug symbol upload.
 * Gracefully handles failures since Sentry being offline is a known non-fatal condition.
 * Stateless - all configuration passed via Map parameters.
 */

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
