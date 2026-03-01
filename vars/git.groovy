/**
 * Git module.
 * Usage: git.sync()
 *
 * Direct-use methods are available after registration:
 * git.getCommitHash(), git.getCommitMessage()
 */

import groovy.transform.Field
import com.buas.ModuleRegistry
import com.buas.vcs.Git

@Field def _impl = null

def sync(Map overrides = [:]) {
    _impl = new Git(this)
    ModuleRegistry.register(
        category: 'vcs',
        name: 'Source Control',
        params: _impl.pipelineParams(overrides),
        execute: { params, ctx -> _impl.execute(params, ctx) },
        hasCleanup: false
    )
}

// Direct-use methods
def getCommitHash() { return _impl.getCommitHash() }
def getCommitMessage() { return _impl.getCommitMessage() }
