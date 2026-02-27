/**
 * Visual Studio / MSBuild module.
 * Stateless - all configuration passed via Map parameters.
 */

def build(Map config) {
    def msbuildPath = config.msbuildPath
    def projectPath = config.projectPath
    def buildConfig = config.config ?: 'Debug'
    def platform = config.platform ?: 'x64'
    def verbosity = config.verbosity ?: 'diagnostic'

    bat(label: "Compile VS project",
        script: "CALL \"${msbuildPath}\" \"${projectPath}\" /t:build /p:Configuration=${buildConfig};Platform=${platform};verbosity=${verbosity}")
}
