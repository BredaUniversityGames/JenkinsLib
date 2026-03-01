package com.buas.vcs

/**
 * Perforce VCS module.
 */
class Perforce implements Serializable {
    def steps

    Perforce(steps) {
        this.steps = steps
    }

    def pipelineParams(Map overrides = [:]) {
        def prev = steps.params ?: [:]
        return [
            steps.string(name: 'P4_CREDENTIAL', defaultValue: overrides.P4_CREDENTIAL ?: prev.P4_CREDENTIAL ?: '',
                   description: 'Jenkins credentials ID for Perforce'),
            steps.string(name: 'P4_HOST', defaultValue: overrides.P4_HOST ?: prev.P4_HOST ?: 'ssl:perforce.buas.nl:1666',
                   description: 'Perforce server host'),
            steps.string(name: 'P4_WORKSPACE', defaultValue: overrides.P4_WORKSPACE ?: prev.P4_WORKSPACE ?: "jenkins-${steps.env.JOB_NAME.replace('/', '-')}",
                   description: 'Perforce workspace name (used as template if P4_VIEW is empty)'),
            steps.string(name: 'P4_VIEW', defaultValue: overrides.P4_VIEW ?: prev.P4_VIEW ?: '',
                   description: 'Workspace view mapping (e.g. //depot/project/... //${P4_WORKSPACE}/...) — if set, overrides the view in the P4_WORKSPACE template'),
            steps.booleanParam(name: 'P4_FORCE_CLEAN', defaultValue: overrides.P4_FORCE_CLEAN ?: prev.P4_FORCE_CLEAN ?: false,
                         description: 'Force clean Perforce sync')
        ]
    }

    def execute(Map params, Map ctx) {
        if (!params.P4_CREDENTIAL?.trim()) {
            steps.error("P4_CREDENTIAL is required — set it in Build with Parameters")
        }

        checkout(
            credential:     params.P4_CREDENTIAL,
            host:           params.P4_HOST,
            workspace:      params.P4_WORKSPACE,
            view:           params.P4_VIEW,
            forceClean:     params.P4_FORCE_CLEAN
        )
        ctx.vcsType = 'Perforce'
        ctx.revision = steps.env.P4_CHANGELIST ?: 'unknown'
        ctx.changelist = steps.env.P4_CHANGELIST ?: ''
    }

    def executeCleanup(Map params, Map ctx) {
        if (params.P4_CREDENTIAL) {
            cleanup(
                credential: params.P4_CREDENTIAL,
                workspace:  params.P4_WORKSPACE,
                view:       params.P4_VIEW
            )
        }
    }

    def checkout(Map config) {
        def credential = config.credential
        def workspace = config.workspace
        def view = config.view
        def forceClean = config.forceClean ?: false
        def version = config.version ?: ''
        def hasView = view?.trim()

        def populate = forceClean
            ? steps.forceClean(have: false, parallel: [enable: true, minbytes: '1024', minfiles: '1', threads: '4'], pin: version, quiet: true)
            : steps.autoClean(delete: false, modtime: false, parallel: [enable: false, minbytes: '1024', minfiles: '1', threads: '4'], pin: version, quiet: true, replace: true, tidy: false)

        if (hasView) {
            steps.p4sync charset: 'none', credential: credential, format: workspace,
                   populate: populate,
                   workspace: steps.manualSpec(charset: 'none', cleanup: false, name: workspace, pinHost: false,
                              spec: steps.clientSpec(allwrite: false, backup: true, changeView: '', clobber: true, compress: false,
                                               line: 'LOCAL', locked: false, modtime: false, rmdir: false, serverID: '',
                                               streamName: '', type: 'WRITABLE', view: view))
        } else {
            steps.p4sync charset: 'none', credential: credential, format: workspace,
                   populate: populate,
                   source: templateSource(workspace)
        }
    }

    def getLatestChangelist(Map config) {
        def credential = config.credential

        def p4s = steps.p4(credential: credential,
                     workspace: steps.manualSpec(charset: 'none', cleanup: false, name: 'Jenkins-${NODE_NAME}', pinHost: false,
                                spec: steps.clientSpec(allwrite: true, backup: true, changeView: '', clobber: true, compress: false,
                                                 line: 'LOCAL', locked: false, modtime: false, rmdir: false, serverID: '',
                                                 streamName: '', type: 'WRITABLE', view: '')))
        def changes = p4s.run('changes', '-s', 'submitted', '-m1')
        def change = ""
        for (def item : changes) {
            for (String key : item.keySet()) {
                if (key == "change") {
                    change = item.get(key)
                }
            }
        }
        return change
    }

    def createTicket(Map config) {
        def credential = config.credential
        def host = config.host
        def ticket = ""

        steps.withCredentials([steps.usernamePassword(credentialsId: credential, passwordVariable: 'P4PASS', usernameVariable: 'P4USER')]) {
            steps.bat(label: "Trust connection", script: "echo %P4PASS%| p4 -p ${host} -u %P4USER% trust -y")
            def result = steps.bat(label: "Create P4 ticket", script: "echo %P4PASS%| p4 -p ${host} -u %P4USER% login -ap", returnStdout: true)
            ticket = result.tokenize().last()
        }

        return ticket
    }

    def unshelve(Map config) {
        def credential = config.credential
        def workspace = config.workspace
        def view = config.view
        def shelfId = config.shelfId
        def hasView = view?.trim()

        def wsSpec = hasView
            ? steps.manualSpec(charset: 'none', cleanup: false, name: workspace, pinHost: false,
                  spec: steps.clientSpec(allwrite: false, backup: true, changeView: '', clobber: true, compress: false,
                                   line: 'LOCAL', locked: false, modtime: false, rmdir: false, serverID: '',
                                   streamName: '', type: 'WRITABLE', view: view))
            : steps.staticSpec(charset: 'none', name: workspace, pinHost: false)

        steps.p4unshelve credential: credential, ignoreEmpty: false, resolve: 'none', shelf: shelfId, tidy: false,
                   workspace: wsSpec
    }

    def getChangelistDescription(Map config) {
        def credential = config.credential
        def workspace = config.workspace
        def view = config.view
        def changelistId = config.changelistId
        def hasView = view?.trim()

        def wsSpec = hasView
            ? steps.manualSpec(charset: 'none', cleanup: false, name: workspace, pinHost: false,
                  spec: steps.clientSpec(allwrite: true, backup: true, changeView: '', clobber: false, compress: false,
                                   line: 'LOCAL', locked: false, modtime: false, rmdir: false, serverID: '',
                                   streamName: '', type: 'WRITABLE', view: view))
            : steps.staticSpec(charset: 'none', name: workspace, pinHost: false)

        def p4s = steps.p4(credential: credential, workspace: wsSpec)
        def changeList = p4s.run('describe', '-s', '-S', "${changelistId}")
        def desc = ""

        for (def item : changeList) {
            for (String key : item.keySet()) {
                if (key == "desc") {
                    desc = item.get(key)
                }
            }
        }

        return desc
    }

    def publish(Map config) {
        def credential = config.credential
        def workspace = config.workspace

        steps.p4publish credential: credential,
                  publish: steps.submit(delete: false, description: 'Submitted by Jenkins. Build: ${BUILD_TAG}', modtime: false,
                                  onlyOnSuccess: true, paths: '', purge: '', reopen: false),
                  workspace: steps.staticSpec(charset: 'none', name: workspace, pinHost: false)
    }

    def cleanup(Map config) {
        def credential = config.credential
        def workspace = config.workspace
        def view = config.view
        def hasView = view?.trim()

        def wsSpec = hasView
            ? steps.manualSpec(charset: 'none', cleanup: false, name: workspace, pinHost: false,
                  spec: steps.clientSpec(allwrite: true, backup: true, changeView: '', clobber: false, compress: false,
                                   line: 'LOCAL', locked: false, modtime: false, rmdir: false, serverID: '',
                                   streamName: '', type: 'WRITABLE', view: view))
            : steps.staticSpec(charset: 'none', name: workspace, pinHost: false)

        def p4s = steps.p4(credential: credential, workspace: wsSpec)
        p4s.run('revert', '-c', 'default', '//...')
    }

    private def templateSource(String workspace) {
        return steps.templateSource(workspace)
    }
}
