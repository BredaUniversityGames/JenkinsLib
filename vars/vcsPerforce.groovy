/**
 * Perforce VCS module.
 * Stateless - all configuration passed via Map parameters.
 */

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
