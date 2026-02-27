/**
 * Notification dispatcher.
 * Routes build status events to all enabled notification channels.
 *
 * To add a new channel (e.g., Slack):
 * 1. Create vars/notifySlack.groovy with send(Map buildInfo, String destination)
 * 2. Add NOTIFY_SLACK boolean + SLACK_WEBHOOK string params to buasPipeline.groovy
 * 3. Add a conditional block in the methods below
 */

def send(String status, Map params, Map extra = [:]) {
    def buildInfo = [
        status:   status,
        jobName:  env.JOB_BASE_NAME,
        buildNum: env.BUILD_NUMBER,
        buildUrl: env.BUILD_URL,
        config:   params.BUILD_CONFIG ?: params.VS_CONFIG ?: '',
        platform: params.BUILD_PLATFORM ?: params.VS_PLATFORM ?: '',
        vcsType:  params.VCS_TYPE,
        revision: params.VCS_TYPE == 'Perforce' ? (env.P4_CHANGELIST ?: 'unknown') : (env.GIT_COMMIT ?: 'unknown')
    ]
    buildInfo.putAll(extra)

    // Discord
    if (params.NOTIFY_DISCORD && params.DISCORD_WEBHOOK) {
        notifyDiscord.send(buildInfo, params.DISCORD_WEBHOOK)
    }

    // Future: Slack
    // if (params.NOTIFY_SLACK && params.SLACK_WEBHOOK) {
    //     notifySlack.send(buildInfo, params.SLACK_WEBHOOK)
    // }

    // Future: Email
    // if (params.NOTIFY_EMAIL && params.EMAIL_RECIPIENTS) {
    //     notifyEmail.send(buildInfo, params.EMAIL_RECIPIENTS)
    // }

    // Future: Teams
    // if (params.NOTIFY_TEAMS && params.TEAMS_WEBHOOK) {
    //     notifyTeams.send(buildInfo, params.TEAMS_WEBHOOK)
    // }
}

def sendTestReport(String jsonResults, Map params) {
    def testResults = new groovy.json.JsonSlurper().parseText(jsonResults)

    def reportInfo = [
        succeeded: testResults.succeeded ?: 0,
        failed:    testResults.failed ?: 0,
        warnings:  testResults.succeededWithWarnings ?: 0,
        total:     (testResults.succeeded ?: 0) + (testResults.failed ?: 0) + (testResults.succeededWithWarnings ?: 0),
        reportUrl: "${env.BUILD_URL}testReport/",
        jobName:   env.JOB_BASE_NAME,
        buildNum:  env.BUILD_NUMBER
    ]

    // Discord
    if (params.NOTIFY_DISCORD && params.DISCORD_WEBHOOK) {
        notifyDiscord.sendTestReport(reportInfo, params.DISCORD_WEBHOOK)
    }
}

def sendReviewNotification(Map reviewInfo, Map params) {
    reviewInfo.jobName = env.JOB_BASE_NAME
    reviewInfo.buildNum = env.BUILD_NUMBER

    // Discord
    if (params.NOTIFY_DISCORD && params.DISCORD_WEBHOOK) {
        notifyDiscord.sendReviewNotification(reviewInfo, params.DISCORD_WEBHOOK)
    }
}
