package com.buas.build

import com.buas.testing.StepsStub
import org.junit.Before
import org.junit.Test

class ZipTest {

    StepsStub steps
    Zip zip

    @Before
    void setUp() {
        steps = new StepsStub()
        steps.env.WORKSPACE = 'C:\\ws'
        steps.handlers.bat = { Map args -> 0 }   // source directory exists
        zip = new Zip(steps)
    }

    @Test
    void resolvesPlaceholdersFromParams() {
        assert Zip.resolveTemplate('build/${PRESET}/bin', [PRESET: 'release']) == 'build/release/bin'
    }

    @Test
    void leavesUnknownPlaceholdersAlone() {
        assert Zip.resolveTemplate('build/${MISSING}/bin', [:]) == 'build/${MISSING}/bin'
    }

    @Test
    void defaultsArchiveNameToLastFolderOfSource() {
        def ctx = [:]

        zip.execute([ZIP_SOURCE_DIR: 'build/release/bin/game/'], ctx)

        assert ctx.outputDir == 'C:\\ws\\game.zip'
        assert steps.callsTo('powershell')[0].args[0].script.contains('"game.zip"')
    }

    @Test
    void usesResolvedArchiveName() {
        def ctx = [:]

        zip.execute([ZIP_SOURCE_DIR: 'out', ZIP_ARCHIVE_NAME: 'Game-${CFG}', CFG: 'Shipping'], ctx)

        assert ctx.outputDir == 'C:\\ws\\Game-Shipping.zip'
    }

    @Test
    void failsWhenSourceIsMissing() {
        steps.handlers.bat = { Map args -> 1 }
        try {
            zip.execute([ZIP_SOURCE_DIR: 'nope'], [:])
            assert false, 'expected an error'
        } catch (StepsStub.StepError e) {
            assert e.message.contains("'nope' does not exist")
        }
        assert steps.callsTo('powershell').isEmpty()
    }

    @Test
    void requiresSourceDir() {
        try {
            zip.execute([:], [:])
            assert false, 'expected an error'
        } catch (StepsStub.StepError e) {
            assert e.message.contains('ZIP_SOURCE_DIR is required')
        }
    }
}
