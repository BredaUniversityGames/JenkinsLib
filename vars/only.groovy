/**
 * Conditional filter — runs inner stages only when the current
 * matrix combination matches the given condition.
 *
 * Usage (inside a matrix block):
 *   matrix(UE5_BUILD_PLATFORM: ['Win64', 'PS4'],
 *          UE5_BUILD_CONFIG: ['Development', 'Shipping']) {
 *       ue5.build()
 *       only(UE5_BUILD_PLATFORM: 'Win64', UE5_BUILD_CONFIG: 'Shipping') {
 *           steam.deploy()
 *       }
 *   }
 *
 * Condition values can be a string (exact match) or a list (match any):
 *   only(UE5_BUILD_PLATFORM: ['Win64', 'Linux']) { ... }
 */

import com.buas.ModuleRegistry

def call(Map condition, Closure body) {
    if (!condition) {
        error('only: condition map must not be empty')
    }

    // Normalize string values to single-element lists for uniform matching
    condition.each { key, value ->
        if (value instanceof String || value instanceof GString) {
            condition[key] = [value]
        } else if (!(value instanceof List)) {
            error("only: condition '${key}' must be a string or list, got: ${value}")
        }
    }

    // Capture inner modules
    ModuleRegistry.push()
    body()
    def innerModules = ModuleRegistry.pop()

    // Re-register each module with the condition attached
    innerModules.each { mod ->
        mod.when = condition
        ModuleRegistry.register(mod)
    }
}
