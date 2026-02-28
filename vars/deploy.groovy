/**
 * Deployment modules.
 * Usage: deploy.steam(), deploy.itch(), deploy.gdrive(), deploy.epic()
 */

import com.buas.ModuleRegistry

def steam(Map overrides = [:]) {
    def impl = new com.buas.deploy.Steam(this)
    ModuleRegistry.register(
        category: 'deploy',
        name: 'Steam',
        params: impl.pipelineParams(overrides),
        execute: { params, ctx -> impl.execute(params, ctx) },
        hasCleanup: false
    )
}

def itch(Map overrides = [:]) {
    def impl = new com.buas.deploy.Itch(this)
    ModuleRegistry.register(
        category: 'deploy',
        name: 'itch.io',
        params: impl.pipelineParams(overrides),
        execute: { params, ctx -> impl.execute(params, ctx) },
        hasCleanup: false
    )
}

def gdrive(Map overrides = [:]) {
    def impl = new com.buas.deploy.GDrive(this)
    ModuleRegistry.register(
        category: 'deploy',
        name: 'Google Drive',
        params: impl.pipelineParams(overrides),
        execute: { params, ctx -> impl.execute(params, ctx) },
        hasCleanup: false
    )
}

def epic(Map overrides = [:]) {
    def impl = new com.buas.deploy.Epic(this)
    ModuleRegistry.register(
        category: 'deploy',
        name: 'Epic Games Store',
        params: impl.pipelineParams(overrides),
        execute: { params, ctx -> impl.execute(params, ctx) },
        hasCleanup: false
    )
}
