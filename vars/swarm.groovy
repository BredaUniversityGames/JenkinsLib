/**
 * Helix Swarm code review module.
 * Usage: swarm.review()
 *
 * Direct-use methods are available after registration:
 * swarm.createReview(), swarm.upVote(), swarm.comment(), etc.
 */

import groovy.transform.Field
import com.buas.ModuleRegistry
import com.buas.review.Swarm

@Field def _impl = null

def review(Map overrides = [:]) {
    _impl = new Swarm(this)
    ModuleRegistry.register(
        category: 'review',
        name: 'Swarm Review',
        params: _impl.pipelineParams(overrides),
        execute: { params, ctx -> _impl.execute(params, ctx) },
        hasCleanup: false
    )
}

// Direct-use methods
def createReview(Map config) { return _impl.createReview(config) }
def getReviewID(String curlResponse) { return _impl.getReviewID(curlResponse) }
def getReviewAuthor(String curlResponse) { return _impl.getReviewAuthor(curlResponse) }
def upVote(Map config) { _impl.upVote(config) }
def downVote(Map config) { _impl.downVote(config) }
def comment(Map config) { _impl.comment(config) }
def setState(Map config) { _impl.setState(config) }
def needsReview(Map config) { _impl.needsReview(config) }
def needsRevision(Map config) { _impl.needsRevision(config) }
def approve(Map config) { _impl.approve(config) }
def archive(Map config) { _impl.archive(config) }
def reject(Map config) { _impl.reject(config) }
def getParticipantsOfGroup(String groupName, String groups) { return _impl.getParticipantsOfGroup(groupName, groups) }
def getParticipantsOfGroups(List groupNames, String groups) { return _impl.getParticipantsOfGroups(groupNames, groups) }
