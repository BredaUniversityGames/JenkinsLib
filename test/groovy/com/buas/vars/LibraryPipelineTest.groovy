package com.buas.vars

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before

import static com.lesfurets.jenkins.unit.global.lib.LibraryConfiguration.library
import static com.lesfurets.jenkins.unit.global.lib.ProjectSource.projectSource

/**
 * Base class for tests that run a whole Jenkinsfile against this library,
 * loaded from the working copy as `@Library('JenkinsLib')`.
 *
 * Registers the pipeline steps the library uses. Every `bat` call returns 0
 * (or '' for returnStdout) unless a test sets `batHandler`.
 */
abstract class LibraryPipelineTest extends BasePipelineTest {

    /** Parameters passed to properties([parameters(...)]), flattened. */
    List declaredParams = []

    /** Optional override for bat: receives the args map, returns the result. */
    Closure batHandler = null

    @Override
    @Before
    void setUp() {
        super.setUp()

        helper.registerSharedLibrary(library()
            .name('JenkinsLib')
            .defaultVersion('<notNeeded>')
            .allowOverride(true)
            .implicit(false)
            .targetPath('<notNeeded>')
            .retriever(projectSource())
            .build())

        // Parameter builders return the same shape matrix.groovy expects
        ['string', 'choice', 'booleanParam', 'credentials'].each { String symbol ->
            helper.registerAllowedMethod(symbol, [Map]) { Map args -> [symbol: symbol, arguments: args] }
        }
        helper.registerAllowedMethod('parameters', [List]) { List list -> list }
        helper.registerAllowedMethod('properties', [List]) { List list -> declaredParams = list.flatten() }

        // JenkinsPipelineUnit mocks Jenkins' `build` step (trigger another
        // job), which would swallow cmake.build(), vs.build() and ue5.build().
        // Real Jenkins calls the library method, so drop the mock.
        helper.allowedMethodCallbacks.keySet().removeIf { it.name == 'build' }

        // Like Jenkins, error() aborts the pipeline
        helper.registerAllowedMethod('error', [String]) { String msg -> throw new Exception(msg) }

        // Steps with a body just run the body
        helper.registerAllowedMethod('node', [String, Closure]) { String label, Closure body -> body() }
        helper.registerAllowedMethod('ws', [String, Closure]) { String path, Closure body -> body() }
        helper.registerAllowedMethod('withEnv', [List, Closure]) { List env, Closure body -> body() }
        helper.registerAllowedMethod('withCredentials', [List, Closure]) { List creds, Closure body -> body() }
        helper.registerAllowedMethod('lock', [Map, Closure]) { Map args, Closure body -> body() }
        helper.registerAllowedMethod('catchError', [Map, Closure]) { Map args, Closure body -> body() }
        helper.registerAllowedMethod('timeout', [Map, Closure]) { Map args, Closure body -> body() }
        helper.registerAllowedMethod('parallel', [Map]) { Map branches -> branches.values().each { it() } }

        // Steps whose result we control
        helper.registerAllowedMethod('bat', [Map]) { Map args ->
            if (batHandler) return batHandler(args)
            return args.returnStdout ? '' : 0
        }
        helper.registerAllowedMethod('powershell', [Map]) { Map args -> args.returnStdout ? '' : 0 }
        helper.registerAllowedMethod('fileExists', [String]) { String path -> false }

        // Steps that only need to exist
        ['cleanWs', 'junit', 'unstable', 'checkout', 'writeFile'].each { String name ->
            helper.registerAllowedMethod(name, [Map]) { Map args -> null }
        }
        helper.registerAllowedMethod('cleanWs', []) { -> null }
        helper.registerAllowedMethod('unstable', [String]) { String msg -> null }
        helper.registerAllowedMethod('checkout', [Object]) { Object scm -> null }
        ['scmGit', 'submodule', 'cloneOption', 'usernamePassword', 'gitUsernamePassword', 'file'].each { String name ->
            helper.registerAllowedMethod(name, [Map]) { Map args -> [(name): args] }
        }

        binding.setVariable('env', [
            JOB_NAME:      'Team/Game',
            JOB_BASE_NAME: 'Game',
            BUILD_NUMBER:  '7',
            BUILD_URL:     'https://jenkins.example/job/Game/7/',
            WORKSPACE:     'C:\\Jenkins\\Team/Game',
            NODE_NAME:     'agent-1',
        ])
        // In Jenkins, currentBuild.result stays null until something sets it;
        // stages.groovy relies on that (`currentBuild.result ?: 'FAILURE'`).
        binding.getVariable('currentBuild').result = null

        // log.currStage() reads STAGE_NAME as a global
        binding.setVariable('STAGE_NAME', 'stage')
    }

    /** Runs a Jenkinsfile from test/resources/jenkinsfiles. */
    def runJenkinsfile(String name, Map params) {
        binding.setVariable('params', params)
        return runScript("test/resources/jenkinsfiles/${name}")
    }

    /** "NAME:symbol" for every declared parameter, in order. */
    List<String> paramSignature() {
        declaredParams.collect { "${it.arguments.name}:${it.symbol}".toString() }
    }

    /** Scripts of all bat calls, in order. */
    List<String> batScripts() {
        helper.callStack.findAll { it.methodName == 'bat' }.collect { it.args[0].script.toString() }
    }

    /** Names of all stages, in order. */
    List<String> stageNames() {
        helper.callStack.findAll { it.methodName == 'stage' }.collect { it.args[0].toString() }
    }
}
