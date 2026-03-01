/**
 * Pipeline Orchestrator - Composable pipeline for student game projects.
 *
 * Usage: In your project's Jenkinsfile, compose modules:
 *
 *   library identifier: 'JenkinsLib@main',
 *       retriever: modernSCM([$class: 'GitSCMSource',
 *           remote: 'https://github.com/BredaUniversityGames/JenkinsLib'])
 *
 *   stages {
 *       perforce.sync()
 *       ue5.build()
 *       // ue5.test()
 *       steam.deploy()
 *       discord.notify()
 *   }
 *
 * Comment/uncomment modules to add/remove both their stages AND parameters.
 * Each module can accept overrides for default parameter values:
 *
 *   stages {
 *       perforce.sync(P4_HOST: 'ssl:custom.host:1666')
 *       ue5.build(BUILD_CONFIG: 'Shipping')
 *       steam.deploy()
 *       discord.notify()
 *   }
 */

import com.buas.ModuleRegistry

def call(Closure body) {
    body()
    def modules = ModuleRegistry.drain()

    // Collect parameters from all registered modules
    def allParams = [
        booleanParam(name: 'CLEAN_WORKSPACE',
                     defaultValue: params?.CLEAN_WORKSPACE != null ? params.CLEAN_WORKSPACE : true,
                     description: 'Clean workspace after build')
    ]
    modules.each { mod ->
        allParams.addAll(mod.params ?: [])
    }
    properties([parameters(allParams)])

    // Category execution order
    def categoryOrder = ['vcs', 'build', 'test', 'review', 'deploy', 'symbols']

    node('Windows') {
        ws("C:\\Jenkins\\${env.JOB_NAME}") {
            def ctx = [outputDir: "${env.WORKSPACE}\\Output"]

            try {
                for (category in categoryOrder) {
                    def mods = modules.findAll { it.category == category }
                    if (!mods) continue

                    if (category == 'deploy') {
                        stage('Deploy') {
                            def branches = [:]
                            for (mod in mods) {
                                def m = mod
                                branches[m.name] = {
                                    stage(m.name) {
                                        catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                            m.execute(params, ctx)
                                        }
                                    }
                                }
                            }
                            parallel branches
                        }
                    } else {
                        for (mod in mods) {
                            stage(mod.name) {
                                log.currStage()
                                mod.execute(params, ctx)
                            }
                        }
                    }
                }

                currentBuild.result = currentBuild.result ?: 'SUCCESS'
            } catch (err) {
                currentBuild.result = currentBuild.result ?: 'FAILURE'
                throw err
            } finally {
                def status = currentBuild.result ?: 'SUCCESS'

                // Run notification modules
                modules.findAll { it.category == 'notify' }.each { mod ->
                    try {
                        mod.notify(status, params, ctx)
                    } catch (notifyErr) {
                        log.warning("Notification failed: ${notifyErr}")
                    }
                }

                // Run module cleanup
                modules.findAll { it.hasCleanup == true }.each { mod ->
                    try {
                        mod.cleanup(params, ctx)
                    } catch (cleanupErr) {
                        log.warning("Cleanup failed for ${mod.name}: ${cleanupErr}")
                    }
                }

                if (params.CLEAN_WORKSPACE) {
                    cleanWs()
                }
            }
        }
    }
}
