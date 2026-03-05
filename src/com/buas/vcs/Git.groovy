package com.buas.vcs

/**
 * Git/GitHub VCS module.
 * Uses Jenkins Git plugin steps.
 */
class Git implements Serializable {
    def steps

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
            def output = ''
            steps.node('Windows') {
                if (credentialsId) {
                    steps.withCredentials([steps.usernamePassword(
                            credentialsId: credentialsId,
                            usernameVariable: 'GIT_USER',
                            passwordVariable: 'GIT_PASS')]) {
                        output = steps.bat(script: "@git ls-remote --heads ${repoUrl}", returnStdout: true)
                    }
                } else {
                    steps.withEnv(['GIT_TERMINAL_PROMPT=0', 'GIT_ASKPASS=']) {
                        output = steps.bat(script: "@git ls-remote --heads ${repoUrl}", returnStdout: true)
                    }
                }
            }
            def branches = output.trim().readLines()
                .collect { it.replaceAll(/.*refs\/heads\//, '') }
                .findAll { it }
                .sort()
            if (!branches) {
                return [defaultBranch]
            }
            // Move the default branch to the top if present
            if (branches.contains(defaultBranch)) {
                branches.remove(defaultBranch)
                branches.add(0, defaultBranch)
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

        def userRemoteConfigs = [url: url]
        if (credentialsId) {
            userRemoteConfigs.credentialsId = credentialsId
        }
        steps.checkout([
            $class: 'GitSCM',
            branches: [[name: "*/${branch}"]],
            userRemoteConfigs: [userRemoteConfigs]
        ])
    }

    def getCommitHash() {
        return steps.bat(script: '@git rev-parse HEAD', returnStdout: true).trim().split('\n').last().trim()
    }

    def getCommitMessage() {
        return steps.bat(script: '@git log -1 --pretty=format:%%s', returnStdout: true).trim().split('\n').last().trim()
    }
}
