// Job DSL: the sandbox folder, its library and the test jobs.
// Applied by Configuration as Code every time the controller starts.

def repo = System.getenv('JENKINSLIB_REPO')          // file:///mnt/c/... as seen from WSL
def snapshot = 'jenkins-local'                        // ref written by run-local.ps1

folder('JenkinsLib-Sandbox') {
    description('JenkinsLib loaded as an untrusted folder library, so it runs in the Groovy sandbox.')
    properties {
        folderLibraries {
            libraries {
                libraryConfiguration {
                    name('JenkinsLibSandbox')
                    defaultVersion(snapshot)
                    implicit(false)
                    allowVersionOverride(true)
                    includeInChangesets(false)
                    retriever {
                        modernSCM {
                            scm {
                                git {
                                    remote(repo)
                                    id('jenkinslib-local')
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

def jobs = [
    // Runs end to end on this PC (CMake, Ninja, Visual Studio)
    'cmake-local': 'test/jenkins/local/jobs/cmake-local.Jenkinsfile',
    // Registers every module; use with -RefreshOnly to check parameter-time code
    'all-modules': 'test/jenkins/local/jobs/all-modules.Jenkinsfile',
    // The jobs meant for the real server (need tools and credentials to build)
    'cmake':       'test/jenkins/cmake.Jenkinsfile',
    'ue5':         'test/jenkins/ue5.Jenkinsfile',
    'vs':          'test/jenkins/vs.Jenkinsfile',
]

jobs.each { name, jenkinsfile ->
    pipelineJob("JenkinsLib-Sandbox/${name}") {
        description("Jenkinsfile: ${jenkinsfile} (from the ${snapshot} snapshot)")
        // Declared up front so the job can be started with parameters before
        // its first run; the library replaces these with the full list.
        parameters {
            booleanParam('REFRESH_PARAMETERS', true, 'Only refresh pipeline parameters (skip build)')
            booleanParam('CLEAN_WORKSPACE', false, 'Clean workspace after build')
        }
        definition {
            cpsScm {
                scm {
                    git {
                        remote { url(repo) }
                        branch(snapshot)
                    }
                }
                scriptPath(jenkinsfile)
                lightweight(true)
            }
        }
    }
}
