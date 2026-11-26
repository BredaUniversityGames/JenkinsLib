// Git, CMake (no presets), Zip and VS modules, used for the parameter snapshot.
@Library('JenkinsLib') _

stages {
    git.sync()
    cmake.build()
    cmake.test()
    cmake.workflow()
    cmake.pack()
    zip.pack()
    vs.build()
    vs.test()
}
