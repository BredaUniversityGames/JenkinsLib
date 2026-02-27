/**
 * itch.io deployment via Butler.
 * Stateless - all configuration passed via Map parameters.
 * Uses withCredentials for secure API key handling.
 */

def deploy(Map config) {
    def butlerPath = config.butlerPath
    def credentialsId = config.credentialsId
    def source = config.source
    def target = config.target
    def platform = config.platform ?: 'Win64'

    def platformDir = utilWin.platformOutputDir(platform)
    def uploadSource = "${source}\\${platformDir}"

    withCredentials([string(credentialsId: credentialsId, variable: 'BUTLER_KEY')]) {
        bat(label: "Upload to itch.io",
            script: "\"${butlerPath}\" --identity=%BUTLER_KEY% push \"${uploadSource}\" ${target}")
    }
}
