/**
 * Google Drive deployment.
 * Wraps the existing GoogleDriveUpload.py script with proper credential handling.
 * Stateless - all configuration passed via Map parameters.
 */

// ── Module registration ──

def call(Map overrides = [:]) {
    buasPipeline.registerModule(
        category: 'deploy',
        name: 'Google Drive',
        params: pipelineParams(overrides),
        ref: this,
        cleanup: false
    )
}

def pipelineParams(Map overrides = [:]) {
    return [
        string(name: 'GDRIVE_CREDENTIALS_ID', defaultValue: overrides.GDRIVE_CREDENTIALS_ID ?: '',
               description: 'Jenkins credential ID for GDrive service account file'),
        string(name: 'GDRIVE_FOLDER_ID', defaultValue: overrides.GDRIVE_FOLDER_ID ?: '',
               description: 'Google Drive parent folder ID'),
        string(name: 'GDRIVE_CHUNK_MULTIPLIER', defaultValue: overrides.GDRIVE_CHUNK_MULTIPLIER ?: '16',
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

// ── Direct-use methods ──

def deploy(Map config) {
    def credentialsId = config.credentialsId
    def source = config.source
    def folderId = config.folderId
    def chunkMultiplier = config.chunkMultiplier ?: '16'
    def platform = config.platform ?: 'Win64'

    def platformDir = utilWin.platformOutputDir(platform)
    def archiveName = "${env.JOB_BASE_NAME}_${env.BUILD_NUMBER}"
    def sourceDir = "${source}\\${platformDir}"

    // Zip the build output
    utilZip.pack(sourceDir, archiveName, false)

    // Upload to Google Drive
    withCredentials([file(credentialsId: credentialsId, variable: 'GDRIVE_SECRET')]) {
        utilPython.runScript(
            "${env.WORKSPACE}\\JenkinsLib\\scripts\\GoogleDriveUpload.py",
            "%GDRIVE_SECRET% \"${env.WORKSPACE}\\${archiveName}.zip\" \"${archiveName}.zip\" \"${folderId}\" ${chunkMultiplier}"
        )
    }
}
