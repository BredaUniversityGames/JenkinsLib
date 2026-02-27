def runScript(scriptPath, args) {
    bat(label: "Running ${scriptPath}", script: "python \"${scriptPath}\" ${args}")
}
