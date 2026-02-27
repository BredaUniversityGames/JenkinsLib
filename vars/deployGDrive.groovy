/**
 * Google Drive deployment.
 * Wraps the existing GoogleDriveUpload.py script with proper credential handling.
 * Stateless - all configuration passed via Map parameters.
 */

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
