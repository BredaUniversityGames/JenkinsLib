/**
 * Epic Games Store deployment via BuildPatchTool.
 * Stateless - all configuration passed via Map parameters.
 */

def deploy(Map config) {
    def bptPath = config.bptPath
    def orgId = config.orgId
    def productId = config.productId
    def artifactId = config.artifactId
    def clientId = config.clientId
    def clientSecretId = config.clientSecretId
    def contentDir = config.contentDir
    def platform = config.platform ?: 'Win64'
    def buildVersion = config.buildVersion ?: "${env.JOB_BASE_NAME}-${env.BUILD_NUMBER}"

    def platformDir = utilWin.platformOutputDir(platform)
    def buildRoot = "${contentDir}\\${platformDir}"

    withCredentials([string(credentialsId: clientSecretId, variable: 'EPIC_SECRET')]) {
        bat(label: "Deploy to Epic Games Store",
            script: "\"${bptPath}\" " +
                    "-mode=UploadBinary " +
                    "-OrganizationId=\"${orgId}\" " +
                    "-ProductId=\"${productId}\" " +
                    "-ArtifactId=\"${artifactId}\" " +
                    "-ClientId=\"${clientId}\" " +
                    "-ClientSecret=%EPIC_SECRET% " +
                    "-BuildRoot=\"${buildRoot}\" " +
                    "-BuildVersion=\"${buildVersion}\" " +
                    "-Platform=${platform}")
    }
}
