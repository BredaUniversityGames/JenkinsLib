// JenkinsLib - Example Jenkinsfile
//
// Compose your pipeline by uncommenting the modules you need.
// Each module adds its own parameters and stage to Jenkins.
// Run once to populate parameters, then configure via the Jenkins UI.
//
// See README.md for full documentation.

@Library('JenkinsLib') _

stages {
    // ── Version Control (pick one) ──
    perforce.sync()
    // git.sync()

    // ── Matrix (optional) ──
    // Wrap stages in a matrix() block to repeat them for every axis combination.
    // Stages outside the matrix run once; stages inside repeat per combination.
    // Each combination gets its own output directory under Output\<value1>\<value2>\...
    matrix(UE5_BUILD_PLATFORM: ['Win64', 'PS4'],
           UE5_BUILD_CONFIG: ['Development', 'Shipping']) {

        // ── Build Engine (pick one) ──
        ue5.build()
        // vs.build()

        // ── Testing (pick one, must match build engine) ──
        // ue5.test()
        // vs.test()

        // ── Code Review ──
        // swarm.review()

        // ── Deployment (enable any combination) ──
        // steam.deploy()
        // itch.deploy()
        // gdrive.deploy()
        // epic.deploy()

        // ── Debug Symbols ──
        // sentry.upload()
    }

    // ── Notifications ──
    discord.alert()
}
