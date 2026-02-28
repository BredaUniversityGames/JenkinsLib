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
    vcs.perforce()
    // vcs.git()

    // ── Build Engine (pick one) ──
    build.ue5()
    // build.vs()

    // ── Testing (pick one, must match build engine) ──
    // test.ue5()
    // test.vs()

    // ── Code Review ──
    // review.swarm()

    // ── Deployment (enable any combination) ──
    // deploy.steam()
    // deploy.itch()
    // deploy.gdrive()
    // deploy.epic()

    // ── Debug Symbols ──
    // symbols.sentry()

    // ── Notifications ──
    notify.discord()
}
