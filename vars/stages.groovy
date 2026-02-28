import groovy.transform.Field

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
 *       vcs.perforce()
 *       build.ue5()
 *       // test.ue5()
 *       deploy.steam()
 *       notify.discord()
 *   }
 *
 * Comment/uncomment modules to add/remove both their stages AND parameters.
 * Each module can accept overrides for default parameter values:
 *
 *   stages {
 *       vcs.perforce(P4_HOST: 'ssl:custom.host:1666')
 *       build.ue5(BUILD_CONFIG: 'Shipping')
 *       deploy.steam()
 *       notify.discord()
 *   }
 */

@Field List _modules = []

/**
 * Register a module with the pipeline orchestrator.
 * Called by each module's registration method during the configuration closure.
 *
 * Module config map supports:
 *   category  - execution category (vcs, build, test, review, deploy, symbols)
 *   name      - display name for the stage
 *   params    - list of Jenkins parameter definitions
 *   execute   - closure: { params, ctx -> ... }
 *   hasCleanup - boolean, whether this module has cleanup
 *   cleanup   - closure: { params, ctx -> ... } (if hasCleanup is true)
 *   notify    - closure: { status, params, ctx -> ... } (for notify category)
 */
def registerModule(Map config) {
    log.debug("registerModule called: category=${config.category}, name=${config.name}, params=${config.params?.size() ?: 0}")
    _modules << config
    log.debug("_modules size after add: ${_modules.size()}")
}

def call(Closure body) {
    _modules = []
    log.debug("Before body(), _modules size: ${_modules.size()}")
    body()
    log.debug("After body(), _modules size: ${_modules.size()}")
    def modules = new ArrayList(_modules)
    _modules = []

    // Collect parameters from all registered modules
    log.debug("modules count: ${modules.size()}")
    def allParams = [
        booleanParam(name: 'CLEAN_WORKSPACE', defaultValue: true,
                     description: 'Clean workspace after build')
    ]
    modules.each { mod ->
        log.debug("Adding params from module: ${mod.name}, params: ${mod.params?.size() ?: 0}")
        allParams.addAll(mod.params ?: [])
    }
    log.debug("Total allParams: ${allParams.size()}")
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
