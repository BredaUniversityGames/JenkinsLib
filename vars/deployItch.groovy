/**
 * itch.io deployment via Butler.
 * Stateless - all configuration passed via Map parameters.
 * Uses withCredentials for secure API key handling.
 */

// ── Module registration ──

def call(Map overrides = [:]) {
    buasPipeline.registerModule(
        category: 'deploy',
        name: 'itch.io',
        params: pipelineParams(overrides),
        ref: this,
        cleanup: false
    )
}

def pipelineParams(Map overrides = [:]) {
    return [
        string(name: 'ITCH_BUTLER_PATH', defaultValue: overrides.ITCH_BUTLER_PATH ?: '',
               description: 'Path to Butler executable'),
        string(name: 'ITCH_CREDENTIALS_ID', defaultValue: overrides.ITCH_CREDENTIALS_ID ?: '',
               description: 'Jenkins credential ID for Butler API key'),
        string(name: 'ITCH_TARGET', defaultValue: overrides.ITCH_TARGET ?: '',
               description: 'itch.io target (user/game:channel)')
    ]
}

def execute(Map params, Map ctx) {
    deploy(
        butlerPath:    params.ITCH_BUTLER_PATH,
        credentialsId: params.ITCH_CREDENTIALS_ID,
        source:        ctx.outputDir,
        target:        params.ITCH_TARGET,
        platform:      ctx.buildPlatform ?: 'Win64'
    )
}

// ── Direct-use methods ──

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
