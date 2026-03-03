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
 *       discord.alert()
 *   }
 *
 * Matrix builds — repeat a set of stages for every axis combination:
 *
 *   stages {
 *       perforce.sync()
 *       matrix(UE5_BUILD_PLATFORM: ['Win64', 'PS4'],
 *              UE5_BUILD_CONFIG: ['Development', 'Shipping']) {
 *           ue5.build()
 *           steam.deploy()
 *       }
 *       discord.alert()
 *   }
 *
 * Comment/uncomment modules to add/remove both their stages AND parameters.
 * Each module can accept overrides for default parameter values:
 *
 *   stages {
 *       perforce.sync(P4_HOST: 'ssl:custom.host:1666')
 *       ue5.build(UE5_BUILD_CONFIG: 'Shipping')
 *       steam.deploy()
 *       discord.alert()
 *   }
 */

import com.buas.ModuleRegistry

def call(Closure body) {
    body()
    def modules = ModuleRegistry.drain()

    // Validate at most one matrix block
    def matrixCount = modules.count { it.category == 'matrix' }
    if (matrixCount > 1) {
        error("Only one matrix block is supported per pipeline. Found ${matrixCount}.")
    }

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

    // Split modules into pre-matrix, matrix, post-matrix by registration order
    def matrixIndex = modules.findIndexOf { it.category == 'matrix' }
    def preMatrixModules, matrixModule, postMatrixModules

    if (matrixIndex >= 0) {
        preMatrixModules  = matrixIndex > 0 ? modules[0..<matrixIndex] : []
        matrixModule      = modules[matrixIndex]
        postMatrixModules = matrixIndex < modules.size() - 1 ? modules[(matrixIndex + 1)..<modules.size()] : []
    } else {
        preMatrixModules  = modules
        matrixModule      = null
        postMatrixModules = []
    }

    // Flatten all modules (including matrix inner modules) for finally-block
    def allModulesFlat = modules.collectMany { mod ->
        mod.category == 'matrix' ? (mod.modules ?: []) : [mod]
    }

    // Category execution order
    def categoryOrder = ['vcs', 'build', 'test', 'review', 'deploy', 'symbols']

    node('Windows') {
        ws("C:\\Jenkins\\${env.JOB_NAME}") {
            def ctx = [outputDir: "${env.WORKSPACE}\\Output"]

            try {
                // Phase 1: Pre-matrix modules
                executeModulesByCategory(preMatrixModules, categoryOrder, params, ctx)

                // Phase 2: Matrix (if present)
                if (matrixModule) {
                    executeMatrix(matrixModule, categoryOrder, ctx)
                }

                // Phase 3: Post-matrix modules
                executeModulesByCategory(postMatrixModules, categoryOrder, params, ctx)

                currentBuild.result = currentBuild.result ?: 'SUCCESS'
            } catch (err) {
                currentBuild.result = currentBuild.result ?: 'FAILURE'
                throw err
            } finally {
                def status = currentBuild.result ?: 'SUCCESS'

                // Run alert modules (from all modules, including matrix inner modules)
                allModulesFlat.findAll { it.category == 'alert' }.each { mod ->
                    stage(mod.name) {
                        try {
                            mod.alert(status, params, ctx)
                        } catch (alertErr) {
                            log.warning("Alert failed: ${alertErr}")
                        }
                    }
                }

                // Run module cleanup (from all modules, including matrix inner modules)
                allModulesFlat.findAll { it.hasCleanup == true }.each { mod ->
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

/**
 * Execute modules grouped by category, in category order.
 * Deploy modules run in parallel; all others run sequentially.
 */
private void executeModulesByCategory(List modules, List categoryOrder, effectiveParams, Map ctx) {
    for (category in categoryOrder) {
        def mods = modules.findAll { it.category == category && matchesCondition(it, effectiveParams) }
        if (!mods) continue

        if (category == 'deploy') {
            stage('Deploy') {
                def branches = [:]
                for (mod in mods) {
                    def m = mod
                    branches[m.name] = {
                        stage(m.name) {
                            catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                                m.execute(effectiveParams, ctx)
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
                    mod.execute(effectiveParams, ctx)
                }
            }
        }
    }
}

/**
 * Check whether a module's 'when' condition (if any) matches the current params.
 * Condition values are lists — the param value must be contained in the list.
 */
private boolean matchesCondition(Map mod, effectiveParams) {
    def condition = mod.when
    if (!condition) return true
    return condition.every { key, allowedValues ->
        def actual = effectiveParams[key]
        return actual != null && allowedValues.contains(actual)
    }
}

/**
 * Execute the matrix: iterate over each axis combination sequentially,
 * running inner modules by category for each combination.
 */
private void executeMatrix(Map matrixModule, List categoryOrder, Map ctx) {
    def combinations = matrixModule.combinations
    def innerModules = matrixModule.modules

    stage('Matrix') {
        for (int i = 0; i < combinations.size(); i++) {
            def combo = combinations[i]
            def comboLabel = combo.collect { k, v -> "${v}" }.join(' / ')

            stage(comboLabel) {
                // Merge axis values into params so modules read the correct values
                def effectiveParams = new LinkedHashMap(params)
                combo.each { key, value ->
                    effectiveParams[key] = value
                }

                // Clone context with a per-combination output directory
                def comboCtx = new LinkedHashMap(ctx)
                def subDir = combo.values().collect { "${it}" }.join('\\')
                comboCtx.outputDir = "${ctx.outputDir}\\${subDir}"

                executeModulesByCategory(innerModules, categoryOrder, effectiveParams, comboCtx)
            }
        }
    }
}
