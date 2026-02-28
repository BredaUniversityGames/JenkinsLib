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

// Static registry avoids CPS serialization issues — @Field variables are
// serialized with each continuation, so changes made inside body() are lost
// when the continuation resumes. Static fields are not serialized by CPS.
class ModuleRegistry {
    static List modules = Collections.synchronizedList(new ArrayList())
}

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
    ModuleRegistry.modules.add(config)
}

def call(Closure body) {
    ModuleRegistry.modules.clear()
    body()
    def modules = new ArrayList(ModuleRegistry.modules)
    ModuleRegistry.modules.clear()

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
