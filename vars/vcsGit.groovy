/**
 * Git/GitHub VCS module.
 * Uses Jenkins Git plugin steps.
 */

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
