package com.buas

import org.junit.Before
import org.junit.Test

class ModuleRegistryTest {

    @Before
    void reset() {
        ModuleRegistry.drain()
    }

    @Test
    void drainReturnsModulesInRegistrationOrderAndEmptiesTheRegistry() {
        ModuleRegistry.register(name: 'a')
        ModuleRegistry.register(name: 'b')

        assert ModuleRegistry.drain()*.name == ['a', 'b']
        assert ModuleRegistry.drain() == []
    }

    @Test
    void popReturnsOnlyModulesRegisteredAfterPush() {
        ModuleRegistry.register(name: 'outer')
        ModuleRegistry.push()
        ModuleRegistry.register(name: 'inner1')
        ModuleRegistry.register(name: 'inner2')

        assert ModuleRegistry.pop()*.name == ['inner1', 'inner2']
        assert ModuleRegistry.drain()*.name == ['outer']
    }

    @Test
    void nestedPushPopUnwindsOneLevelAtATime() {
        ModuleRegistry.push()
        ModuleRegistry.register(name: 'level1')
        ModuleRegistry.push()
        ModuleRegistry.register(name: 'level2')

        assert ModuleRegistry.pop()*.name == ['level2']
        assert ModuleRegistry.pop()*.name == ['level1']
        assert ModuleRegistry.drain() == []
    }

    @Test
    void popWithoutPushReturnsEmptyListAndKeepsModules() {
        ModuleRegistry.register(name: 'a')

        assert ModuleRegistry.pop() == []
        assert ModuleRegistry.drain()*.name == ['a']
    }

    @Test
    void emptyPushPopReturnsEmptyList() {
        ModuleRegistry.push()
        assert ModuleRegistry.pop() == []
        assert ModuleRegistry.drain() == []
    }
}
