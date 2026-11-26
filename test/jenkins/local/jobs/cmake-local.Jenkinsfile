// Runs end to end on the local test Jenkins: clones this repo (the
// jenkins-local snapshot) and builds test/jenkins/sample-cmake.
// Parameter values: test/jenkins/local/params/cmake-local.properties
@Library('JenkinsLibSandbox') _

stages {
    git.sync()

    matrix(CMAKE_CONFIG: ['Debug', 'Release']) {
        cmake.build()
        cmake.test()
        only(CMAKE_CONFIG: 'Release') {
            zip.pack(ZIP_SOURCE_DIR: 'build-sample/bin', ZIP_ARCHIVE_NAME: 'SampleGame-${CMAKE_CONFIG}')
        }
    }

    // No webhook configured, so this only exercises the alert code path
    discord.alert()
}
