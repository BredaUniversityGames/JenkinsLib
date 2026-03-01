/**
 * Perforce/Helix Core module.
 * Usage: perforce.sync()
 *
 * Direct-use methods are available after registration:
 * perforce.createTicket(), perforce.getLatestChangelist(), etc.
 */

import groovy.transform.Field
import com.buas.ModuleRegistry
import com.buas.vcs.Perforce

@Field def _impl = null

def sync(Map overrides = [:]) {
    _impl = new Perforce(this)
    ModuleRegistry.register(
        category: 'vcs',
        name: 'Source Control',
        params: _impl.pipelineParams(overrides),
        execute: { params, ctx -> _impl.execute(params, ctx) },
        hasCleanup: true,
        cleanup: { params, ctx -> _impl.executeCleanup(params, ctx) }
    )
}

// Direct-use methods
def createTicket(Map config) { return _impl.createTicket(config) }
def getLatestChangelist(Map config) { return _impl.getLatestChangelist(config) }
def unshelve(Map config) { _impl.unshelve(config) }
def getChangelistDescription(Map config) { return _impl.getChangelistDescription(config) }
def publish(Map config) { _impl.publish(config) }
