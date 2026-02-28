/**
 * Notification modules.
 * Usage: notify.discord()
 */

import com.buas.notify.Discord

private def _discordImpl = null

def discord(Map overrides = [:]) {
    _discordImpl = new Discord(this)
    stages.registerModule(
        category: 'notify',
        name: 'Discord',
        params: _discordImpl.pipelineParams(overrides),
        notify: { status, params, ctx -> _discordImpl.executeNotify(status, params, ctx) },
        hasCleanup: false
    )
}

// Discord direct-use methods
def send(Map buildInfo, String webhook) { _discordImpl.send(buildInfo, webhook) }
def sendTestReport(Map reportInfo, String webhook) { _discordImpl.sendTestReport(reportInfo, webhook) }
def sendReviewNotification(Map reviewInfo, String webhook) { _discordImpl.sendReviewNotification(reviewInfo, webhook) }
def sendCustom(String title, String messageColor, List fields, String webhook, Map footer = null, String content = null) {
    _discordImpl.sendCustom(title, messageColor, fields, webhook, footer, content)
}
def createGroup(String groupName, String groupDiscordID, groupSwarmID, String groupType, List groupsList) {
    _discordImpl.createGroup(groupName, groupDiscordID, groupSwarmID, groupType, groupsList)
}
def mentionGroup(String groupName, String groups) { return _discordImpl.mentionGroup(groupName, groups) }
def mentionGroups(List groupNames, String groups) { return _discordImpl.mentionGroups(groupNames, groups) }
def swarmIDtoDiscordID(String swarmID, String groups) { return _discordImpl.swarmIDtoDiscordID(swarmID, groups) }
