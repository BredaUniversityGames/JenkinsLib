/**
 * BUAS Pipeline - Uber-template for student game projects.
 *
 * Usage: In your project's Jenkinsfile, simply call:
 *
 *   library identifier: 'JenkinsLib@main',
 *       retriever: modernSCM([$class: 'GitSCMSource',
 *           remote: 'https://github.com/BredaUniversityGames/JenkinsLib'])
 *
 *   buasPipeline()
 *
 * Then configure all options via the Jenkins UI parameters on first run.
 *
 * You can also pass defaults from code:
 *
 *   buasPipeline(
 *       VCS_TYPE: 'Perforce',
 *       BUILD_ENGINE: 'UE5',
 *       DEPLOY_STEAM: true
 *   )
 */
def call(Map overrides = [:]) {
    pipeline {
        agent {
            node {
                label 'Win64'
                customWorkspace "C:\\Jenkins\\${env.JOB_NAME}"
            }
        }

        parameters {
            // ── VCS ──
            choice(name: 'VCS_TYPE', choices: ['Perforce', 'Git'],
                   description: 'Version control system')
            // Git params
            string(name: 'GIT_REPO_URL', defaultValue: '',
                   description: 'Git repository URL (only for Git VCS)')
            string(name: 'GIT_BRANCH', defaultValue: 'main',
                   description: 'Git branch to build')
            string(name: 'GIT_CREDENTIALS_ID', defaultValue: '',
                   description: 'Jenkins credentials ID for Git auth (leave empty for public repos)')
            // Perforce params
            string(name: 'P4_CREDENTIAL', defaultValue: '',
                   description: 'Jenkins credentials ID for Perforce')
            string(name: 'P4_HOST', defaultValue: 'ssl:perforce.buas.nl:1666',
                   description: 'Perforce server host')
            string(name: 'P4_WORKSPACE', defaultValue: '',
                   description: 'Perforce workspace template name')
            string(name: 'P4_MAPPING', defaultValue: '',
                   description: 'Perforce depot view mapping (for depot source)')
            booleanParam(name: 'P4_FORCE_CLEAN', defaultValue: false,
                         description: 'Force clean Perforce sync')
            booleanParam(name: 'P4_USE_DEPOT_SOURCE', defaultValue: false,
                         description: 'Use depot source instead of workspace template')

            // ── BUILD ENGINE ──
            choice(name: 'BUILD_ENGINE', choices: ['UE5', 'VisualStudio'],
                   description: 'Build system to use')

            // UE5 params
            choice(name: 'UE5_BUILD_METHOD',
                   choices: ['Blueprint', 'Precompiled', 'Custom'],
                   description: 'UE5 build method')
            string(name: 'UE5_ENGINE_ROOT', defaultValue: '',
                   description: 'Path to UE5 engine root (e.g. C:\\UE_5.3)')
            string(name: 'UE5_PROJECT_PATH', defaultValue: '',
                   description: 'Absolute path to .uproject file')
            string(name: 'UE5_PROJECT_NAME', defaultValue: '',
                   description: 'Project name (without extension)')
            string(name: 'UE5_CUSTOM_FLAGS', defaultValue: '-Cook -Allmaps -Build -Stage -Pak -Rocket -Prereqs -Package',
                   description: 'Custom RunUAT flags (only for Custom build method)')
            choice(name: 'BUILD_CONFIG',
                   choices: ['Development', 'Shipping', 'DebugGame', 'Debug', 'Test'],
                   description: 'Build configuration')
            choice(name: 'BUILD_PLATFORM',
                   choices: ['Win64', 'Linux', 'PS5'],
                   description: 'Target platform')

            // VS params
            string(name: 'VS_MSBUILD_PATH', defaultValue: '',
                   description: 'Path to MSBuild.exe')
            string(name: 'VS_PROJECT_PATH', defaultValue: '',
                   description: 'Path to .sln or .vcxproj file')
            string(name: 'VS_CONFIG', defaultValue: 'Debug',
                   description: 'VS build configuration')
            string(name: 'VS_PLATFORM', defaultValue: 'x64',
                   description: 'VS target platform')

            // ── TESTING ──
            booleanParam(name: 'RUN_TESTS', defaultValue: false,
                         description: 'Run automated tests after build')
            choice(name: 'UE5_TEST_MODE',
                   choices: ['RunAll', 'RunNamed', 'RunFiltered'],
                   description: 'UE5 test execution mode')
            string(name: 'UE5_TEST_NAMES', defaultValue: '',
                   description: 'Semicolon-separated test names (for RunNamed mode)')
            choice(name: 'UE5_TEST_FILTER',
                   choices: ['Product', 'Smoke', 'Engine', 'Stress', 'Perf'],
                   description: 'Test filter category (for RunFiltered mode)')
            choice(name: 'VS_TEST_FRAMEWORK',
                   choices: ['None', 'CTest', 'GoogleTest'],
                   description: 'VS test framework to use')
            string(name: 'VS_TEST_EXECUTABLE', defaultValue: '',
                   description: 'Path to test executable (GoogleTest) or CTest build dir')

            // ── DEPLOYMENT ──
            booleanParam(name: 'DEPLOY_STEAM', defaultValue: false,
                         description: 'Deploy to Steam')
            string(name: 'STEAM_CREDENTIAL', defaultValue: '',
                   description: 'Jenkins credential ID for Steam (username/password)')
            string(name: 'STEAM_CMD_PATH', defaultValue: '',
                   description: 'Path to steamcmd.exe')
            string(name: 'STEAM_APP_ID', defaultValue: '',
                   description: 'Steam App ID')
            string(name: 'STEAM_DEPOT_ID', defaultValue: '',
                   description: 'Steam Depot ID')
            string(name: 'STEAM_BRANCH', defaultValue: '',
                   description: 'Steam branch to set live (leave empty for none)')

            booleanParam(name: 'DEPLOY_ITCH', defaultValue: false,
                         description: 'Deploy to itch.io')
            string(name: 'ITCH_BUTLER_PATH', defaultValue: '',
                   description: 'Path to Butler executable')
            string(name: 'ITCH_CREDENTIALS_ID', defaultValue: '',
                   description: 'Jenkins credential ID for Butler API key')
            string(name: 'ITCH_TARGET', defaultValue: '',
                   description: 'itch.io target (user/game:channel)')

            booleanParam(name: 'DEPLOY_GDRIVE', defaultValue: false,
                         description: 'Deploy to Google Drive')
            string(name: 'GDRIVE_CREDENTIALS_ID', defaultValue: '',
                   description: 'Jenkins credential ID for GDrive service account file')
            string(name: 'GDRIVE_FOLDER_ID', defaultValue: '',
                   description: 'Google Drive parent folder ID')
            string(name: 'GDRIVE_CHUNK_MULTIPLIER', defaultValue: '16',
                   description: 'Upload chunk size multiplier (higher = faster but more memory)')

            booleanParam(name: 'DEPLOY_EPIC', defaultValue: false,
                         description: 'Deploy to Epic Games Store')
            string(name: 'EPIC_BPT_PATH', defaultValue: '',
                   description: 'Path to BuildPatchTool.exe')
            string(name: 'EPIC_ORG_ID', defaultValue: '',
                   description: 'Epic organization ID')
            string(name: 'EPIC_PRODUCT_ID', defaultValue: '',
                   description: 'Epic product ID')
            string(name: 'EPIC_ARTIFACT_ID', defaultValue: '',
                   description: 'Epic artifact ID')
            string(name: 'EPIC_CLIENT_ID', defaultValue: '',
                   description: 'Epic client ID')
            string(name: 'EPIC_CLIENT_SECRET_ID', defaultValue: '',
                   description: 'Jenkins credential ID for Epic client secret')

            // ── NOTIFICATIONS ──
            booleanParam(name: 'NOTIFY_DISCORD', defaultValue: true,
                         description: 'Send Discord notifications')
            string(name: 'DISCORD_WEBHOOK', defaultValue: '',
                   description: 'Discord webhook URL')

            // ── OPTIONAL STAGES ──
            booleanParam(name: 'ENABLE_SWARM_REVIEW', defaultValue: false,
                         description: 'Enable Helix Swarm code review integration')
            string(name: 'SWARM_URL', defaultValue: '',
                   description: 'Swarm server URL')
            string(name: 'SWARM_USER', defaultValue: '',
                   description: 'Swarm user ID')

            booleanParam(name: 'UPLOAD_SENTRY_SYMBOLS', defaultValue: false,
                         description: 'Upload debug symbols to Sentry')
            string(name: 'SENTRY_CLI_PATH', defaultValue: '',
                   description: 'Path to sentry-cli.exe')
            string(name: 'SENTRY_AUTH_TOKEN_ID', defaultValue: '',
                   description: 'Jenkins credential ID for Sentry auth token')
            string(name: 'SENTRY_ORG', defaultValue: '',
                   description: 'Sentry organization slug')
            string(name: 'SENTRY_PROJECT', defaultValue: '',
                   description: 'Sentry project slug')

            booleanParam(name: 'MATCH_BUILD_ID', defaultValue: false,
                         description: 'Run MatchBuildID.py before UE5 build (for precompiled engines with plugins)')

            booleanParam(name: 'CLEAN_WORKSPACE', defaultValue: true,
                         description: 'Clean workspace after build')
        }

        stages {
            stage('Source Control') {
                steps {
                    script {
                        log.currStage()
                        if (params.VCS_TYPE == 'Perforce') {
                            vcsPerforce.checkout(
                                credential:     params.P4_CREDENTIAL,
                                host:           params.P4_HOST,
                                workspace:      params.P4_WORKSPACE,
                                mapping:        params.P4_MAPPING,
                                forceClean:     params.P4_FORCE_CLEAN,
                                useDepotSource: params.P4_USE_DEPOT_SOURCE
                            )
                        } else {
                            vcsGit.checkout(
                                url:           params.GIT_REPO_URL,
                                branch:        params.GIT_BRANCH,
                                credentialsId: params.GIT_CREDENTIALS_ID
                            )
                        }
                    }
                }
            }

            stage('Pre-Build') {
                when { expression { return params.MATCH_BUILD_ID && params.BUILD_ENGINE == 'UE5' } }
                steps {
                    script {
                        log.currStage()
                        def projectDir = params.UE5_PROJECT_PATH.substring(0,
                            params.UE5_PROJECT_PATH.lastIndexOf('\\'))
                        utilPython.runScript(
                            "${env.WORKSPACE}\\JenkinsLib\\scripts\\MatchBuildID.py",
                            "\"${projectDir}\" \"${params.UE5_ENGINE_ROOT}\" \"false\""
                        )
                    }
                }
            }

            stage('Build') {
                steps {
                    script {
                        log.currStage()
                        def outputDir = "${env.WORKSPACE}\\Output"

                        if (params.BUILD_ENGINE == 'UE5') {
                            buildUE5.build(
                                engineRoot:  params.UE5_ENGINE_ROOT,
                                projectName: params.UE5_PROJECT_NAME,
                                project:     params.UE5_PROJECT_PATH,
                                config:      params.BUILD_CONFIG,
                                platform:    params.BUILD_PLATFORM,
                                outputDir:   outputDir,
                                method:      params.UE5_BUILD_METHOD,
                                customFlags: params.UE5_CUSTOM_FLAGS
                            )
                        } else {
                            buildVS.build(
                                msbuildPath: params.VS_MSBUILD_PATH,
                                projectPath: params.VS_PROJECT_PATH,
                                config:      params.VS_CONFIG,
                                platform:    params.VS_PLATFORM
                            )
                        }
                    }
                }
            }

            stage('Test') {
                when { expression { return params.RUN_TESTS } }
                steps {
                    script {
                        log.currStage()
                        if (params.BUILD_ENGINE == 'UE5') {
                            buildUE5.runTests(
                                engineRoot: params.UE5_ENGINE_ROOT,
                                project:    params.UE5_PROJECT_PATH,
                                mode:       params.UE5_TEST_MODE,
                                testNames:  params.UE5_TEST_NAMES,
                                testFilter: params.UE5_TEST_FILTER,
                                config:     params.BUILD_CONFIG,
                                platform:   params.BUILD_PLATFORM
                            )

                            // Publish test results
                            def testJson = buildUE5.getTestResults()
                            def junitXml = buildUE5.getJUnitXMLFromJSON(testJson)
                            writeFile file: 'Logs/UnitTestsReport/junit.xml', text: junitXml
                            junit testResults: 'Logs/UnitTestsReport/junit.xml', allowEmptyResults: true

                            // Send test report notification
                            notify.sendTestReport(testJson, params)
                        } else if (params.VS_TEST_FRAMEWORK != 'None') {
                            testRunner.run(
                                framework:  params.VS_TEST_FRAMEWORK,
                                executable: params.VS_TEST_EXECUTABLE,
                                config:     params.VS_CONFIG
                            )
                        }
                    }
                }
            }

            stage('Swarm Review') {
                when { expression { return params.ENABLE_SWARM_REVIEW && params.VCS_TYPE == 'Perforce' } }
                steps {
                    script {
                        log.currStage()
                        def ticket = vcsPerforce.createTicket(
                            credential: params.P4_CREDENTIAL,
                            host: params.P4_HOST
                        )

                        def response = reviewSwarm.createReview(
                            user: params.SWARM_USER,
                            ticket: ticket,
                            swarmUrl: params.SWARM_URL,
                            changelistId: env.P4_CHANGELIST
                        )

                        def reviewId = reviewSwarm.getReviewID(response)
                        def author = reviewSwarm.getReviewAuthor(response)

                        notify.sendReviewNotification([
                            reviewId: reviewId,
                            author: author,
                            swarmUrl: params.SWARM_URL,
                            buildStatus: currentBuild.currentResult ?: 'not built'
                        ], params)
                    }
                }
            }

            stage('Deploy') {
                when { expression {
                    return params.DEPLOY_STEAM || params.DEPLOY_ITCH || params.DEPLOY_GDRIVE || params.DEPLOY_EPIC
                } }
                parallel {
                    stage('Steam') {
                        when { expression { return params.DEPLOY_STEAM } }
                        steps {
                            script {
                                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                    deploySteam.deploy(
                                        credential:   params.STEAM_CREDENTIAL,
                                        steamCmdPath: params.STEAM_CMD_PATH,
                                        appId:        params.STEAM_APP_ID,
                                        depotId:      params.STEAM_DEPOT_ID,
                                        contentRoot:  "${env.WORKSPACE}\\Output",
                                        platform:     params.BUILD_PLATFORM,
                                        branch:       params.STEAM_BRANCH
                                    )
                                }
                            }
                        }
                    }
                    stage('itch.io') {
                        when { expression { return params.DEPLOY_ITCH } }
                        steps {
                            script {
                                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                    deployItch.deploy(
                                        butlerPath:    params.ITCH_BUTLER_PATH,
                                        credentialsId: params.ITCH_CREDENTIALS_ID,
                                        source:        "${env.WORKSPACE}\\Output",
                                        target:        params.ITCH_TARGET,
                                        platform:      params.BUILD_PLATFORM
                                    )
                                }
                            }
                        }
                    }
                    stage('Google Drive') {
                        when { expression { return params.DEPLOY_GDRIVE } }
                        steps {
                            script {
                                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                    deployGDrive.deploy(
                                        credentialsId:   params.GDRIVE_CREDENTIALS_ID,
                                        source:          "${env.WORKSPACE}\\Output",
                                        folderId:        params.GDRIVE_FOLDER_ID,
                                        chunkMultiplier: params.GDRIVE_CHUNK_MULTIPLIER,
                                        platform:        params.BUILD_PLATFORM
                                    )
                                }
                            }
                        }
                    }
                    stage('Epic Games Store') {
                        when { expression { return params.DEPLOY_EPIC } }
                        steps {
                            script {
                                catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                    deployEpic.deploy(
                                        bptPath:        params.EPIC_BPT_PATH,
                                        orgId:          params.EPIC_ORG_ID,
                                        productId:      params.EPIC_PRODUCT_ID,
                                        artifactId:     params.EPIC_ARTIFACT_ID,
                                        clientId:       params.EPIC_CLIENT_ID,
                                        clientSecretId: params.EPIC_CLIENT_SECRET_ID,
                                        contentDir:     "${env.WORKSPACE}\\Output",
                                        platform:       params.BUILD_PLATFORM
                                    )
                                }
                            }
                        }
                    }
                }
            }

            stage('Debug Symbols') {
                when { expression { return params.UPLOAD_SENTRY_SYMBOLS } }
                steps {
                    script {
                        log.currStage()
                        withCredentials([string(credentialsId: params.SENTRY_AUTH_TOKEN_ID, variable: 'SENTRY_TOKEN')]) {
                            symbolsSentry.upload(
                                cliPath:   params.SENTRY_CLI_PATH,
                                authToken: SENTRY_TOKEN,
                                org:       params.SENTRY_ORG,
                                project:   params.SENTRY_PROJECT,
                                outputDir: "${env.WORKSPACE}\\Output"
                            )
                        }
                    }
                }
            }
        }

        post {
            success {
                script {
                    notify.send('SUCCESS', params)
                }
            }
            unstable {
                script {
                    notify.send('UNSTABLE', params)
                }
            }
            failure {
                script {
                    notify.send('FAILURE', params)
                }
            }
            aborted {
                script {
                    notify.send('ABORTED', params)
                }
            }
            cleanup {
                script {
                    if (params.VCS_TYPE == 'Perforce' && params.P4_CREDENTIAL) {
                        try {
                            vcsPerforce.cleanup(
                                credential: params.P4_CREDENTIAL,
                                workspace:  params.P4_WORKSPACE,
                                mapping:    params.P4_MAPPING
                            )
                        } catch (err) {
                            log.warning("P4 cleanup failed: ${err}")
                        }
                    }
                    if (params.CLEAN_WORKSPACE) {
                        cleanWs()
                    }
                }
            }
        }
    }
}
