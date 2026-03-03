package com.buas

/**
 * Central registry for pipeline modules.
 *
 * Modules register themselves during the stages { } configuration closure
 * via register(). The orchestrator then calls drain() to retrieve all
 * registered modules and clear the registry for the next run.
 *
 * Uses static fields to avoid CPS serialization issues — @Field variables
 * on vars/ scripts are serialized with each continuation, so changes made
 * inside closures are lost when the continuation resumes.
 */
class ModuleRegistry {
    private static List modules = []

    static void register(Map config) {
        modules += [config]
    }

    static List drain() {
        def result = [] + modules
        modules = []
        return result
    }

    private static final Map SENTINEL = Collections.unmodifiableMap([__sentinel__: true])

    /**
     * Pushes a sentinel marker onto the registry.
     * Call before executing a nested closure (e.g. matrix body).
     */
    static void push() {
        modules += [SENTINEL]
    }

    /**
     * Removes and returns all modules registered after the most recent sentinel.
     * The sentinel itself is also removed.
     */
    static List pop() {
        def idx = modules.lastIndexOf(SENTINEL)
        if (idx < 0) {
            return []
        }
        def inner = modules.drop(idx + 1)
        modules = modules.take(idx)
        return inner
    }
}
