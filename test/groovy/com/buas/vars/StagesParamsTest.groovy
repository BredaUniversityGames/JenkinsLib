package com.buas.vars

import org.junit.Test

/**
 * Snapshot of the parameters each module declares.
 *
 * Phase 1 (#3) must keep these lists identical. A phase that changes
 * parameters on purpose (e.g. Phase 4 credential types) updates the
 * snapshot in the same commit.
 */
class StagesParamsTest extends LibraryPipelineTest {

    static final Map REFRESH_ONLY = [REFRESH_PARAMETERS: true, CLEAN_WORKSPACE: false]

    static final List<String> COMMON = [
        'REFRESH_PARAMETERS:booleanParam',
        'CLEAN_WORKSPACE:booleanParam',
    ]

    @Test
    void ue5MatrixPipelineParameters() {
        runJenkinsfile('ue5-matrix.Jenkinsfile', REFRESH_ONLY)

        assert paramSignature() == [
            // perforce.sync()
            'P4_CREDENTIAL:credentials', 'P4_HOST:string', 'P4_WORKSPACE:string', 'P4_VIEW:string',
            'P4_FORCE_CLEAN:booleanParam',
            // github.version()
            'GH_CREDENTIALS_ID:credentials', 'GH_VERSION_BUMP:choice',
            // matrix { ue5.build() }
            'UE5_PROJECT_PATH:string', 'UE5_ENGINE_VERSION:string', 'UE5_BUILD_METHOD:choice',
            'UE5_CUSTOM_FLAGS:string', 'UE5_BUILD_CONFIG:choice', 'UE5_BUILD_PLATFORM:choice',
            'UE5_MATCH_BUILD_ID:booleanParam', 'UE5_CLEAN_BUILD:booleanParam',
            // ue5.test()
            'UE5_TEST_MODE:choice', 'UE5_TEST_NAMES:string', 'UE5_TEST_FILTER:choice',
            // only { steam.deploy(); gdrive.deploy() }
            'STEAM_CREDENTIAL:string', 'STEAM_CMD_PATH:string', 'STEAM_APP_ID:string', 'STEAM_DEPOT_ID:string',
            'STEAM_BRANCH:string',
            'GDRIVE_CREDENTIALS_ID:credentials', 'GDRIVE_FOLDER_ID:string',
            // itch.deploy()
            'ITCH_CREDENTIALS_ID:credentials', 'ITCH_TARGET:string',
            // epic.deploy()
            'EPIC_BPT_PATH:string', 'EPIC_ORG_ID:string', 'EPIC_PRODUCT_ID:string', 'EPIC_ARTIFACT_ID:string',
            'EPIC_CLIENT_ID:string', 'EPIC_CLIENT_SECRET_ID:string',
            // sentry.upload()
            'SENTRY_CLI_PATH:string', 'SENTRY_AUTH_TOKEN_ID:string', 'SENTRY_ORG:string', 'SENTRY_PROJECT:string',
            // swarm.review()
            'SWARM_URL:string', 'SWARM_USER:string',
            // github.release()
            'GH_RELEASE_ASSETS:string', 'GH_RELEASE_NAME:string', 'GH_RELEASE_DRAFT:booleanParam',
            'GH_RELEASE_PRERELEASE:booleanParam',
            // discord.alert()
            'DISCORD_WEBHOOK:string',
        ] + COMMON
    }

    @Test
    void gitCmakeVsPipelineParameters() {
        runJenkinsfile('git-cmake-vs.Jenkinsfile', REFRESH_ONLY)

        assert paramSignature() == [
            // git.sync()
            'GIT_REPO_URL:string', 'GIT_BRANCH:choice', 'GIT_CREDENTIALS_ID:credentials',
            // cmake.build() without presets
            'CMAKE_SOURCE_DIR:string', 'CMAKE_BUILD_DIR:string', 'CMAKE_GENERATOR:choice', 'CMAKE_CONFIG:choice',
            'CMAKE_PLATFORM:choice', 'CMAKE_ARGS:string', 'CMAKE_BUILD_ARGS:string',
            // cmake.test() without presets declares nothing; cmake.workflow() without presets registers nothing
            // cmake.pack() without presets
            'CMAKE_CPACK_GENERATOR:choice', 'CMAKE_CPACK_ARGS:string',
            // zip.pack()
            'ZIP_SOURCE_DIR:string', 'ZIP_ARCHIVE_NAME:string',
            // vs.build()
            'VS_MSBUILD_PATH:string', 'VS_PROJECT_PATH:string', 'VS_CONFIG:string', 'VS_PLATFORM:string',
            // vs.test()
            'VS_TEST_FRAMEWORK:choice', 'VS_TEST_EXECUTABLE:string',
        ] + COMMON
    }

    @Test
    void refreshOnlyDoesNotBuild() {
        runJenkinsfile('ue5-matrix.Jenkinsfile', REFRESH_ONLY)

        assert binding.getVariable('currentBuild').result == 'NOT_BUILT'
        assert stageNames().isEmpty()
        assert batScripts().isEmpty()
    }

    @Test
    void overridesBecomeDefaults() {
        runJenkinsfile('overrides.Jenkinsfile', REFRESH_ONLY)

        def byName = declaredParams.collectEntries { [(it.arguments.name): it.arguments] }
        assert byName.P4_HOST.defaultValue == 'ssl:custom:1666'
        assert byName.UE5_BUILD_CONFIG.choices[0] == 'Development'   // choice lists ignore plain overrides
        assert byName.UE5_BUILD_PLATFORM.choices == ['Linux', 'Win64']
    }

    @Test
    void previousValuesBecomeDefaults() {
        runJenkinsfile('overrides.Jenkinsfile', REFRESH_ONLY + [P4_VIEW: '//depot/game/...', UE5_BUILD_PLATFORM: 'Win64'])

        def byName = declaredParams.collectEntries { [(it.arguments.name): it.arguments] }
        assert byName.P4_VIEW.defaultValue == '//depot/game/...'
        assert byName.UE5_BUILD_PLATFORM.choices == ['Win64', 'Linux']
    }
}
