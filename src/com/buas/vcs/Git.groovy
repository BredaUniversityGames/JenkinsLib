package com.buas.vcs

/**
 * Git/GitHub VCS module.
 * Uses Jenkins Git plugin steps.
 */
class Git implements Serializable {
    def steps
    private static final NO_PROMPT_ENV = ['GIT_TERMINAL_PROMPT=0', 'GIT_ASKPASS=']

    Git(steps) {
        this.steps = steps
    }

    def pipelineParams(Map overrides = [:]) {
        def prev = steps.params ?: [:]
        def repoUrl = overrides.GIT_REPO_URL ?: prev.GIT_REPO_URL ?: ''
        def credId = overrides.GIT_CREDENTIALS_ID ?: prev.GIT_CREDENTIALS_ID ?: ''
        def defaultBranch = overrides.GIT_BRANCH ?: prev.GIT_BRANCH ?: ''
        def branches = listBranches(repoUrl, credId, defaultBranch)
        return [
            steps.string(name: 'GIT_REPO_URL', defaultValue: repoUrl,
                   description: 'Git repository URL'),
            steps.choice(name: 'GIT_BRANCH', choices: branches,
                   description: 'Git branch to build'),
            steps.credentials(name: 'GIT_CREDENTIALS_ID', defaultValue: credId,
                   description: 'Git credential (leave empty for public repos)',
                   credentialType: 'com.cloudbees.plugins.credentials.common.StandardCredentials', required: false)
        ]
    }

    def execute(Map params, Map ctx) {
        checkout(
            url:           params.GIT_REPO_URL,
            branch:        params.GIT_BRANCH,
            credentialsId: params.GIT_CREDENTIALS_ID
        )
        ctx.vcsType = 'Git'
        ctx.revision = getCommitHash()
    }

    private List<String> listBranches(String repoUrl, String credentialsId, String defaultBranch) {
        if (!repoUrl) {
            return [defaultBranch ?: 'main']
        }
        try {
            def headOutput = ''
            def branchOutput = ''
            steps.node('Windows') {
                steps.withEnv(NO_PROMPT_ENV) {
                    if (credentialsId) {
                        steps.withCredentials([steps.gitUsernamePassword(
                                credentialsId: credentialsId,
                                gitToolName: 'Default')]) {
                            headOutput = steps.bat(script: "@git ls-remote --symref ${repoUrl} HEAD", returnStdout: true)
                            branchOutput = steps.bat(script: "@git ls-remote --heads ${repoUrl}", returnStdout: true)
                        }
                    } else {
                        headOutput = steps.bat(script: "@git ls-remote --symref ${repoUrl} HEAD", returnStdout: true)
                        branchOutput = steps.bat(script: "@git ls-remote --heads ${repoUrl}", returnStdout: true)
                    }
                }
            }
            // Detect the remote default branch from symref output
            def remoteDefault = defaultBranch
            def symrefMatch = (headOutput =~ /ref: refs\/heads\/(\S+)\s+HEAD/)
            if (symrefMatch) {
                remoteDefault = symrefMatch[0][1]
            }
            def branches = branchOutput.trim().readLines()
                .collect { it.replaceAll(/.*refs\/heads\//, '') }
                .findAll { it }
                .sort()
            if (!branches) {
                return [remoteDefault]
            }
            // Move the remote default branch to the top, then the previous
            // selection above it so the user's choice is preserved.
            if (branches.contains(remoteDefault)) {
                branches.remove(remoteDefault)
                branches.add(0, remoteDefault)
            }
            if (defaultBranch && defaultBranch != remoteDefault && branches.contains(defaultBranch)) {
                branches.remove(defaultBranch)
                branches.add(0, defaultBranch)
            }
            return branches
        } catch (Exception e) {
            steps.echo "Warning: could not list branches for ${repoUrl}: ${e.message}"
            return [defaultBranch ?: 'main']
        }
    }

    def checkout(Map config) {
        def url = config.url
        def branch = config.branch ?: 'main'
        def credentialsId = config.credentialsId ?: ''

        def remoteConfig = [url: url]
        if (credentialsId) {
            remoteConfig.credentialsId = credentialsId
        }
        steps.withEnv(NO_PROMPT_ENV) {
            steps.checkout steps.scmGit(
                branches: [[name: "*/${branch}"]],
                userRemoteConfigs: [remoteConfig],
                extensions: [
                    steps.submodule(depth: 1, parentCredentials: true,
                        recursiveSubmodules: true, shallow: true),
                    steps.cloneOption(shallow: true, depth: 1)
                ]
            )
        }
    }

    def getCommitHash() {
        return steps.bat(script: '@git rev-parse HEAD', returnStdout: true).trim().split('\n').last().trim()
    }

    def getCommitMessage() {
        return steps.bat(script: '@git log -1 --pretty=format:%%s', returnStdout: true).trim().split('\n').last().trim()
    }

    /**
     * Fetch a single file from the remote without a full checkout.
     * Shallow-clones into a temp directory, reads the file via git show, then cleans up.
     */
    String fetchFile(Map config) {
        def path   = config.path
        def url    = config.url
        def credId = config.credentialsId ?: ''
        def branch = config.branch ?: 'main'

        def content = ''
        steps.withEnv(NO_PROMPT_ENV) {
            def tmpDir     = "${steps.env.TEMP}\\git_fetch_${steps.env.BUILD_NUMBER}"
            def cloneCmd   = "@git clone --depth 1 --no-checkout -b ${branch} \"${url}\" \"${tmpDir}\" 2>nul"
            def showCmd    = "@cd /d \"${tmpDir}\" && git show HEAD:${path}"
            def cleanupCmd = "@cd /d \"%TEMP%\" && if exist \"${tmpDir}\" rmdir /s /q \"${tmpDir}\" 2>nul"
            try {
                if (credId) {
                    steps.withCredentials([steps.gitUsernamePassword(
                            credentialsId: credId,
                            gitToolName: 'Default')]) {
                        steps.bat(script: cloneCmd, returnStatus: true)
                        content = steps.bat(script: showCmd, returnStdout: true).trim()
                    }
                } else {
                    steps.bat(script: cloneCmd, returnStatus: true)
                    content = steps.bat(script: showCmd, returnStdout: true).trim()
                }
            } finally {
                steps.bat(script: cleanupCmd, returnStatus: true)
            }
        }
        return content
    }
}
