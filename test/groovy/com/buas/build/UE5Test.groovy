package com.buas.build

import com.buas.testing.StepsStub
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

class UE5Test {

    StepsStub steps
    UE5 ue5

    static final String REPORT_JSON = '''
    {
      "succeeded": 1,
      "succeededWithWarnings": 1,
      "failed": 1,
      "totalDuration": 12.5,
      "tests": [
        { "testDisplayName": "Passes", "fullTestPath": "Game.Passes", "state": "Success", "entries": [] },
        { "testDisplayName": "Warns", "fullTestPath": "Game.Warns", "state": "Success",
          "entries": [ { "event": { "type": "Warning", "message": "careful" }, "filename": "Warn.cpp", "lineNumber": 3 } ] },
        { "testDisplayName": "Fails", "fullTestPath": "Game.Fails", "state": "Fail",
          "entries": [ { "event": { "type": "Error", "message": "boom" }, "filename": "Fail.cpp", "lineNumber": 7 } ] }
      ]
    }
    '''

    @Before
    void setUp() {
        steps = new StepsStub()
        ue5 = new UE5(steps)
    }

    // ── reorderChoices ──

    @Test
    void movesCurrentChoiceToTheFront() {
        assert UE5.reorderChoices(['Win64', 'Linux', 'PS5'], 'PS5') == ['PS5', 'Win64', 'Linux']
        assert UE5.reorderChoices(['Win64', 'Linux'], 'Mac') == ['Win64', 'Linux']
    }

    // ── JUnit conversion ──

    private Node junit() {
        new XmlParser().parseText(ue5.getJUnitXMLFromJSON(REPORT_JSON))
    }

    @Test
    void writesOneTestcasePerTest() {
        def suite = junit()

        assert suite.name() == 'testsuite'
        assert suite.testcase*.@name == ['Passes', 'Warns', 'Fails']
        assert suite.testcase*.@classname == ['Game.Passes', 'Game.Warns', 'Game.Fails']
    }

    @Test
    void failingTestHasFailureWithMessageAndLocation() {
        def failure = junit().testcase.find { it.@name == 'Fails' }.failure[0]

        assert failure.@message == 'boom'
        assert failure.text() == 'Fail.cpp 7'
    }

    @Ignore('Phase 3 (#5): warnings are reported as failures')
    @Test
    void warningsAreNotFailures() {
        assert junit().testcase.find { it.@name == 'Warns' }.failure.isEmpty()
    }

    @Ignore('Phase 3 (#5): tests that passed with warnings are not counted')
    @Test
    void suiteCountsAllTests() {
        assert junit().@tests == '3'
    }

    // ── build commands ──

    private String buildScript(Map config) {
        ue5.build([engineRoot: 'C:\\UE\\UE_5.4', project: 'C:\\ws\\Game.uproject', outputDir: 'C:\\ws\\Output'] + config)
        steps.callsTo('bat')[-1].args[0].script
    }

    @Test
    void blueprintBuildPackagesAndArchives() {
        def script = buildScript(method: 'Blueprint', config: 'Shipping', platform: 'Win64')

        assert script.startsWith('"C:\\UE\\UE_5.4\\Engine\\Build\\BatchFiles\\RunUAT.bat" BuildCookRun')
        assert script.contains('-Project="C:\\ws\\Game.uproject"')
        assert script.contains('-ClientConfig=Shipping')
        assert script.contains('-Archivedirectory="C:\\ws\\Output"')
        assert !script.contains('-Clean')
    }

    @Test
    void cleanBuildAddsCleanFlag() {
        assert buildScript(method: 'Precompiled', clean: true).contains('-Clean')
    }

    @Test
    void customBuildUsesCustomFlags() {
        assert buildScript(method: 'Custom', customFlags: '-Cook -MyFlag').contains('-Cook -MyFlag')
    }

    @Test
    void unknownBuildMethodFails() {
        try {
            buildScript(method: 'Magic')
            assert false, 'expected an error'
        } catch (StepsStub.StepError e) {
            assert e.message.contains('Unknown UE5 build method: Magic')
        }
    }

    // ── engine resolution ──

    @Test
    void resolvesEngineFolderOnAgent() {
        steps.env.UE5_ENGINE_ROOT = 'C:\\UE'
        // First candidate (UE_5.4) missing, second (UE5.4) present
        steps.handlers.bat = { Map args -> args.script.contains('UE_5.4') ? 1 : 0 }

        assert ue5.resolveEnginePath([UE5_ENGINE_VERSION: '5.4']) == 'C:\\UE\\UE5.4'
    }

    @Test
    void engineResolutionRequiresEngineRoot() {
        try {
            ue5.resolveEnginePath([UE5_ENGINE_VERSION: '5.4'])
            assert false, 'expected an error'
        } catch (StepsStub.StepError e) {
            assert e.message.contains('UE5_ENGINE_ROOT')
        }
    }
}
