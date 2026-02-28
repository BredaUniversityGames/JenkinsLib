package com.buas.deploy

/**
 * Google Drive deployment.
 * Wraps the existing GoogleDriveUpload.py script with proper credential handling.
 */
class GDrive implements Serializable {
    def steps

    GDrive(steps) {
        this.steps = steps
    }

    def pipelineParams(Map overrides = [:]) {
        return [
            steps.string(name: 'GDRIVE_CREDENTIALS_ID', defaultValue: overrides.GDRIVE_CREDENTIALS_ID ?: '',
                   description: 'Jenkins credential ID for GDrive service account file'),
            steps.string(name: 'GDRIVE_FOLDER_ID', defaultValue: overrides.GDRIVE_FOLDER_ID ?: '',
                   description: 'Google Drive parent folder ID'),
            steps.string(name: 'GDRIVE_CHUNK_MULTIPLIER', defaultValue: overrides.GDRIVE_CHUNK_MULTIPLIER ?: '16',
                   description: 'Upload chunk size multiplier (higher = faster but more memory)')
        ]
    }

    def execute(Map params, Map ctx) {
        deploy(
            credentialsId:   params.GDRIVE_CREDENTIALS_ID,
            source:          ctx.outputDir,
            folderId:        params.GDRIVE_FOLDER_ID,
            chunkMultiplier: params.GDRIVE_CHUNK_MULTIPLIER,
            platform:        ctx.buildPlatform ?: 'Win64'
        )
    }

    def deploy(Map config) {
        def credentialsId = config.credentialsId
        def source = config.source
        def folderId = config.folderId
        def chunkMultiplier = config.chunkMultiplier ?: '16'
        def platform = config.platform ?: 'Win64'

        def platformDir = steps.utilWin.platformOutputDir(platform)
        def archiveName = "${steps.env.JOB_BASE_NAME}_${steps.env.BUILD_NUMBER}"
        def sourceDir = "${source}\\${platformDir}"

        steps.utilZip.pack(sourceDir, archiveName, false)

        steps.withCredentials([steps.file(credentialsId: credentialsId, variable: 'GDRIVE_SECRET')]) {
            steps.utilPython.runScript(
                "${steps.env.WORKSPACE}\\JenkinsLib\\scripts\\GoogleDriveUpload.py",
                "%GDRIVE_SECRET% \"${steps.env.WORKSPACE}\\${archiveName}.zip\" \"${archiveName}.zip\" \"${folderId}\" ${chunkMultiplier}"
            )
        }
    }
}
