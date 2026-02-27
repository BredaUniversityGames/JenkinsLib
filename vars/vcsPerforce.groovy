/**
 * Perforce VCS module.
 * Stateless - all configuration passed via Map parameters.
 */

// ── Module registration ──

def call(Map overrides = [:]) {
    buasPipeline.registerModule(
        category: 'vcs',
        name: 'Source Control',
        params: pipelineParams(overrides),
        ref: this,
        cleanup: true
    )
}

def pipelineParams(Map overrides = [:]) {
    return [
        string(name: 'P4_CREDENTIAL', defaultValue: overrides.P4_CREDENTIAL ?: '',
               description: 'Jenkins credentials ID for Perforce'),
        string(name: 'P4_HOST', defaultValue: overrides.P4_HOST ?: 'ssl:perforce.buas.nl:1666',
               description: 'Perforce server host'),
        string(name: 'P4_WORKSPACE', defaultValue: overrides.P4_WORKSPACE ?: '',
               description: 'Perforce workspace template name'),
        string(name: 'P4_MAPPING', defaultValue: overrides.P4_MAPPING ?: '',
               description: 'Perforce depot view mapping (for depot source)'),
        booleanParam(name: 'P4_FORCE_CLEAN', defaultValue: overrides.P4_FORCE_CLEAN ?: false,
                     description: 'Force clean Perforce sync'),
        booleanParam(name: 'P4_USE_DEPOT_SOURCE', defaultValue: overrides.P4_USE_DEPOT_SOURCE ?: false,
                     description: 'Use depot source instead of workspace template')
    ]
}

def execute(Map params, Map ctx) {
    checkout(
        credential:     params.P4_CREDENTIAL,
        host:           params.P4_HOST,
        workspace:      params.P4_WORKSPACE,
        mapping:        params.P4_MAPPING,
        forceClean:     params.P4_FORCE_CLEAN,
        useDepotSource: params.P4_USE_DEPOT_SOURCE
    )
    ctx.vcsType = 'Perforce'
    ctx.revision = env.P4_CHANGELIST ?: 'unknown'
    ctx.changelist = env.P4_CHANGELIST ?: ''
}

def executeCleanup(Map params, Map ctx) {
    if (params.P4_CREDENTIAL) {
        cleanup(
            credential: params.P4_CREDENTIAL,
            workspace:  params.P4_WORKSPACE,
            mapping:    params.P4_MAPPING
        )
    }
}

// ── Direct-use methods ──

def checkout(Map config) {
    def credential = config.credential
    def host = config.host
    def workspace = config.workspace
    def mapping = config.mapping
    def forceClean = config.forceClean ?: false
    def version = config.version ?: ''
    def useDepotSource = config.useDepotSource ?: false

    def source = useDepotSource ? depotSource(mapping) : templateSource(workspace)
    def format = useDepotSource ? 'jenkins-${JOB_NAME}-${NODE_NAME}' : 'jenkins-${JOB_NAME}'

    if (forceClean) {
        p4sync charset: 'none', credential: credential, format: format,
               populate: forceClean(have: false, parallel: [enable: true, minbytes: '1024', minfiles: '1', threads: '4'], pin: version, quiet: true),
               source: source
    } else {
        p4sync charset: 'none', credential: credential, format: format,
               populate: autoClean(delete: false, modtime: false, parallel: [enable: false, minbytes: '1024', minfiles: '1', threads: '4'], pin: version, quiet: true, replace: true, tidy: false),
               source: source
    }
}

def getLatestChangelist(Map config) {
    def credential = config.credential

    def p4s = p4(credential: credential,
                 workspace: manualSpec(charset: 'none', cleanup: false, name: 'Jenkins-${NODE_NAME}', pinHost: false,
                            spec: clientSpec(allwrite: true, backup: true, changeView: '', clobber: true, compress: false,
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

    withCredentials([usernamePassword(credentialsId: credential, passwordVariable: 'P4PASS', usernameVariable: 'P4USER')]) {
        bat(label: "Trust connection", script: "echo %P4PASS%| p4 -p ${host} -u %P4USER% trust -y")
        def result = bat(label: "Create P4 ticket", script: "echo %P4PASS%| p4 -p ${host} -u %P4USER% login -ap", returnStdout: true)
        ticket = result.tokenize().last()
    }

    return ticket
}

def unshelve(Map config) {
    def credential = config.credential
    def workspace = config.workspace
    def mapping = config.mapping
    def shelfId = config.shelfId

    p4unshelve credential: credential, ignoreEmpty: false, resolve: 'none', shelf: shelfId, tidy: false,
               workspace: manualSpec(charset: 'none', cleanup: false, name: workspace, pinHost: false,
                          spec: clientSpec(allwrite: false, backup: true, changeView: '', clobber: true, compress: false,
                                           line: 'LOCAL', locked: false, modtime: false, rmdir: false, serverID: '',
                                           streamName: '', type: 'WRITABLE', view: mapping))
}

def getChangelistDescription(Map config) {
    def credential = config.credential
    def workspace = config.workspace
    def mapping = config.mapping
    def changelistId = config.changelistId

    def p4s = p4(credential: credential,
                 workspace: manualSpec(charset: 'none', cleanup: false, name: workspace, pinHost: false,
                            spec: clientSpec(allwrite: true, backup: true, changeView: '', clobber: false, compress: false,
                                             line: 'LOCAL', locked: false, modtime: false, rmdir: false, serverID: '',
                                             streamName: '', type: 'WRITABLE', view: mapping)))
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

    p4publish credential: credential,
              publish: submit(delete: false, description: 'Submitted by Jenkins. Build: ${BUILD_TAG}', modtime: false,
                              onlyOnSuccess: true, paths: '', purge: '', reopen: false),
              workspace: staticSpec(charset: 'none', name: workspace, pinHost: false)
}

def cleanup(Map config) {
    def credential = config.credential
    def workspace = config.workspace
    def mapping = config.mapping

    def p4s = p4(credential: credential,
                 workspace: manualSpec(charset: 'none', cleanup: false, name: workspace, pinHost: false,
                            spec: clientSpec(allwrite: true, backup: true, changeView: '', clobber: false, compress: false,
                                             line: 'LOCAL', locked: false, modtime: false, rmdir: false, serverID: '',
                                             streamName: '', type: 'WRITABLE', view: mapping)))
    p4s.run('revert', '-c', 'default', '//...')
}
