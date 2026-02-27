/**
 * Visual Studio / MSBuild module.
 * Stateless - all configuration passed via Map parameters.
 */

// ── Module registration ──

def call(Map overrides = [:]) {
    buasPipeline.registerModule(
        category: 'build',
        name: 'Build',
        params: pipelineParams(overrides),
        ref: this,
        cleanup: false
    )
}

def pipelineParams(Map overrides = [:]) {
    return [
        string(name: 'VS_MSBUILD_PATH', defaultValue: overrides.VS_MSBUILD_PATH ?: '',
               description: 'Path to MSBuild.exe'),
        string(name: 'VS_PROJECT_PATH', defaultValue: overrides.VS_PROJECT_PATH ?: '',
               description: 'Path to .sln or .vcxproj file'),
        string(name: 'VS_CONFIG', defaultValue: overrides.VS_CONFIG ?: 'Debug',
               description: 'VS build configuration'),
        string(name: 'VS_PLATFORM', defaultValue: overrides.VS_PLATFORM ?: 'x64',
               description: 'VS target platform')
    ]
}

def execute(Map params, Map ctx) {
    build(
        msbuildPath: params.VS_MSBUILD_PATH,
        projectPath: params.VS_PROJECT_PATH,
        config:      params.VS_CONFIG,
        platform:    params.VS_PLATFORM
    )
    ctx.buildConfig = params.VS_CONFIG
    ctx.buildPlatform = params.VS_PLATFORM
    ctx.buildEngine = 'VisualStudio'
}

// ── Direct-use methods ──

def build(Map config) {
    def msbuildPath = config.msbuildPath
    def projectPath = config.projectPath
    def buildConfig = config.config ?: 'Debug'
    def platform = config.platform ?: 'x64'
    def verbosity = config.verbosity ?: 'diagnostic'

    bat(label: "Compile VS project",
        script: "CALL \"${msbuildPath}\" \"${projectPath}\" /t:build /p:Configuration=${buildConfig};Platform=${platform};verbosity=${verbosity}")
}
