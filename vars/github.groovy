/**
 * GitHub module.
 * Usage: github.version(), github.release()
 *
 * github.version() computes the next semver tag from git history and stores
 * it in ctx.version and env.BUILD_VERSION for downstream modules.
 *
 * github.release() creates a GitHub Release with a generated changelog and
 * uploads matching assets.
 *
 * Requires gh CLI on the Jenkins agent and a GitHub token credential.
 */

import com.buas.ModuleRegistry

def version(Map overrides = [:]) {
    def impl = new com.buas.deploy.GitHub(this)
    ModuleRegistry.register(
        category: 'vcs',
        name: 'GitHub Version',
        params: impl.versionPipelineParams(overrides),
        execute: { params, ctx -> impl.computeVersion(params, ctx) },
        hasCleanup: false
    )
}

def release(Map overrides = [:]) {
    def impl = new com.buas.deploy.GitHub(this)
    ModuleRegistry.register(
        category: 'deploy',
        name: 'GitHub Release',
        params: impl.releasePipelineParams(overrides),
        execute: { params, ctx -> impl.createRelease(params, ctx) },
        hasCleanup: false
    )
}
