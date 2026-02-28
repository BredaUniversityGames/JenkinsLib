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
}
