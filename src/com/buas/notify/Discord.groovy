package com.buas.notify

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * Discord notification channel.
 * Implements notification interface: send, sendTestReport, sendReviewNotification.
 */
class Discord implements Serializable {
    def steps

    Discord(steps) {
        this.steps = steps
    }

    def pipelineParams(Map overrides = [:]) {
        return [
            steps.string(name: 'DISCORD_WEBHOOK', defaultValue: overrides.DISCORD_WEBHOOK ?: '',
                   description: 'Discord webhook URL')
        ]
    }

    def executeNotify(String status, Map params, Map ctx) {
        if (!params.DISCORD_WEBHOOK) return

        def buildInfo = [
            status:   status,
            jobName:  steps.env.JOB_BASE_NAME,
            buildNum: steps.env.BUILD_NUMBER,
            buildUrl: steps.env.BUILD_URL,
            config:   ctx.buildConfig ?: '',
            platform: ctx.buildPlatform ?: '',
            vcsType:  ctx.vcsType ?: '',
            revision: ctx.revision ?: 'unknown'
        ]

        send(buildInfo, params.DISCORD_WEBHOOK)

        if (ctx.testResults) {
            def testResults = new JsonSlurper().parseText(ctx.testResults)
            def reportInfo = [
                succeeded: testResults.succeeded ?: 0,
                failed:    testResults.failed ?: 0,
                warnings:  testResults.succeededWithWarnings ?: 0,
                total:     (testResults.succeeded ?: 0) + (testResults.failed ?: 0) + (testResults.succeededWithWarnings ?: 0),
                reportUrl: "${steps.env.BUILD_URL}testReport/",
                jobName:   steps.env.JOB_BASE_NAME,
                buildNum:  steps.env.BUILD_NUMBER
            ]
            sendTestReport(reportInfo, params.DISCORD_WEBHOOK)
        }

        if (ctx.reviewId) {
            def reviewInfo = [
                reviewId:    ctx.reviewId,
                author:      ctx.reviewAuthor ?: '',
                swarmUrl:    ctx.swarmUrl ?: '',
                buildStatus: status,
                jobName:     steps.env.JOB_BASE_NAME,
                buildNum:    steps.env.BUILD_NUMBER
            ]
            sendReviewNotification(reviewInfo, params.DISCORD_WEBHOOK)
        }
    }

    def send(Map buildInfo, String webhook) {
        def colorMap = [SUCCESS: 65280, UNSTABLE: 16776960, FAILURE: 16711680, ABORTED: 16711680]
        def emojiMap = [SUCCESS: ':white_check_mark:', UNSTABLE: ':warning:', FAILURE: ':x:', ABORTED: ':stop_sign:']
        def labelMap = [SUCCESS: 'SUCCEEDED', UNSTABLE: 'UNSTABLE', FAILURE: 'FAILED', ABORTED: 'ABORTED']

        def status = buildInfo.status
        def emoji = emojiMap[status] ?: ''
        def color = colorMap[status] ?: 0
        def label = labelMap[status] ?: status

        def fields = [
            [name: "${buildInfo.config} (${buildInfo.platform}) ${buildInfo.jobName} has ${label.toLowerCase()}",
             value: "Revision: ${buildInfo.revision}"],
            [name: "Job URL",
             value: "${buildInfo.buildUrl}"]
        ]

        def body = [embeds: [[
            title: "${emoji} BUILD ${label} ${emoji}",
            color: color,
            fields: fields,
            footer: [text: "${buildInfo.jobName} (#${buildInfo.buildNum})"]
        ]]]

        sendRaw(body, webhook)
    }

    def sendTestReport(Map reportInfo, String webhook) {
        def total = reportInfo.total ?: 0
        def succeeded = reportInfo.succeeded ?: 0
        def warnings = reportInfo.warnings ?: 0
        def failed = reportInfo.failed ?: 0

        def fields = [
            [name: "A new test report is ready",
             value: "${reportInfo.reportUrl}"],
            [name: ":white_check_mark: Succeeded",
             value: "${succeeded}/${total}"],
            [name: ":warning: Succeeded with warnings",
             value: "${warnings}/${total}"],
            [name: ":x: Failed",
             value: "${failed}/${total}"]
        ]

        def body = [embeds: [[
            title: ":clipboard: NEW TEST REPORT :clipboard:",
            color: 16776960,
            fields: fields,
            footer: [text: "${reportInfo.jobName} (#${reportInfo.buildNum})"]
        ]]]

        sendRaw(body, webhook)
    }

    def sendReviewNotification(Map reviewInfo, String webhook) {
        def fields = [
            [name: "A new review is ready",
             value: "${reviewInfo.swarmUrl}/reviews/${reviewInfo.reviewId}"],
            [name: "Author",
             value: "${reviewInfo.author}"],
            [name: "Participants",
             value: "${reviewInfo.participants}"],
            [name: "Build status",
             value: "Build ${reviewInfo.buildStatus ?: 'not built'}"]
        ]

        def body = [embeds: [[
            title: ":warning: NEW REVIEW :warning:",
            color: 16776960,
            fields: fields,
            footer: [text: "${reviewInfo.jobName} (#${reviewInfo.buildNum})"]
        ]]]

        if (reviewInfo.participants) {
            body.content = "${reviewInfo.participants}"
        }

        sendRaw(body, webhook)
    }

    def sendCustom(String title, String messageColor, List fields, String webhook, Map footer = null, String content = null) {
        def colorMap = [green: 65280, yellow: 16776960, red: 16711680]
        def color = colorMap[messageColor] ?: 0

        def body = [embeds: [[
            title: title,
            color: color,
            fields: fields
        ]]]

        if (footer) {
            body.embeds[0].footer = footer
        }
        if (content) {
            body.content = content
        }

        sendRaw(body, webhook)
    }

    private def sendRaw(Map body, String webhook) {
        def json = JsonOutput.toJson(body).replace('"', '""')
        steps.bat(label: "Send Discord notification", script: "curl -X POST -H \"Content-Type: application/json\" -d \"${json}\" ${webhook}")
    }

    // Group management helpers (for Swarm integration)

    def createGroup(String groupName, String groupDiscordID, groupSwarmID, String groupType, List groupsList) {
        def group = [
            name: groupName,
            discordID: groupDiscordID,
            swarmID: groupSwarmID,
            type: groupType
        ]
        groupsList.add(JsonOutput.toJson(group))
    }

    def mentionGroup(String groupName, String groups) {
        def groupsParsed = new JsonSlurper().parseText(groups)
        def groupType = ""
        def discordID = ""

        groupsParsed.groups.each { group ->
            if (group.name == groupName) {
                groupType = group.type
                discordID = group.discordID
            }
        }

        def prefix = (groupType == 'role') ? '<@&' : (groupType == 'channel') ? '<#' : '<@'
        def message = "${prefix}${discordID}>"
        message = message.replace(",", " ")
        return "${groupName}: ${message}"
    }

    def mentionGroups(List groupNames, String groups) {
        def message = ""
        groupNames.each {
            message = message + mentionGroup(it, groups) + "\n"
        }
        return message
    }

    def swarmIDtoDiscordID(String swarmID, String groups) {
        def swarmName = swarmID.replaceAll("\\d", "")
        def discordID = ""
        def groupsParsed = new JsonSlurper().parseText(groups)

        groupsParsed.groups.each { group ->
            if (group.name == swarmName) {
                discordID = group.discordID
            }
        }

        return discordID
    }
}
