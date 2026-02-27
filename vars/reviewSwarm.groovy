import groovy.json.JsonSlurper

/**
 * Helix Swarm code review integration via REST API (v9).
 * Stateless - all configuration passed via Map parameters.
 */

def createReview(Map config) {
    def user = config.user
    def ticket = config.ticket
    def swarmUrl = config.swarmUrl
    def changelistId = config.changelistId
    def participants = config.participants

    def reviewers = ""
    if (participants) {
        participants.each {
            reviewers = reviewers + "-d \"reviewers[]=${it} \""
        }
    }

    def output = bat(script: "curl -u \"${user}:${ticket}\" -X POST -d \"change=${changelistId}\" \"${reviewers}\" \"${swarmUrl}/api/v9/reviews/\"",
                     returnStdout: true)
    def responseArray = output.split('\\n')
    return responseArray[2].trim()
}

def getReviewID(String curlResponse) {
    def reviewInfo = new JsonSlurper().parseText(curlResponse)
    return reviewInfo.review.id
}

def getReviewAuthor(String curlResponse) {
    def reviewInfo = new JsonSlurper().parseText(curlResponse)
    return reviewInfo.review.author
}

def upVote(Map config) {
    def user = config.user
    def ticket = config.ticket
    def swarmUrl = config.swarmUrl
    def reviewId = config.reviewId

    bat(label: "Upvote Swarm review",
        script: "curl -u \"${user}:${ticket}\" -X POST \"${swarmUrl}/reviews/${reviewId}/vote/up\"")
}

def downVote(Map config) {
    def user = config.user
    def ticket = config.ticket
    def swarmUrl = config.swarmUrl
    def reviewId = config.reviewId

    bat(label: "Downvote Swarm review",
        script: "curl -u \"${user}:${ticket}\" -X POST \"${swarmUrl}/reviews/${reviewId}/vote/down\"")
}

def comment(Map config) {
    def user = config.user
    def ticket = config.ticket
    def swarmUrl = config.swarmUrl
    def reviewId = config.reviewId
    def commentText = config.comment

    bat(label: "Comment on Swarm review",
        script: "curl -u \"${user}:${ticket}\" -X POST -d \"topic=reviews/${reviewId}&body=${commentText}\" \"${swarmUrl}/api/v9/comments/\"")
}

def setState(Map config) {
    def user = config.user
    def ticket = config.ticket
    def swarmUrl = config.swarmUrl
    def reviewId = config.reviewId
    def state = config.state

    bat(label: "Set Swarm review state to ${state}",
        script: "curl -u \"${user}:${ticket}\" -X PATCH -d \"state=${state}\" \"${swarmUrl}/api/v9/reviews/${reviewId}/state/\"")
}

def needsReview(Map config) {
    config.state = "needsReview"
    setState(config)
}

def needsRevision(Map config) {
    config.state = "needsRevision"
    setState(config)
}

def approve(Map config) {
    config.state = "approved"
    setState(config)
}

def archive(Map config) {
    config.state = "archived"
    setState(config)
}

def reject(Map config) {
    config.state = "rejected"
    setState(config)
}

// Group participant helpers

def getParticipantsOfGroup(String groupName, String groups) {
    def participants = []
    def groupsParsed = new JsonSlurper().parseText(groups)

    groupsParsed.groups.each { group ->
        if (group.name == groupName) {
            participants = group.swarmID
        }
    }

    return participants
}

def getParticipantsOfGroups(List groupNames, String groups) {
    def participantsArray = []

    groupNames.each {
        def participants = getParticipantsOfGroup(it, groups)
        participants.each {
            participantsArray.add(it)
        }
    }

    return participantsArray
}
