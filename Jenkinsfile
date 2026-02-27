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

buasPipeline {
    // ── Version Control (pick one) ──
    vcsPerforce()
    // vcsGit()

    // ── Build Engine (pick one) ──
    buildUE5()
    // buildVS()

    // ── Testing (pick one, must match build engine) ──
    // testUE5()
    // testVS()

    // ── Code Review ──
    // reviewSwarm()

    // ── Deployment (enable any combination) ──
    // deploySteam()
    // deployItch()
    // deployGDrive()
    // deployEpic()

    // ── Debug Symbols ──
    // symbolsSentry()

    // ── Notifications ──
    notifyDiscord()
}
