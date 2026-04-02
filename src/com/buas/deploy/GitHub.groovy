package com.buas.deploy

/**
 * GitHub Release deployment.
 * Computes the next semver tag from git history, generates a changelog
 * from conventional commits, and creates a GitHub Release with assets
 * via the gh CLI.
 *
 * Requires:
 *   - gh CLI installed on the Jenkins agent
 *   - A Jenkins Username/Password credential where the password is a GitHub token
 *     (the same credential type used by git.sync(), so they can be shared)
 */
class GitHub implements Serializable {
    def steps

    GitHub(steps) {
        this.steps = steps
    }

    def versionPipelineParams(Map overrides = [:]) {
        def prev = steps.params ?: [:]
        return [
            steps.credentials(name: 'GH_CREDENTIALS_ID',
                defaultValue: overrides.GH_CREDENTIALS_ID ?: prev.GH_CREDENTIALS_ID ?: '',
                credentialType: 'com.cloudbees.plugins.credentials.common.StandardCredentials',
                description: 'Jenkins credential for GitHub (Username with password — same as GIT_CREDENTIALS_ID)'),
            steps.choice(name: 'GH_VERSION_BUMP',
                choices: reorderChoices(['patch', 'minor', 'major'], prev.GH_VERSION_BUMP),
                description: 'Which semver component to increment')
        ]
    }

    def releasePipelineParams(Map overrides = [:]) {
        def prev = steps.params ?: [:]
        return [
            steps.string(name: 'GH_RELEASE_ASSETS',
                defaultValue: overrides.GH_RELEASE_ASSETS ?: prev.GH_RELEASE_ASSETS ?: '*.zip',
                description: 'Glob pattern for files to attach to the release'),
            steps.string(name: 'GH_RELEASE_NAME',
                defaultValue: overrides.GH_RELEASE_NAME ?: prev.GH_RELEASE_NAME ?: 'Release ${VERSION}',
                description: 'Release title (use ${VERSION} as placeholder)'),
            steps.booleanParam(name: 'GH_RELEASE_DRAFT',
                defaultValue: overrides.GH_RELEASE_DRAFT ?: prev.GH_RELEASE_DRAFT ?: false,
                description: 'Create as draft release'),
            steps.booleanParam(name: 'GH_RELEASE_PRERELEASE',
                defaultValue: overrides.GH_RELEASE_PRERELEASE ?: prev.GH_RELEASE_PRERELEASE ?: false,
                description: 'Mark as pre-release')
        ]
    }

    /**
     * Compute the next semver version from the latest git tag and store it
     * in ctx.version and env.BUILD_VERSION.
     */
    def computeVersion(Map params, Map ctx) {
        def bump = params.GH_VERSION_BUMP ?: 'patch'

        def latestTag = steps.bat(
            label: 'Get latest git tag',
            script: '@git describe --tags --abbrev=0 2>nul || echo none',
            returnStdout: true
        ).trim().split('\n').last().trim()

        def newVersion
        if (!latestTag || latestTag == 'none') {
            steps.echo 'No existing tags found, starting at v0.1.0'
            newVersion = 'v0.1.0'
        } else {
            newVersion = bumpVersion(latestTag, bump)
        }

        steps.echo "Version: ${latestTag} -> ${newVersion}"
        ctx.version = newVersion
        steps.env.BUILD_VERSION = newVersion
    }

    /**
     * Create a GitHub Release with a generated changelog and upload assets.
     */
    def createRelease(Map params, Map ctx) {
        def version = ctx.version
        if (!version) {
            steps.error('github.release() requires github.version() to run first (ctx.version is not set)')
        }

        def credentialsId = params.GH_CREDENTIALS_ID
        if (!credentialsId) {
            steps.error('GH_CREDENTIALS_ID is required for github.release()')
        }

        def changelog = generateChangelog()
        def releaseName = (params.GH_RELEASE_NAME ?: 'Release ${VERSION}').replace('${VERSION}', version)
        def assetsPattern = params.GH_RELEASE_ASSETS ?: '*.zip'
        def draft = params.GH_RELEASE_DRAFT ?: false
        def prerelease = params.GH_RELEASE_PRERELEASE ?: false

        release(
            credentialsId: credentialsId,
            version:       version,
            name:          releaseName,
            body:          changelog,
            assets:        assetsPattern,
            draft:         draft,
            prerelease:    prerelease
        )
    }

    def release(Map config) {
        def credentialsId = config.credentialsId
        def version       = config.version
        def name          = config.name ?: "Release ${version}"
        def body          = config.body ?: ''
        def assets        = config.assets ?: ''
        def draft         = config.draft ?: false
        def prerelease    = config.prerelease ?: false

        steps.withCredentials([steps.usernamePassword(credentialsId: credentialsId, passwordVariable: 'GH_TOKEN', usernameVariable: 'GH_USER')]) {
            def cmd = "gh release create \"${version}\" --title \"${name}\""

            if (body) {
                steps.writeFile(file: 'release_notes.md', text: body)
                cmd += ' --notes-file release_notes.md'
            } else {
                cmd += ' --generate-notes'
            }

            if (draft)      cmd += ' --draft'
            if (prerelease) cmd += ' --prerelease'

            if (assets) {
                cmd += " ${assets}"
            }

            steps.bat(label: "Create GitHub Release ${version}", script: cmd)
        }
    }

    /**
     * Generate a changelog from conventional commits since the last tag.
     */
    String generateChangelog() {
        def latestTag = steps.bat(
            label: 'Get latest tag for changelog',
            script: '@git describe --tags --abbrev=0 2>nul || echo none',
            returnStdout: true
        ).trim().split('\n').last().trim()

        def logCmd
        if (!latestTag || latestTag == 'none') {
            logCmd = '@git log --pretty=format:"- %%s" HEAD'
        } else {
            logCmd = "@git log --pretty=format:\"- %%s\" ${latestTag}..HEAD"
        }

        def rawLog = steps.bat(
            label: 'Generate changelog',
            script: logCmd,
            returnStdout: true
        ).trim()

        // Filter to conventional commits
        def changes = rawLog.split('\n')
            .findAll { it =~ /^- (feat|fix|breaking|docs|chore|refactor|perf|test|ci|build|style):/ }
            .take(20)
            .join('\n')

        if (!changes) {
            changes = '- New release'
        }

        return "## What's Changed\n${changes}"
    }

    private static String bumpVersion(String tag, String bump) {
        def versionNumber = tag.replaceFirst(/^v/, '')
        def parts = versionNumber.tokenize('.')
        def major = parts[0] as int
        def minor = parts.size() > 1 ? parts[1] as int : 0
        def patch = parts.size() > 2 ? parts[2] as int : 0

        switch (bump) {
            case 'major':
                major++; minor = 0; patch = 0
                break
            case 'minor':
                minor++; patch = 0
                break
            default:
                patch++
                break
        }

        return "v${major}.${minor}.${patch}"
    }

    private static List<String> reorderChoices(List<String> choices, String previous) {
        if (!previous || !choices.contains(previous)) return choices
        return [previous] + choices.findAll { it != previous }
    }
}
