package com.buas.build

/**
 * Zip packaging module.
 * Creates a ZIP archive from a source directory without requiring CPack
 * or install() rules in CMakeLists.txt.
 *
 * Uses PowerShell and System.IO.Compression.ZipFile (available on all Windows agents).
 *
 * The generated archive is placed in the workspace root so that deploy
 * modules (e.g. github.release with GH_RELEASE_ASSETS = '*.zip') can
 * find it via a simple glob.
 */
class Zip implements Serializable {
    def steps

    Zip(steps) {
        this.steps = steps
    }

    def pipelineParams(Map overrides = [:]) {
        def prev = steps.params ?: [:]
        return [
            steps.string(name: 'ZIP_SOURCE_DIR',
                defaultValue: overrides.ZIP_SOURCE_DIR ?: prev.ZIP_SOURCE_DIR ?: '',
                description: 'Directory to zip (supports ${PARAM} placeholders, e.g. build/${CMAKE_BUILD_PRESET}/bin/game)'),
            steps.string(name: 'ZIP_ARCHIVE_NAME',
                defaultValue: overrides.ZIP_ARCHIVE_NAME ?: prev.ZIP_ARCHIVE_NAME ?: '',
                description: 'Output archive name without extension (supports ${PARAM} placeholders, default: source directory name)')
        ]
    }

    def execute(Map params, Map ctx) {
        def sourceDir = resolveTemplate(params.ZIP_SOURCE_DIR ?: '', params)
        if (!sourceDir) {
            steps.error('ZIP_SOURCE_DIR is required for zip.pack()')
        }

        def archiveName = resolveTemplate(params.ZIP_ARCHIVE_NAME ?: '', params)
        if (!archiveName) {
            // Default to the last path component of the source directory
            archiveName = sourceDir.replaceAll(/[\\/]$/, '').split(/[\\/]/).last()
        }

        // Verify the source directory exists
        def exists = steps.bat(
            label: "Check ${sourceDir} exists",
            script: "@if not exist \"${sourceDir}\\\" exit /b 1",
            returnStatus: true
        )
        if (exists != 0) {
            steps.error("zip.pack(): source directory '${sourceDir}' does not exist")
        }

        // Create the archive in the workspace root
        def archivePath = "${archiveName}.zip"
        steps.powershell(
            label: "Zip ${sourceDir} -> ${archivePath}",
            script: """
                Add-Type -Assembly 'System.IO.Compression.FileSystem'
                \$src  = (Resolve-Path \"${sourceDir}\").Path
                \$dest = [System.IO.Path]::GetFullPath(\"${archivePath}\")
                if (Test-Path \$dest) { Remove-Item \$dest -Force }
                [System.IO.Compression.ZipFile]::CreateFromDirectory(\$src, \$dest)
            """
        )

        ctx.outputDir = "${steps.env.WORKSPACE}\\${archivePath}"
    }

    /**
     * Replace ${PARAM_NAME} placeholders with values from the pipeline params.
     */
    private static String resolveTemplate(String template, Map params) {
        return template.replaceAll(/\$\{(\w+)\}/) { groups ->
            params[groups[1]] ?: groups[0]
        }
    }

}
