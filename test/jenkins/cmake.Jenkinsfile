// Sandbox test: Git + CMake. See test/jenkins/README.md.
@Library('JenkinsLibSandbox') _   // version comes from the folder's library setting

stages {
    git.sync()
    github.version()

    matrix(CMAKE_CONFIG: ['Debug', 'Release']) {
        cmake.build()
        cmake.test()
        only(CMAKE_CONFIG: 'Release') {
            cmake.pack()
            zip.pack()
            gdrive.deploy()
        }
    }

    github.release()
    discord.alert()
}
