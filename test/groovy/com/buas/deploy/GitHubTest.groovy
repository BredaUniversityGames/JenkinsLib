package com.buas.deploy

import com.buas.testing.StepsStub
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

class GitHubTest {

    StepsStub steps
    GitHub github

    @Before
    void setUp() {
        steps = new StepsStub()
        github = new GitHub(steps)
    }

    // ── bumpVersion ──

    @Test
    void bumpsPatchByDefault() {
        assert GitHub.bumpVersion('v1.2.3', 'patch') == 'v1.2.4'
        assert GitHub.bumpVersion('v1.2.3', 'unknown') == 'v1.2.4'
    }

    @Test
    void bumpsMinorAndResetsPatch() {
        assert GitHub.bumpVersion('v1.2.3', 'minor') == 'v1.3.0'
    }

    @Test
    void bumpsMajorAndResetsMinorAndPatch() {
        assert GitHub.bumpVersion('v1.2.3', 'major') == 'v2.0.0'
    }

    @Test
    void fillsInMissingVersionParts() {
        assert GitHub.bumpVersion('v1', 'patch') == 'v1.0.1'
        assert GitHub.bumpVersion('1.2', 'patch') == 'v1.2.1'
    }

    @Ignore('Phase 3 (#5): bumpVersion crashes on pre-release tags')
    @Test
    void handlesPreReleaseTags() {
        assert GitHub.bumpVersion('v1.2.3-rc1', 'patch') == 'v1.2.4'
    }

    // ── reorderChoices ──

    @Test
    void movesPreviousChoiceToTheFront() {
        assert GitHub.reorderChoices(['patch', 'minor', 'major'], 'major') == ['major', 'patch', 'minor']
    }

    @Test
    void keepsOrderWhenPreviousIsMissingOrUnknown() {
        assert GitHub.reorderChoices(['patch', 'minor'], null) == ['patch', 'minor']
        assert GitHub.reorderChoices(['patch', 'minor'], 'huge') == ['patch', 'minor']
    }

    // ── computeVersion ──

    @Test
    void computeVersionBumpsLatestTag() {
        steps.handlers.bat = { Map args -> args.returnStdout ? 'v1.10.0\nv1.9.0\n' : 0 }
        def ctx = [:]

        github.computeVersion([GH_VERSION_BUMP: 'minor'], ctx)

        assert ctx.version == 'v1.11.0'
        assert ctx.latestTag == 'v1.10.0'
        assert steps.env.BUILD_VERSION == 'v1.11.0'
    }

    @Test
    void computeVersionStartsAtV010WithoutTags() {
        steps.handlers.bat = { Map args -> args.returnStdout ? '' : 0 }
        def ctx = [:]

        github.computeVersion([:], ctx)

        assert ctx.version == 'v0.1.0'
        assert ctx.latestTag == ''
    }

    // ── generateChangelog ──

    private void stubLog(String log) {
        steps.handlers.bat = { Map args -> args.returnStdout ? log : 0 }
    }

    @Test
    void changelogKeepsOnlyConventionalCommits() {
        stubLog('- feat: add zip\n- Merge branch dev\n- fix: chunk size\n- tweak stuff')

        assert github.generateChangelog('v1.0.0') == "## What's Changed\n- feat: add zip\n- fix: chunk size"
    }

    @Test
    void changelogFallsBackWhenNothingMatches() {
        stubLog('- tweak stuff')

        assert github.generateChangelog(null) == "## What's Changed\n- New release"
    }

    @Test
    void changelogIsCappedAt20Entries() {
        stubLog((1..30).collect { "- fix: item ${it}" }.join('\n'))

        assert github.generateChangelog('v1.0.0').readLines().size() == 1 + 20
    }

    @Ignore('Phase 3 (#5): scoped commits like "fix(gdrive): ..." are dropped')
    @Test
    void changelogKeepsScopedAndBreakingCommits() {
        stubLog('- fix(gdrive): chunk size\n- feat!: new API')

        assert github.generateChangelog('v1.0.0') == "## What's Changed\n- fix(gdrive): chunk size\n- feat!: new API"
    }

    // ── createRelease ──

    @Test
    void createReleaseRequiresVersion() {
        try {
            github.createRelease([GH_CREDENTIALS_ID: 'gh'], [:])
            assert false, 'expected an error'
        } catch (StepsStub.StepError e) {
            assert e.message.contains('github.version()')
        }
    }
}
