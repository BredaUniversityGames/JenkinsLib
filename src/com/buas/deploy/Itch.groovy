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
        def prev = steps.params ?: [:]
        return [
            steps.credentials(name: 'ITCH_CREDENTIALS_ID', defaultValue: overrides.ITCH_CREDENTIALS_ID ?: prev.ITCH_CREDENTIALS_ID ?: '',
                   credentialType: 'org.jenkinsci.plugins.plaincredentials.StringCredentials',
                   description: 'Jenkins credential for Butler API key (Secret text)'),
            steps.string(name: 'ITCH_TARGET', defaultValue: overrides.ITCH_TARGET ?: prev.ITCH_TARGET ?: '',
                   description: 'itch.io target (user/game:channel)')
        ]
    }

    def execute(Map params, Map ctx) {
        def butlerPath = steps.env.ITCH_BUTLER_PATH
        if (!butlerPath) {
            steps.error("ITCH_BUTLER_PATH environment variable is not set. Configure it in Manage Jenkins > Nodes > (node) > Environment variables.")
        }
        deploy(
            butlerPath:    butlerPath,
            credentialsId: params.ITCH_CREDENTIALS_ID,
            source:        ctx.outputDir,
            target:        params.ITCH_TARGET,
            platform:      ctx.buildPlatform ?: ''
        )
    }

    def deploy(Map config) {
        def butlerPath = config.butlerPath
        def credentialsId = config.credentialsId
        def source = config.source
        def target = config.target
        def platform = config.platform ?: ''

        def uploadSource = source
        if (platform) {
            def platformDir = steps.utilWin.platformOutputDir(platform)
            uploadSource = "${source}\\${platformDir}"
        }

        steps.withCredentials([steps.string(credentialsId: credentialsId, variable: 'BUTLER_API_KEY')]) {
            steps.bat(label: "Upload to itch.io",
                script: "\"${butlerPath}\" push \"${uploadSource}\" ${target}")
        }
    }
}
