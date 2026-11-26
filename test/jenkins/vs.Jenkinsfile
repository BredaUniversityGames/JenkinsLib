// Sandbox test: Git + Visual Studio. See test/jenkins/README.md.
@Library('JenkinsLibSandbox@dev') _

stages {
    git.sync()
    vs.build()
    vs.test()
    zip.pack()
    discord.alert()
}
