/**
 * Steam deployment via SteamCMD.
 * Handles VDF manifest generation, SteamGuard authentication, and upload.
 * Stateless - all configuration passed via Map parameters.
 */

def deploy(Map config) {
    def credential = config.credential
    def steamCmdPath = config.steamCmdPath
    def appId = config.appId
    def depotId = config.depotId
    def contentRoot = config.contentRoot
    def platform = config.platform ?: 'Win64'
    def branch = config.branch ?: ''
    def description = config.description ?: "Jenkins Build #${env.BUILD_NUMBER}"
    def isPreview = config.isPreview ?: false
    def outputDir = config.outputDir ?: 'output'
    def exclude = config.exclude ?: '*.pdb'

    def platformDir = utilWin.platformOutputDir(platform)

    // Create depot manifest
    createDepotManifest(
        depotId: depotId,
        contentRoot: contentRoot,
        localPath: '*',
        depotPath: '.',
        isRecursive: true,
        exclude: exclude
    )

    // Create app manifest
    createAppManifest(
        appId: appId,
        depotId: depotId,
        contentRoot: contentRoot,
        description: description,
        isPreview: isPreview,
        localContentPath: platformDir,
        branch: branch,
        outputDir: outputDir
    )

    // Try deploy with SteamGuard fallback
    def appManifest = "app_build_${appId}.vdf"
    tryDeploy(credential, steamCmdPath, appManifest)
}

def createDepotManifest(Map config) {
    def depotId = config.depotId
    def contentRoot = config.contentRoot
    def localPath = config.localPath ?: '*'
    def depotPath = config.depotPath ?: '.'
    def isRecursive = config.isRecursive != null ? config.isRecursive : true
    def exclude = config.exclude ?: '*.pdb'

    def depotManifest = libraryResource("depot_build_template.vdf")
    depotManifest = depotManifest.replace("<DEPOTID>", depotId)
    depotManifest = depotManifest.replace("<CONTENTROOT>", contentRoot)
    depotManifest = depotManifest.replace("<LOCALPATH>", localPath)
    depotManifest = depotManifest.replace("<DEPOTPATH>", depotPath)
    depotManifest = depotManifest.replace("<ISRECURSIVE>", "${isRecursive ? '1' : '0'}")
    depotManifest = depotManifest.replace("<EXCLUDE>", exclude)

    writeFile(file: "depot_build_${depotId}.vdf", text: depotManifest)
    return "depot_build_${depotId}.vdf"
}

def createAppManifest(Map config) {
    def appId = config.appId
    def depotId = config.depotId
    def contentRoot = config.contentRoot
    def description = config.description ?: ''
    def isPreview = config.isPreview ?: false
    def localContentPath = config.localContentPath ?: ''
    def branch = config.branch ?: ''
    def outputDir = config.outputDir ?: 'output'

    def appManifest = libraryResource("app_build_template.vdf")
    appManifest = appManifest.replace("<APPID>", appId)
    appManifest = appManifest.replace("<DESCRIPTION>", description)
    appManifest = appManifest.replace("<ISPREVIEW>", "${isPreview ? '1' : '0'}")
    appManifest = appManifest.replace("<LOCALCONTENT>", localContentPath)
    appManifest = appManifest.replace("<BRANCH>", branch)
    appManifest = appManifest.replace("<OUTPUTDIR>", outputDir)
    appManifest = appManifest.replace("<CONTENTROOT>", contentRoot)
    appManifest = appManifest.replace("<DEPOTID>", depotId)

    writeFile(file: "app_build_${appId}.vdf", text: appManifest)
    return "app_build_${appId}.vdf"
}

def tryDeploy(String credential, String steamCmdPath, String appManifest) {
    try {
        log("Trying to deploy to Steam without SteamGuard...")
        executeDeploy(credential, steamCmdPath, appManifest)
    } catch (err) {
        log.error("Steam deploy failed. Insert Steam Guard Code...")

        def guardCode = null
        timeout(time: 3, unit: 'MINUTES') {
            guardCode = input message: 'Insert Steam Guard code', ok: 'Submit',
                              parameters: [
                                  string(name: 'Steam Guard Code', defaultValue: '',
                                         description: 'Provide the pipeline with the required Steam Guard code.')
                              ]
        }

        if (guardCode) {
            executeDeploy(credential, steamCmdPath, appManifest, guardCode)
        } else {
            log.error("Failed to provide Steam Guard code.")
            error("Steam Guard authentication failed")
        }
    }
}

private def executeDeploy(String credential, String steamCmdPath, String appManifest, String steamGuard = null) {
    withCredentials([usernamePassword(credentialsId: credential, passwordVariable: 'STEAMPASS', usernameVariable: 'STEAMUSER')]) {
        if (steamGuard) {
            log("Deploying to Steam with SteamGuard code")
            bat(label: "Deploy to Steam with SteamGuard",
                script: "\"${steamCmdPath}\" +login %STEAMUSER% %STEAMPASS% \"${steamGuard}\" +run_app_build_http \"${appManifest}\" +quit")
        } else {
            log("Deploying to Steam without SteamGuard")
            bat(label: "Deploy to Steam without SteamGuard",
                script: "\"${steamCmdPath}\" +login %STEAMUSER% +run_app_build_http \"${appManifest}\" +quit")
        }
    }
}
