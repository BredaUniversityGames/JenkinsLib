/**
 * Build engine modules.
 * Usage: build.ue5(), build.vs()
 *
 * UE5 test methods are also accessible via build.runTests(), build.getTestResults(), etc.
 */

import com.buas.build.UE5
import com.buas.build.VS

// Store impl references for cross-module access (e.g., test.ue5() needs build.runTests())
private def _ue5Impl = null
private def _vsImpl = null

def ue5(Map overrides = [:]) {
    _ue5Impl = new UE5(this)
    stages.registerModule(
        category: 'build',
        name: 'Build',
        params: _ue5Impl.pipelineParams(overrides),
        execute: { params, ctx -> _ue5Impl.execute(params, ctx) },
        hasCleanup: false
    )
}

def vs(Map overrides = [:]) {
    _vsImpl = new VS(this)
    stages.registerModule(
        category: 'build',
        name: 'Build',
        params: _vsImpl.pipelineParams(overrides),
        execute: { params, ctx -> _vsImpl.execute(params, ctx) },
        hasCleanup: false
    )
}

// UE5 direct-use methods
def runTests(Map config) { _ue5Impl.runTests(config) }
def getTestResults() { return _ue5Impl.getTestResults() }
def getJUnitXMLFromJSON(String jsonContent) { return _ue5Impl.getJUnitXMLFromJSON(jsonContent) }
def fixupRedirects(Map config) { _ue5Impl.fixupRedirects(config) }
