// Sandbox test: Git + Visual Studio. See test/jenkins/README.md.
@Library('JenkinsLibSandbox') _   // version comes from the folder's library setting

stages {
    git.sync()
    vs.build()
    vs.test()
    zip.pack()
    discord.alert()
}
