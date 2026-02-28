/**
 * Code review modules.
 * Usage: review.swarm()
 */

import groovy.transform.Field
import com.buas.ModuleRegistry
import com.buas.review.Swarm

@Field def _swarmImpl = null

def swarm(Map overrides = [:]) {
    _swarmImpl = new Swarm(this)
    ModuleRegistry.register(
        category: 'review',
        name: 'Swarm Review',
        params: _swarmImpl.pipelineParams(overrides),
        execute: { params, ctx -> _swarmImpl.execute(params, ctx) },
        hasCleanup: false
    )
}

// Swarm direct-use methods
def createReview(Map config) { return _swarmImpl.createReview(config) }
def getReviewID(String curlResponse) { return _swarmImpl.getReviewID(curlResponse) }
def getReviewAuthor(String curlResponse) { return _swarmImpl.getReviewAuthor(curlResponse) }
def upVote(Map config) { _swarmImpl.upVote(config) }
def downVote(Map config) { _swarmImpl.downVote(config) }
def comment(Map config) { _swarmImpl.comment(config) }
def setState(Map config) { _swarmImpl.setState(config) }
def needsReview(Map config) { _swarmImpl.needsReview(config) }
def needsRevision(Map config) { _swarmImpl.needsRevision(config) }
def approve(Map config) { _swarmImpl.approve(config) }
def archive(Map config) { _swarmImpl.archive(config) }
def reject(Map config) { _swarmImpl.reject(config) }
def getParticipantsOfGroup(String groupName, String groups) { return _swarmImpl.getParticipantsOfGroup(groupName, groups) }
def getParticipantsOfGroups(List groupNames, String groups) { return _swarmImpl.getParticipantsOfGroups(groupNames, groups) }
