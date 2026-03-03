/**
 * Matrix build support — runs inner stages for every axis combination.
 *
 * Usage:
 *   stages {
 *       perforce.sync()
 *       matrix(UE5_BUILD_PLATFORM: ['Win64', 'PS4'],
 *              UE5_BUILD_CONFIG: ['Development', 'Shipping']) {
 *           ue5.build()
 *           steam.deploy()
 *       }
 *       discord.alert()
 *   }
 *
 * Stages outside the matrix closure run once; stages inside repeat for
 * each combination of axis values (4 combinations in the example above).
 *
 * Parameters whose names match an axis key are removed from the Jenkins UI
 * since their values are controlled by the matrix.
 */

import com.buas.ModuleRegistry

def call(Map axes, Closure body) {
    // ── Validate axes ──
    if (!axes) {
        error('matrix: axes map must not be empty')
    }
    axes.each { key, values ->
        if (values instanceof List) {
            if (values.isEmpty()) {
                error("matrix: axis '${key}' must not be an empty list")
            }
        } else if (values instanceof String || values instanceof GString) {
            axes[key] = [values]
        } else {
            error("matrix: axis '${key}' must be a list or string, got: ${values}")
        }
    }

    // ── Capture inner modules ──
    ModuleRegistry.push()
    body()
    def innerModules = ModuleRegistry.pop()

    if (!innerModules) {
        error('matrix: no modules registered inside matrix block')
    }

    // ── Compute all axis combinations (cartesian product) ──
    def combinations = cartesian(axes)

    // ── Collect params, filtering out axis-controlled ones ──
    def axisNames = axes.keySet() as Set
    def seen = [] as Set
    def filteredParams = []
    innerModules.each { mod ->
        (mod.params ?: []).each { p ->
            if (!axisNames.contains(p.name) && seen.add(p.name)) {
                filteredParams.add(p)
            }
        }
    }

    // ── Register a single meta-module for the orchestrator ──
    ModuleRegistry.register(
        category:     'matrix',
        name:         'Matrix',
        axes:         axes,
        combinations: combinations,
        modules:      innerModules,
        params:       filteredParams,
        hasCleanup:   innerModules.any { it.hasCleanup == true }
    )
}

/**
 * Cartesian product of axis values.
 *
 * Given [A: [1,2], B: ['x','y']], returns:
 *   [[A:1, B:'x'], [A:1, B:'y'], [A:2, B:'x'], [A:2, B:'y']]
 */
private List cartesian(Map axes) {
    def result = [[:]
    ]
    axes.each { name, values ->
        def expanded = []
        for (combo in result) {
            for (val in values) {
                expanded.add(combo + [(name): val])
            }
        }
        result = expanded
    }
    return result
}
