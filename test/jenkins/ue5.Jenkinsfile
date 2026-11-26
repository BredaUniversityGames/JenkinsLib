// Sandbox test: Perforce + UE5. See test/jenkins/README.md.
@Library('JenkinsLibSandbox') _   // version comes from the folder's library setting

stages {
    perforce.sync()
    ue5.build()
    ue5.test()
    steam.deploy()
    itch.deploy()
    sentry.upload()
    discord.alert()
}
