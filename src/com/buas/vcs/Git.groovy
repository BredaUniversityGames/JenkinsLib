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
        return [
            steps.string(name: 'GIT_REPO_URL', defaultValue: overrides.GIT_REPO_URL ?: prev.GIT_REPO_URL ?: '',
                   description: 'Git repository URL'),
            steps.string(name: 'GIT_BRANCH', defaultValue: overrides.GIT_BRANCH ?: prev.GIT_BRANCH ?: 'main',
                   description: 'Git branch to build'),
            steps.credentials(name: 'GIT_CREDENTIALS_ID', defaultValue: overrides.GIT_CREDENTIALS_ID ?: prev.GIT_CREDENTIALS_ID ?: '',
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

    def checkout(Map config) {
        def url = config.url
        def branch = config.branch ?: 'main'
        def credentialsId = config.credentialsId ?: ''

        if (credentialsId) {
            steps.git url: url, branch: branch, credentialsId: credentialsId
        } else {
            steps.git url: url, branch: branch
        }
    }

    def getCommitHash() {
        return steps.bat(script: '@git rev-parse HEAD', returnStdout: true).trim().split('\n').last().trim()
    }

    def getCommitMessage() {
        return steps.bat(script: '@git log -1 --pretty=format:%%s', returnStdout: true).trim().split('\n').last().trim()
    }
}
