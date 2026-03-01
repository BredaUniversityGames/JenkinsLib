/**
 * Google Drive deployment module.
 * Usage: gdrive.deploy()
 */

import com.buas.ModuleRegistry

def deploy(Map overrides = [:]) {
    def impl = new com.buas.deploy.GDrive(this)
    ModuleRegistry.register(
        category: 'deploy',
        name: 'Google Drive',
        params: impl.pipelineParams(overrides),
        execute: { params, ctx -> impl.execute(params, ctx) },
        hasCleanup: false
    )
}
