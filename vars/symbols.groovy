/**
 * Debug symbol modules.
 * Usage: symbols.sentry()
 */

import groovy.transform.Field
import com.buas.symbols.Sentry

@Field def _sentryImpl = null

def sentry(Map overrides = [:]) {
    _sentryImpl = new Sentry(this)
    stages.registerModule(
        category: 'symbols',
        name: 'Debug Symbols',
        params: _sentryImpl.pipelineParams(overrides),
        execute: { params, ctx -> _sentryImpl.execute(params, ctx) },
        hasCleanup: false
    )
}

// Sentry direct-use methods
def upload(Map config) { _sentryImpl.upload(config) }
