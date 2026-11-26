package com.buas.vars

import com.buas.ModuleRegistry
import com.lesfurets.jenkins.unit.BasePipelineTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/**
 * Tests for vars/matrix.groovy and vars/only.groovy, loaded as plain scripts.
 */
class MatrixTest extends BasePipelineTest {

    def matrix
    def only

    @Override
    @Before
    void setUp() {
        super.setUp()
        ModuleRegistry.drain()
        // Like Jenkins, error() aborts the pipeline
        helper.registerAllowedMethod('error', [String]) { String msg -> throw new Exception(msg) }
        matrix = loadScript('vars/matrix.groovy')
        only = loadScript('vars/only.groovy')
    }

    private static Map param(String name) { [symbol: 'string', arguments: [name: name]] }

    // ── cartesian ──

    @Test
    void cartesianProductKeepsAxisOrder() {
        assert matrix.cartesian([A: [1, 2], B: ['x', 'y']]) == [
            [A: 1, B: 'x'], [A: 1, B: 'y'], [A: 2, B: 'x'], [A: 2, B: 'y']
        ]
    }

    @Test
    void cartesianOfSingleAxis() {
        assert matrix.cartesian([A: ['only']]) == [[A: 'only']]
    }

    // ── matrix() ──

    @Test
    void matrixRegistersOneMetaModuleWithInnerModules() {
        matrix.call([PLATFORM: ['Win64', 'PS5'], CONFIG: 'Shipping']) {
            ModuleRegistry.register(category: 'build', name: 'Build', params: [param('A'), param('B')])
            ModuleRegistry.register(category: 'deploy', name: 'Deploy', params: [param('B'), param('C')], hasCleanup: true)
        }

        def modules = ModuleRegistry.drain()
        assert modules.size() == 1
        def meta = modules[0]
        assert meta.category == 'matrix'
        assert meta.modules*.name == ['Build', 'Deploy']
        // A single string axis value becomes a one-element list
        assert meta.axes == [PLATFORM: ['Win64', 'PS5'], CONFIG: ['Shipping']]
        assert meta.combinations.size() == 2
        // Parameters are de-duplicated by name
        assert meta.params*.arguments*.name == ['A', 'B', 'C']
        assert meta.hasCleanup
    }

    @Test(expected = Exception)
    void matrixRejectsEmptyAxes() {
        matrix.call([:]) {}
    }

    @Test(expected = Exception)
    void matrixRejectsEmptyAxisList() {
        matrix.call([PLATFORM: []]) {}
    }

    @Test(expected = Exception)
    void matrixRejectsBodyWithoutModules() {
        matrix.call([PLATFORM: ['Win64']]) {}
    }

    @Ignore('Phase 3 (#5): the docs say axis parameters are removed from the UI, but they are not')
    @Test
    void matrixDropsParametersControlledByAxes() {
        matrix.call([PLATFORM: ['Win64']]) {
            ModuleRegistry.register(category: 'build', name: 'Build', params: [param('PLATFORM'), param('B')])
        }
        assert ModuleRegistry.drain()[0].params*.arguments*.name == ['B']
    }

    // ── only() ──

    @Test
    void onlyAttachesNormalizedConditionToInnerModules() {
        ModuleRegistry.register(name: 'before')
        only.call([PLATFORM: 'Win64', CONFIG: ['Development', 'Shipping']]) {
            ModuleRegistry.register(name: 'steam')
            ModuleRegistry.register(name: 'itch')
        }

        def modules = ModuleRegistry.drain()
        assert modules*.name == ['before', 'steam', 'itch']
        assert modules[0].when == null
        assert modules[1].when == [PLATFORM: ['Win64'], CONFIG: ['Development', 'Shipping']]
        assert modules[2].when == modules[1].when
    }

    @Test(expected = Exception)
    void onlyRejectsEmptyCondition() {
        only.call([:]) {}
    }
}
