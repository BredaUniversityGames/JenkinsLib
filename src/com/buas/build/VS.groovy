package com.buas.build

/**
 * Visual Studio / MSBuild module.
 */
class VS implements Serializable {
    def steps

    VS(steps) {
        this.steps = steps
    }

    def pipelineParams(Map overrides = [:]) {
        def prev = steps.params ?: [:]
        return [
            steps.string(name: 'VS_MSBUILD_PATH', defaultValue: overrides.VS_MSBUILD_PATH ?: prev.VS_MSBUILD_PATH ?: '',
                   description: 'Path to MSBuild.exe'),
            steps.string(name: 'VS_PROJECT_PATH', defaultValue: overrides.VS_PROJECT_PATH ?: prev.VS_PROJECT_PATH ?: '',
                   description: 'Path to .sln or .vcxproj file'),
            steps.string(name: 'VS_CONFIG', defaultValue: overrides.VS_CONFIG ?: prev.VS_CONFIG ?: 'Debug',
                   description: 'VS build configuration'),
            steps.string(name: 'VS_PLATFORM', defaultValue: overrides.VS_PLATFORM ?: prev.VS_PLATFORM ?: 'x64',
                   description: 'VS target platform')
        ]
    }

    def execute(Map params, Map ctx) {
        build(
            msbuildPath: params.VS_MSBUILD_PATH,
            projectPath: params.VS_PROJECT_PATH,
            config:      params.VS_CONFIG,
            platform:    params.VS_PLATFORM,
            outputDir:   ctx.outputDir
        )
        ctx.buildConfig = params.VS_CONFIG
        ctx.buildPlatform = params.VS_PLATFORM
        ctx.buildEngine = 'VisualStudio'
    }

    def build(Map config) {
        def msbuildPath = config.msbuildPath
        def projectPath = config.projectPath
        def buildConfig = config.config ?: 'Debug'
        def platform = config.platform ?: 'x64'
        def verbosity = config.verbosity ?: 'diagnostic'
        def outputDir = config.outputDir

        def outDirFlag = outputDir ? ";OutDir=${outputDir}\\${platform}\\" : ''

        steps.bat(label: "Compile VS project",
            script: "CALL \"${msbuildPath}\" \"${projectPath}\" /t:build /p:Configuration=${buildConfig};Platform=${platform};verbosity=${verbosity}${outDirFlag}")
    }
}
