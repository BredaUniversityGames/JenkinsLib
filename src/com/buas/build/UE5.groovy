package com.buas.build

import groovy.json.JsonSlurper
import groovy.xml.MarkupBuilder

/**
 * UE5 Build and Test module.
 * Supports three build methods: Blueprint, Precompiled, Custom.
 */
class UE5 implements Serializable {
    def steps

    UE5(steps) {
        this.steps = steps
    }

    def pipelineParams(Map overrides = [:]) {
        def prev = steps.params ?: [:]
        return [
            steps.choice(name: 'UE5_BUILD_METHOD',
                   choices: reorderChoices(overrides.UE5_BUILD_METHOD_CHOICES ?: ['Blueprint', 'Precompiled', 'Custom'], prev.UE5_BUILD_METHOD),
                   description: 'UE5 build method'),
            steps.string(name: 'UE5_ENGINE_ROOT', defaultValue: overrides.UE5_ENGINE_ROOT ?: prev.UE5_ENGINE_ROOT ?: '',
                   description: 'Path to UE5 engine root (e.g. C:\\UE_5.3)'),
            steps.string(name: 'UE5_PROJECT_PATH', defaultValue: overrides.UE5_PROJECT_PATH ?: prev.UE5_PROJECT_PATH ?: '',
                   description: 'Absolute path to .uproject file'),
            steps.string(name: 'UE5_PROJECT_NAME', defaultValue: overrides.UE5_PROJECT_NAME ?: prev.UE5_PROJECT_NAME ?: '',
                   description: 'Project name (without extension)'),
            steps.string(name: 'UE5_CUSTOM_FLAGS',
                   defaultValue: overrides.UE5_CUSTOM_FLAGS ?: prev.UE5_CUSTOM_FLAGS ?: '-Cook -Allmaps -Build -Stage -Pak -Rocket -Prereqs -Package',
                   description: 'Custom RunUAT flags (only for Custom build method)'),
            steps.choice(name: 'BUILD_CONFIG',
                   choices: reorderChoices(overrides.BUILD_CONFIG_CHOICES ?: ['Development', 'Shipping', 'DebugGame', 'Debug', 'Test'], prev.BUILD_CONFIG),
                   description: 'Build configuration'),
            steps.choice(name: 'BUILD_PLATFORM',
                   choices: reorderChoices(overrides.BUILD_PLATFORM_CHOICES ?: ['Win64', 'Linux', 'PS5'], prev.BUILD_PLATFORM),
                   description: 'Target platform'),
            steps.booleanParam(name: 'MATCH_BUILD_ID', defaultValue: overrides.MATCH_BUILD_ID ?: prev.MATCH_BUILD_ID ?: false,
                         description: 'Run MatchBuildID.py before build (for precompiled engines with plugins)')
        ]
    }

    private static List reorderChoices(List choices, def current) {
        if (current && choices.contains(current)) {
            return [current] + (choices - current)
        }
        return choices
    }

    def execute(Map params, Map ctx) {
        if (params.MATCH_BUILD_ID) {
            def projectDir = params.UE5_PROJECT_PATH.substring(0,
                params.UE5_PROJECT_PATH.lastIndexOf('\\'))
            steps.utilPython.runScript(
                "${steps.env.WORKSPACE}\\JenkinsLib\\scripts\\MatchBuildID.py",
                "\"${projectDir}\" \"${params.UE5_ENGINE_ROOT}\" \"false\""
            )
        }

        build(
            engineRoot:  params.UE5_ENGINE_ROOT,
            projectName: params.UE5_PROJECT_NAME,
            project:     params.UE5_PROJECT_PATH,
            config:      params.BUILD_CONFIG,
            platform:    params.BUILD_PLATFORM,
            outputDir:   ctx.outputDir,
            method:      params.UE5_BUILD_METHOD,
            customFlags: params.UE5_CUSTOM_FLAGS
        )

        ctx.buildConfig = params.BUILD_CONFIG
        ctx.buildPlatform = params.BUILD_PLATFORM
        ctx.engineRoot = params.UE5_ENGINE_ROOT
        ctx.projectPath = params.UE5_PROJECT_PATH
        ctx.buildEngine = 'UE5'
    }

    def build(Map config) {
        def engineRoot = config.engineRoot
        def projectName = config.projectName
        def project = config.project
        def buildConfig = config.config ?: 'Development'
        def platform = config.platform ?: 'Win64'
        def outputDir = config.outputDir
        def method = config.method ?: 'Blueprint'
        def customFlags = config.customFlags ?: '-Cook -Allmaps -Build -Stage -Pak -Rocket -Prereqs -Package -crashreporter'

        switch (method) {
            case 'Blueprint':
                steps.bat(label: "Package UE5 Blueprint project",
                    script: "\"${engineRoot}\\Build\\BatchFiles\\RunUAT.bat\" BuildCookRun " +
                            "-Project=\"${project}\" -NoP4 -Distribution " +
                            "-TargetPlatform=${platform} -Platform=${platform} " +
                            "-ClientConfig=${buildConfig} -ServerConfig=${buildConfig} " +
                            "-Cook -Allmaps -Build -Stage -Pak -Archive " +
                            "-Archivedirectory=\"${outputDir}\" -Rocket -Prereqs -Package")
                break

            case 'Precompiled':
                steps.bat(label: "Package UE5 Precompiled project",
                    script: "\"${engineRoot}\\Build\\BatchFiles\\RunUAT.bat\" BuildCookRun " +
                            "-Project=\"${project}\" -NoP4 " +
                            "-nocompileeditor -skipbuildeditor " +
                            "-TargetPlatform=${platform} -Platform=${platform} " +
                            "-ClientConfig=${buildConfig} " +
                            "-Cook -Build -Stage -Pak -Archive " +
                            "-Archivedirectory=\"${outputDir}\" -Rocket -Prereqs " +
                            "-iostore -compressed -Package -nocompile -nocompileuat")
                break

            case 'Custom':
                steps.bat(label: "Package UE5 Custom project",
                    script: "\"${engineRoot}\\Build\\BatchFiles\\RunUAT.bat\" BuildCookRun " +
                            "-Project=\"${project}\" -NoP4 -Distribution " +
                            "-TargetPlatform=${platform} -Platform=${platform} " +
                            "-ClientConfig=${buildConfig} -ServerConfig=${buildConfig} " +
                            "-Archive -Archivedirectory=\"${outputDir}\" ${customFlags}")
                break

            default:
                steps.error("Unknown UE5 build method: ${method}. Valid methods: Blueprint, Precompiled, Custom")
        }
    }

    def runTests(Map config) {
        def mode = config.mode ?: 'RunAll'

        switch (mode) {
            case 'RunAll':
                runAutomationCommand('RunAll Now', config)
                break
            case 'RunNamed':
                def names = config.testNames?.split(';')?.join('+') ?: ''
                if (!names) {
                    steps.log.warning("No test names specified for RunNamed mode")
                    return
                }
                runAutomationCommand("RunTests Now ${names}", config)
                break
            case 'RunFiltered':
                def testFilter = config.testFilter ?: 'Product'
                def validFilters = ['Engine', 'Smoke', 'Stress', 'Perf', 'Product']
                if (validFilters.contains(testFilter)) {
                    runAutomationCommand("RunFilter Now ${testFilter}", config)
                } else {
                    steps.log.error("Invalid test filter '${testFilter}'. Valid filters: ${validFilters.join(', ')}")
                }
                break
            default:
                steps.log.error("Unknown test mode: ${mode}. Valid modes: RunAll, RunNamed, RunFiltered")
        }
    }

    def runAutomationCommand(String testCommand, Map config) {
        def engineRoot = config.engineRoot
        def project = config.project
        def buildConfig = config.config ?: 'Development'
        def platform = config.platform ?: 'Win64'

        steps.log("Running tests: ${testCommand} in ${buildConfig} on ${platform}")
        def result = steps.bat(label: "Run UE5 Automation Tests",
            script: "\"${engineRoot}\\Binaries\\${platform}\\UnrealEditor-Cmd.exe\" " +
                    "\"${project}\" -stdout -fullstdlogoutput -buildmachine -nullrhi " +
                    "-unattended -NoPause -NoSplash -NoSound " +
                    "-ExecCmds=\"Automation ${testCommand};Quit\" " +
                    "-ReportExportPath=\"${steps.env.WORKSPACE}\\Logs\\UnitTestsReport\"",
            returnStatus: true)

        if (result != 0) {
            steps.unstable "Some tests did not pass!"
        }
    }

    def getTestResults() {
        def json = steps.readFile file: 'Logs/UnitTestsReport/index.json', encoding: "UTF-8"
        json = json.replace("\uFEFF", "")
        return json
    }

    @NonCPS
    def getJUnitXMLFromJSON(String jsonContent) {
        def j = new JsonSlurper().parseText(jsonContent)

        def sw = new StringWriter()
        def builder = new MarkupBuilder(sw)

        builder.doubleQuotes = true
        builder.mkp.xmlDeclaration version: "1.0", encoding: "utf-8"

        builder.testsuite(tests: j.succeeded + j.failed, failures: j.failed, time: j.totalDuration) {
            for (test in j.tests) {
                builder.testcase(name: test.testDisplayName, classname: test.fullTestPath, status: test.state) {
                    for (entry in test.entries) {
                        builder.failure(message: entry.event.message, type: entry.event.type, entry.filename + " " + entry.lineNumber)
                    }
                }
            }
        }

        return sw.toString()
    }

    def fixupRedirects(Map config) {
        def engineRoot = config.engineRoot
        def project = config.project
        def platform = config.platform ?: 'Win64'

        steps.bat(label: "Fix up redirectors in UE5 project",
            script: "\"${engineRoot}\\Binaries\\${platform}\\UnrealEditor.exe\" \"${project}\" " +
                    "-run=ResavePackages -fixupredirects -autocheckout -projectonly -unattended -stdout")
    }
}
