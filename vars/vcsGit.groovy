/**
 * Git/GitHub VCS module.
 * Uses Jenkins Git plugin steps.
 */

// ── Module registration ──

def call(Map overrides = [:]) {
    buasPipeline.registerModule(
        category: 'vcs',
        name: 'Source Control',
        params: pipelineParams(overrides),
        ref: this,
        cleanup: false
    )
}

def pipelineParams(Map overrides = [:]) {
    return [
        string(name: 'GIT_REPO_URL', defaultValue: overrides.GIT_REPO_URL ?: '',
               description: 'Git repository URL'),
        string(name: 'GIT_BRANCH', defaultValue: overrides.GIT_BRANCH ?: 'main',
               description: 'Git branch to build'),
        string(name: 'GIT_CREDENTIALS_ID', defaultValue: overrides.GIT_CREDENTIALS_ID ?: '',
               description: 'Jenkins credentials ID for Git auth (leave empty for public repos)')
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

// ── Direct-use methods ──

def checkout(Map config) {
    def url = config.url
    def branch = config.branch ?: 'main'
    def credentialsId = config.credentialsId ?: ''

    if (credentialsId) {
        git url: url, branch: branch, credentialsId: credentialsId
    } else {
        git url: url, branch: branch
    }
}

def getCommitHash() {
    return bat(script: '@git rev-parse HEAD', returnStdout: true).trim().split('\n').last().trim()
}

def getCommitMessage() {
    return bat(script: '@git log -1 --pretty=format:%%s', returnStdout: true).trim().split('\n').last().trim()
}

def cleanup() {
    // No-op for Git - workspace cleanup handles it
}
