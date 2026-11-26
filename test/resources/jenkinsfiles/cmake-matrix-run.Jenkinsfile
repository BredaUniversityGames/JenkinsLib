// Matrix with an only() filter, run with mocked steps.
@Library('JenkinsLib') _

stages {
    git.sync()
    matrix(CMAKE_CONFIG: ['Debug', 'Release']) {
        cmake.build()
        only(CMAKE_CONFIG: 'Release') {
            zip.pack()
        }
    }
    discord.alert()
}
