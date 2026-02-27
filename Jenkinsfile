// JenkinsLib - Example Jenkinsfile
//
// This is the uber-template entry point. Copy this file to your project
// repo as 'Jenkinsfile', create a Pipeline job in Jenkins, run once to
// populate parameters, then configure everything via the Jenkins UI.
//
// See README.md for full parameter documentation.

library identifier: 'JenkinsLib@main',
    retriever: modernSCM([
      $class: 'GitSCMSource',
      remote: 'https://github.com/BredaUniversityGames/JenkinsLib'
    ])

buasPipeline()
