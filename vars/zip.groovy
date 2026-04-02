/**
 * Zip packaging module.
 * Usage: zip.pack()
 *
 * Creates a ZIP archive from a source directory. Works with any build
 * system — no CPack or install() rules required.
 *
 * Example (inside a matrix with CMake presets):
 *
 *   stages {
 *       git.sync()
 *       matrix(CMAKE_BUILD_PRESET: ['Developer-Packaging', 'Release-Game']) {
 *           cmake.build()
 *           zip.pack(ZIP_SOURCE_DIR: 'build/${CMAKE_BUILD_PRESET}/bin/game')
 *       }
 *       github.release()
 *   }
 */

import com.buas.ModuleRegistry
import com.buas.build.Zip

def pack(Map overrides = [:]) {
    def impl = new Zip(this)
    ModuleRegistry.register(
        category: 'pack',
        name: 'Zip Package',
        params: impl.pipelineParams(overrides),
        execute: { params, ctx -> impl.execute(params, ctx) },
        hasCleanup: false
    )
}
