package com.buas.build

/**
 * Zip packaging module.
 * Creates a ZIP archive from a source directory without requiring CPack
 * or install() rules in CMakeLists.txt.
 *
 * Uses 7-Zip when available, falls back to PowerShell Compress-Archive.
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
                description: 'Output archive name without extension (supports ${PARAM} placeholders, default: source directory name)'),
            steps.choice(name: 'ZIP_METHOD',
                choices: reorderChoices(['7z', 'powershell'], prev.ZIP_METHOD),
                description: 'Compression tool to use')
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

        def use7z = (params.ZIP_METHOD ?: '7z') == '7z'

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
        if (use7z) {
            steps.bat(label: "Zip ${sourceDir} -> ${archivePath}",
                script: "7z a \"${archivePath}\" \".\\${sourceDir}\\*\"")
        } else {
            steps.powershell(label: "Zip ${sourceDir} -> ${archivePath}",
                script: "Compress-Archive -Path \"${sourceDir}\\*\" -DestinationPath \"${archivePath}\" -Force")
        }

        ctx.outputDir = "${steps.env.WORKSPACE}\\${archivePath}"
    }

    /**
     * Replace ${PARAM_NAME} placeholders with values from the pipeline params.
     */
    private static String resolveTemplate(String template, Map params) {
        return template.replaceAll(/\$\{(\w+)\}/) { match, key ->
            params[key] ?: match
        }
    }

    private static List<String> reorderChoices(List<String> choices, String previous) {
        if (!previous || !choices.contains(previous)) return choices
        return [previous] + choices.findAll { it != previous }
    }
}
