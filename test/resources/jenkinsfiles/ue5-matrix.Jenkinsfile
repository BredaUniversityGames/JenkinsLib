// Every UE5/Perforce-side module, used for the parameter snapshot.
@Library('JenkinsLib') _

stages {
    perforce.sync()
    github.version()
    matrix(UE5_BUILD_PLATFORM: ['Win64', 'PS5'],
           UE5_BUILD_CONFIG: ['Development', 'Shipping']) {
        ue5.build()
        ue5.test()
        only(UE5_BUILD_PLATFORM: 'Win64') {
            steam.deploy()
            gdrive.deploy()
        }
        itch.deploy()
        epic.deploy()
        sentry.upload()
        swarm.review()
    }
    github.release()
    discord.alert()
}
