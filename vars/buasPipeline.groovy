import groovy.transform.Field

/**
 * BUAS Pipeline - Composable pipeline orchestrator for student game projects.
 *
 * Usage: In your project's Jenkinsfile, compose modules:
 *
 *   library identifier: 'JenkinsLib@main',
 *       retriever: modernSCM([$class: 'GitSCMSource',
 *           remote: 'https://github.com/BredaUniversityGames/JenkinsLib'])
 *
 *   buasPipeline {
 *       vcsPerforce()
 *       buildUE5()
 *       // testUE5()
 *       deploySteam()
 *       notifyDiscord()
 *   }
 *
 * Comment/uncomment modules to add/remove both their stages AND parameters.
 * Each module can accept overrides for default parameter values:
 *
 *   buasPipeline {
 *       vcsPerforce(P4_HOST: 'ssl:custom.host:1666')
 *       buildUE5(BUILD_CONFIG: 'Shipping')
 *       deploySteam()
 *       notifyDiscord()
 *   }
 */

@Field List _modules = []

/**
 * Register a module with the pipeline orchestrator.
 * Called by each module's call() method during the configuration closure.
 */
def registerModule(Map config) {
    _modules << config
}

def call(Closure body) {
    _modules = []
    body()
    def modules = new ArrayList(_modules)
    _modules = []

    // Collect parameters from all registered modules
    def allParams = [
        booleanParam(name: 'CLEAN_WORKSPACE', defaultValue: true,
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
                                            m.ref.execute(params, ctx)
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
                                mod.ref.execute(params, ctx)
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
                        mod.ref.executeNotify(status, params, ctx)
                    } catch (notifyErr) {
                        log.warning("Notification failed: ${notifyErr}")
                    }
                }

                // Run module cleanup
                modules.findAll { it.cleanup == true }.each { mod ->
                    try {
                        mod.ref.executeCleanup(params, ctx)
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
