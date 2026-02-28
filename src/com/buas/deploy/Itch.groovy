package com.buas.deploy

/**
 * itch.io deployment via Butler.
 * Uses withCredentials for secure API key handling.
 */
class Itch implements Serializable {
    def steps

    Itch(steps) {
        this.steps = steps
    }

    def pipelineParams(Map overrides = [:]) {
        return [
            steps.string(name: 'ITCH_BUTLER_PATH', defaultValue: overrides.ITCH_BUTLER_PATH ?: '',
                   description: 'Path to Butler executable'),
            steps.string(name: 'ITCH_CREDENTIALS_ID', defaultValue: overrides.ITCH_CREDENTIALS_ID ?: '',
                   description: 'Jenkins credential ID for Butler API key'),
            steps.string(name: 'ITCH_TARGET', defaultValue: overrides.ITCH_TARGET ?: '',
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

    def deploy(Map config) {
        def butlerPath = config.butlerPath
        def credentialsId = config.credentialsId
        def source = config.source
        def target = config.target
        def platform = config.platform ?: 'Win64'

        def platformDir = steps.utilWin.platformOutputDir(platform)
        def uploadSource = "${source}\\${platformDir}"

        steps.withCredentials([steps.string(credentialsId: credentialsId, variable: 'BUTLER_KEY')]) {
            steps.bat(label: "Upload to itch.io",
                script: "\"${butlerPath}\" --identity=%BUTLER_KEY% push \"${uploadSource}\" ${target}")
        }
    }
}
