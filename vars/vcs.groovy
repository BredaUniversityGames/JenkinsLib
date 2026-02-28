/**
 * Version control modules.
 * Usage: vcs.perforce(), vcs.git()
 *
 * Direct-use methods are available via the impl objects stored on this script.
 * After registration, use vcs.createTicket(), vcs.getCommitHash(), etc.
 */

import groovy.transform.Field
import com.buas.ModuleRegistry
import com.buas.vcs.Perforce
import com.buas.vcs.Git

// Store impl references for cross-module access (e.g., review.swarm() needs vcs.createTicket())
@Field def _perforceImpl = null
@Field def _gitImpl = null

def perforce(Map overrides = [:]) {
    _perforceImpl = new Perforce(this)
    ModuleRegistry.register(
        category: 'vcs',
        name: 'Source Control',
        params: _perforceImpl.pipelineParams(overrides),
        execute: { params, ctx -> _perforceImpl.execute(params, ctx) },
        hasCleanup: true,
        cleanup: { params, ctx -> _perforceImpl.executeCleanup(params, ctx) }
    )
}

def git(Map overrides = [:]) {
    _gitImpl = new Git(this)
    ModuleRegistry.register(
        category: 'vcs',
        name: 'Source Control',
        params: _gitImpl.pipelineParams(overrides),
        execute: { params, ctx -> _gitImpl.execute(params, ctx) },
        hasCleanup: false
    )
}

// Perforce direct-use methods
def createTicket(Map config) { return _perforceImpl.createTicket(config) }
def getLatestChangelist(Map config) { return _perforceImpl.getLatestChangelist(config) }
def unshelve(Map config) { _perforceImpl.unshelve(config) }
def getChangelistDescription(Map config) { return _perforceImpl.getChangelistDescription(config) }
def publish(Map config) { _perforceImpl.publish(config) }

// Git direct-use methods
def getCommitHash() { return _gitImpl.getCommitHash() }
def getCommitMessage() { return _gitImpl.getCommitMessage() }
