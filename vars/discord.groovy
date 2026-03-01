/**
 * Discord notification module.
 * Usage: discord.notify()
 *
 * Direct-use methods are available after registration:
 * discord.send(), discord.sendTestReport(), discord.sendCustom(), etc.
 */

import groovy.transform.Field
import com.buas.ModuleRegistry
import com.buas.notify.Discord

@Field def _impl = null

def notify(Map overrides = [:]) {
    _impl = new Discord(this)
    ModuleRegistry.register(
        category: 'notify',
        name: 'Discord',
        params: _impl.pipelineParams(overrides),
        notify: { status, params, ctx -> _impl.executeNotify(status, params, ctx) },
        hasCleanup: false
    )
}

// Direct-use methods
def send(Map buildInfo, String webhook) { _impl.send(buildInfo, webhook) }
def sendTestReport(Map reportInfo, String webhook) { _impl.sendTestReport(reportInfo, webhook) }
def sendReviewNotification(Map reviewInfo, String webhook) { _impl.sendReviewNotification(reviewInfo, webhook) }
def sendCustom(String title, String messageColor, List fields, String webhook, Map footer = null, String content = null) {
    _impl.sendCustom(title, messageColor, fields, webhook, footer, content)
}
def createGroup(String groupName, String groupDiscordID, groupSwarmID, String groupType, List groupsList) {
    _impl.createGroup(groupName, groupDiscordID, groupSwarmID, groupType, groupsList)
}
def mentionGroup(String groupName, String groups) { return _impl.mentionGroup(groupName, groups) }
def mentionGroups(List groupNames, String groups) { return _impl.mentionGroups(groupNames, groups) }
def swarmIDtoDiscordID(String swarmID, String groups) { return _impl.swarmIDtoDiscordID(swarmID, groups) }
