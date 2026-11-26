// A small runnable pipeline. Modules are listed out of order on purpose:
// the orchestrator runs them by category (vcs, build, …, pack) and alerts last.
@Library('JenkinsLib') _

stages {
    discord.alert()
    zip.pack()
    cmake.build()
    git.sync()
}
