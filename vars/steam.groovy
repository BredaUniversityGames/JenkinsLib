/**
 * Steam deployment module.
 * Usage: steam.deploy()
 */

import com.buas.ModuleRegistry

def deploy(Map overrides = [:]) {
    def impl = new com.buas.deploy.Steam(this)
    ModuleRegistry.register(
        category: 'deploy',
        name: 'Steam Deploy',
        params: impl.pipelineParams(overrides),
        execute: { params, ctx -> impl.execute(params, ctx) },
        hasCleanup: false
    )
}
