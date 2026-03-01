/**
 * Epic Games Store deployment module.
 * Usage: epic.deploy()
 */

import com.buas.ModuleRegistry

def deploy(Map overrides = [:]) {
    def impl = new com.buas.deploy.Epic(this)
    ModuleRegistry.register(
        category: 'deploy',
        name: 'Epic Games Store',
        params: impl.pipelineParams(overrides),
        execute: { params, ctx -> impl.execute(params, ctx) },
        hasCleanup: false
    )
}
