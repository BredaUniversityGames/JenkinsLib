package com.buas.vars

import org.junit.Ignore
import org.junit.Test

/**
 * Runs whole pipelines (with bat mocked) to pin down orchestration:
 * stage order, matrix/only expansion, alerts and cleanup in `finally`.
 */
class StagesRunTest extends LibraryPipelineTest {

    static final Map CMAKE_PARAMS = [
        REFRESH_PARAMETERS: false,
        CLEAN_WORKSPACE:    true,
        GIT_REPO_URL:       'https://example.com/game.git',
        GIT_BRANCH:         'main',
        CMAKE_SOURCE_DIR:   '.',
        CMAKE_BUILD_DIR:    'build',
        CMAKE_GENERATOR:    'Ninja',
        CMAKE_CONFIG:       'Debug',
        CMAKE_PLATFORM:     'x64',
        ZIP_SOURCE_DIR:     'build/bin',
        DISCORD_WEBHOOK:    'https://discord.example/webhook',
    ]

    @Override
    void setUp() {
        super.setUp()
        batHandler = { Map args ->
            if (args.returnStdout) {
                return args.script.contains('rev-parse') ? 'C:\\ws>git rev-parse HEAD\nabc123\n' : ''
            }
            return 0
        }
    }

    @Test
    void runsModulesInCategoryOrderThenAlertsAndCleansUp() {
        runJenkinsfile('git-cmake-run.Jenkinsfile', CMAKE_PARAMS)

        // Registration order in the Jenkinsfile is discord, zip, cmake, git;
        // execution follows category order with alerts last.
        assert stageNames() == ['Git Sync', 'CMake Build', 'Zip Package', 'Discord Alert']

        def scripts = batScripts()
        def configure = scripts.findIndexOf { it.contains('cmake -S "." -B "build" -G "Ninja"') }
        def build = scripts.findIndexOf { it.contains('cmake --build "build" --config Debug') }
        def curl = scripts.findIndexOf { it.startsWith('curl -X POST') }
        assert 0 <= configure && configure < build && build < curl

        assert helper.callStack.any { it.methodName == 'cleanWs' }
        assert binding.getVariable('currentBuild').result == 'SUCCESS'
    }

    @Test
    void discordMessageIncludesBuildInfo() {
        runJenkinsfile('git-cmake-run.Jenkinsfile', CMAKE_PARAMS)

        def curl = batScripts().find { it.startsWith('curl -X POST') }
        assert curl.contains('Revision: abc123')
        assert curl.contains('Debug (x64) Game has succeeded')
        assert curl.endsWith('https://discord.example/webhook')
    }

    @Test
    void matrixRunsInnerModulesPerCombinationAndOnlyFilters() {
        runJenkinsfile('cmake-matrix-run.Jenkinsfile', CMAKE_PARAMS)

        def builds = batScripts().findAll { it.contains('cmake --build') }
        assert builds.size() == 2
        assert builds[0].contains('--config Debug')
        assert builds[1].contains('--config Release')

        // only(CMAKE_CONFIG: 'Release') { zip.pack() } runs once
        def zips = helper.callStack.findAll { it.methodName == 'powershell' }
        assert zips.size() == 1

        assert stageNames().containsAll(['Matrix', 'Debug', 'Release'])
    }

    @Ignore('Phase 3 (#5): alerts after a matrix lose the build info from the combinations')
    @Test
    void alertAfterMatrixKnowsTheBuildConfig() {
        runJenkinsfile('cmake-matrix-run.Jenkinsfile', CMAKE_PARAMS)

        def curl = batScripts().find { it.startsWith('curl -X POST') }
        assert !curl.contains(' () Game')
    }

    @Test
    void failedStageStillAlertsAndCleansUp() {
        batHandler = { Map args ->
            if (args.script.contains('cmake --build')) throw new Exception('compile error')
            args.returnStdout ? '' : 0
        }
        binding.setVariable('params', CMAKE_PARAMS)

        try {
            runScript('test/resources/jenkinsfiles/git-cmake-run.Jenkinsfile')
            assert false, 'expected the build to fail'
        } catch (Exception e) {
            assert e.message == 'compile error'
        }

        assert binding.getVariable('currentBuild').result == 'FAILURE'
        def curl = batScripts().find { it.startsWith('curl -X POST') }
        assert curl?.contains('BUILD FAILED')
        assert helper.callStack.any { it.methodName == 'cleanWs' }
    }
}
