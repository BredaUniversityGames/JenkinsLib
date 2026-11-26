// Registers every module so a refresh runs all parameter-time code in the
// sandbox. Use: .\run-local.ps1 all-modules -RefreshOnly
// (It can't build: it mixes Perforce and Git, UE5, VS and CMake.)
@Library('JenkinsLibSandbox') _

stages {
    perforce.sync()
    git.sync()
    github.version()
    matrix(UE5_BUILD_PLATFORM: ['Win64', 'PS5']) {
        ue5.build()
        ue5.test()
        vs.build()
        vs.test()
        cmake.build()
        cmake.test()
        cmake.workflow()
        cmake.pack()
        zip.pack()
        swarm.review()
        only(UE5_BUILD_PLATFORM: 'Win64') {
            steam.deploy()
            itch.deploy()
            epic.deploy()
            gdrive.deploy()
        }
        sentry.upload()
    }
    github.release()
    discord.alert()
}
