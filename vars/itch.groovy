/**
 * itch.io deployment module.
 * Usage: itch.deploy()
 */

import com.buas.ModuleRegistry

def deploy(Map overrides = [:]) {
    def impl = new com.buas.deploy.Itch(this)
    ModuleRegistry.register(
        category: 'deploy',
        name: 'itch.io Deploy',
        params: impl.pipelineParams(overrides),
        execute: { params, ctx -> impl.execute(params, ctx) },
        hasCleanup: false
    )
}
