// JenkinsLib - Example Jenkinsfile
//
// Compose your pipeline by uncommenting the modules you need.
// Each module adds its own parameters and stage to Jenkins.
// Run once to populate parameters, then configure via the Jenkins UI.
//
// See README.md for full documentation.

library identifier: 'JenkinsLib@main',
    retriever: modernSCM([
      $class: 'GitSCMSource',
      remote: 'https://github.com/BredaUniversityGames/JenkinsLib'
    ])

stages {
    // ── Version Control (pick one) ──
    perforce.sync()
    // git.sync()

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

    // ── Notifications ──
    discord.notify()
}
