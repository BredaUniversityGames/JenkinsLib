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
        def defaultBranch = overrides.GIT_BRANCH ?: prev.GIT_BRANCH ?: 'main'
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
            return [defaultBranch]
        }
        try {
            def headOutput = ''
            def branchOutput = ''
            steps.node('Windows') {
                steps.withEnv(NO_PROMPT_ENV) {
                    if (credentialsId) {
                        steps.withCredentials([steps.usernamePassword(
                                credentialsId: credentialsId,
                                usernameVariable: 'GIT_USER',
                                passwordVariable: 'GIT_PASS')]) {
                            def authedUrl = repoUrl.replaceFirst('https://', 'https://%GIT_USER%:%GIT_PASS%@')
                            headOutput = steps.bat(script: "@git ls-remote --symref ${authedUrl} HEAD", returnStdout: true)
                            branchOutput = steps.bat(script: "@git ls-remote --heads ${authedUrl}", returnStdout: true)
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
            // Move the remote default branch to the top if present
            if (branches.contains(remoteDefault)) {
                branches.remove(remoteDefault)
                branches.add(0, remoteDefault)
            }
            return branches
        } catch (Exception e) {
            steps.echo "Warning: could not list branches for ${repoUrl}: ${e.message}"
            return [defaultBranch]
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
}
