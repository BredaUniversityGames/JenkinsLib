package com.buas.testing

/**
 * Minimal stand-in for the pipeline `steps` object that src/ classes receive.
 *
 * - `params` and `env` are plain maps the test can fill in.
 * - Parameter builders (string, choice, booleanParam, credentials) return
 *   [symbol: ..., arguments: [...]], the same shape matrix.groovy reads
 *   (`p.arguments?.name`) from Jenkins' UninstantiatedDescribable.
 * - Every other step call is recorded in `calls` and returns null,
 *   unless a handler is registered in `handlers`.
 */
class StepsStub {
    Map params = [:]
    Map env = [:]
    List<Map> calls = []
    Map<String, Closure> handlers = [:]

    def string(Map args)       { describable('string', args) }
    def choice(Map args)       { describable('choice', args) }
    def booleanParam(Map args) { describable('booleanParam', args) }
    def credentials(Map args)  { describable('credentials', args) }

    def error(String message) {
        throw new StepError(message)
    }

    def methodMissing(String name, args) {
        def argList = args as List
        calls << [name: name, args: argList]
        def handler = handlers[name]
        if (handler) {
            return handler.call(*argList)
        }
        // Run a trailing closure body (node, ws, withCredentials, …)
        def last = argList ? argList[-1] : null
        if (last instanceof Closure) {
            return last.call()
        }
        return null
    }

    List<Map> callsTo(String name) {
        calls.findAll { it.name == name }
    }

    private static Map describable(String symbol, Map args) {
        [symbol: symbol, arguments: args]
    }

    static class StepError extends RuntimeException {
        StepError(String message) { super(message) }
    }
}
