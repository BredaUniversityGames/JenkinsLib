/**
 * Google Drive deployment module.
 * Usage: gdrive.deploy()
 */

import com.buas.ModuleRegistry

def deploy(Map overrides = [:]) {
    def impl = new com.buas.deploy.GDrive(this)
    ModuleRegistry.register(
        category: 'deploy',
        name: 'GDrive Deploy',
        params: impl.pipelineParams(overrides),
        execute: { params, ctx -> impl.execute(params, ctx) },
        hasCleanup: false
    )
}
