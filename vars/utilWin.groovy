def makeWritable(folderPath) {
    bat(label: "Removing Read-Only flags", script: "attrib -r ${folderPath}*.* /s")
}

def copyPathFiles(sourcePath, targetPath, fileFilter = "/e") {
    try {
        bat(label: "Copying files", script: "robocopy ${sourcePath} ${targetPath} ${fileFilter}")
    } catch (err) {
        echo "Caught: ${err}"
        echo "Exit Code 1 means success for robocopy"
    }
}

def movePathFiles(sourcePath, targetPath, fileFilter = "/e") {
    try {
        bat(label: "Moving files", script: "robocopy ${sourcePath} ${targetPath} ${fileFilter} /mov")
    } catch (err) {
        echo "Caught: ${err}"
        echo "Exit Code 1 means success for robocopy"
    }
}

def platformOutputDir(String platform) {
    return (platform == 'Win64') ? 'Windows' : platform
}
