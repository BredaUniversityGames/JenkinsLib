/**
 * Sentry debug symbols module.
 * Usage: sentry.upload()
 */

import groovy.transform.Field
import com.buas.ModuleRegistry
import com.buas.symbols.Sentry

@Field def _impl = null

def upload(Map overrides = [:]) {
    _impl = new Sentry(this)
    ModuleRegistry.register(
        category: 'symbols',
        name: 'Debug Symbols',
        params: _impl.pipelineParams(overrides),
        execute: { params, ctx -> _impl.execute(params, ctx) },
        hasCleanup: false
    )
}
