/**
 * Epic Games Store deployment via BuildPatchTool.
 * Stateless - all configuration passed via Map parameters.
 */

// ── Module registration ──

def call(Map overrides = [:]) {
    buasPipeline.registerModule(
        category: 'deploy',
        name: 'Epic Games Store',
        params: pipelineParams(overrides),
        ref: this,
        cleanup: false
    )
}

def pipelineParams(Map overrides = [:]) {
    return [
        string(name: 'EPIC_BPT_PATH', defaultValue: overrides.EPIC_BPT_PATH ?: '',
               description: 'Path to BuildPatchTool.exe'),
        string(name: 'EPIC_ORG_ID', defaultValue: overrides.EPIC_ORG_ID ?: '',
               description: 'Epic organization ID'),
        string(name: 'EPIC_PRODUCT_ID', defaultValue: overrides.EPIC_PRODUCT_ID ?: '',
               description: 'Epic product ID'),
        string(name: 'EPIC_ARTIFACT_ID', defaultValue: overrides.EPIC_ARTIFACT_ID ?: '',
               description: 'Epic artifact ID'),
        string(name: 'EPIC_CLIENT_ID', defaultValue: overrides.EPIC_CLIENT_ID ?: '',
               description: 'Epic client ID'),
        string(name: 'EPIC_CLIENT_SECRET_ID', defaultValue: overrides.EPIC_CLIENT_SECRET_ID ?: '',
               description: 'Jenkins credential ID for Epic client secret')
    ]
}

def execute(Map params, Map ctx) {
    deploy(
        bptPath:        params.EPIC_BPT_PATH,
        orgId:          params.EPIC_ORG_ID,
        productId:      params.EPIC_PRODUCT_ID,
        artifactId:     params.EPIC_ARTIFACT_ID,
        clientId:       params.EPIC_CLIENT_ID,
        clientSecretId: params.EPIC_CLIENT_SECRET_ID,
        contentDir:     ctx.outputDir,
        platform:       ctx.buildPlatform ?: 'Win64'
    )
}

// ── Direct-use methods ──

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
