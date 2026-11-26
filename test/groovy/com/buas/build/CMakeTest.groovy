package com.buas.build

import com.buas.testing.StepsStub
import org.junit.Before
import org.junit.Test

class CMakeTest {

    StepsStub steps
    CMake cmake

    static final String PRESETS_JSON = '''
    {
      "version": 6,
      "configurePresets": [
        { "name": "base", "hidden": true, "binaryDir": "${sourceDir}/build/base" },
        { "name": "windows", "inherits": "base", "binaryDir": "${sourceDir}/build/${presetName}" },
        { "name": "linux" }
      ],
      "buildPresets": [
        { "name": "windows-debug",   "configurePreset": "windows", "configuration": "Debug" },
        { "name": "windows-release", "configurePreset": "windows", "configuration": "Release" },
        { "name": "hidden-build",    "configurePreset": "windows", "hidden": true }
      ],
      "testPresets": [
        { "name": "windows-tests", "configurePreset": "windows" },
        { "name": "linux-tests",   "configurePreset": "linux" }
      ],
      "packagePresets": [
        { "name": "windows-zip", "configurePreset": "windows", "configurations": ["Release"] },
        { "name": "windows-out", "configurePreset": "windows", "outputDirectory": "${sourceParentDir}/dist/${presetName}" }
      ],
      "workflowPresets": [
        { "name": "ci" }
      ]
    }
    '''

    @Before
    void setUp() {
        steps = new StepsStub()
        cmake = new CMake(steps)
    }

    // ── parsePackagePath ──

    @Test
    void parsesPackagePathFromCPackOutput() {
        def output = '''CPack: Create package using ZIP
CPack: - package: C:/ws/build/Game-1.0-win64.zip generated.'''
        assert CMake.parsePackagePath(output) == 'C:\\ws\\build\\Game-1.0-win64.zip'
    }

    @Test
    void returnsNullWhenCPackOutputHasNoPackage() {
        assert CMake.parsePackagePath('CPack: Create package using ZIP\nCPack Error: nothing') == null
    }

    // ── preset parsing ──

    @Test
    void parsesVisiblePresetNames() {
        cmake.parsePresets(PRESETS_JSON)
        def p = cmake.presets

        assert p.configurePresets == ['windows', 'linux']
        assert p.buildPresets == ['windows-debug', 'windows-release']
        assert p.testPresets == ['windows-tests', 'linux-tests']
        assert p.packagePresets == ['windows-zip', 'windows-out']
        assert p.workflowPresets == ['ci']
    }

    @Test
    void mapsPresetsToTheirConfigurePreset() {
        cmake.parsePresets(PRESETS_JSON)

        assert cmake.getBuildConfigurePreset('windows-release') == 'windows'
        assert cmake.getTestConfigurePreset('linux-tests') == 'linux'
        assert cmake.getPackageConfigurePreset('windows-zip') == 'windows'
        assert cmake.getBuildConfigurePreset('hidden-build') == null
    }

    @Test
    void findsBuildPresetsForConfigurePresetAndConfigurations() {
        cmake.parsePresets(PRESETS_JSON)

        assert cmake.findBuildPresets('windows', null) == ['windows-debug', 'windows-release']
        assert cmake.findBuildPresets('windows', ['Release']) == ['windows-release']
        assert cmake.findBuildPresets('linux', null) == []
    }

    @Test
    void resolvesPackageOutputDirectory() {
        cmake.parsePresets(PRESETS_JSON)

        // Explicit outputDirectory wins
        assert cmake.getPackageOutputDir('windows-out') == '../dist/windows-out'
        // Otherwise the configure preset's binaryDir
        assert cmake.getPackageOutputDir('windows-zip') == './build/windows'
        assert cmake.getPackageOutputDir('nope') == null
    }

    @Test
    void overridePresetListsSkipDiscovery() {
        def presets = cmake.discoverPresets('.', [CMAKE_BUILD_PRESETS: ['a', 'b']])

        assert presets.buildPresets == ['a', 'b']
        assert steps.callsTo('node').isEmpty()
    }

    @Test
    void discoveryReadsPresetsFromWorkspace() {
        steps.env.JOB_NAME = 'Team/Game'
        steps.handlers.fileExists = { String path -> path == 'CMakePresets.json' }
        steps.handlers.readFile = { Map args -> PRESETS_JSON }

        def presets = cmake.discoverPresets('.', [:])

        assert presets.buildPresets == ['windows-debug', 'windows-release']
    }

    // ── pipeline parameters ──

    private static List names(List params) { params.collect { it.arguments.name } }

    @Test
    void buildParamsWithoutPresetsUseManualSettings() {
        steps.env.JOB_NAME = 'Team/Game'

        assert names(cmake.buildPipelineParams()) == [
            'CMAKE_SOURCE_DIR', 'CMAKE_BUILD_DIR', 'CMAKE_GENERATOR', 'CMAKE_CONFIG', 'CMAKE_PLATFORM',
            'CMAKE_ARGS', 'CMAKE_BUILD_ARGS'
        ]
    }

    @Test
    void buildParamsWithPresetsOfferPresetChoice() {
        def params = cmake.buildPipelineParams(CMAKE_BUILD_PRESETS: ['debug', 'release'])

        assert names(params) == ['CMAKE_SOURCE_DIR', 'CMAKE_BUILD_PRESET', 'CMAKE_ARGS', 'CMAKE_BUILD_ARGS']
        assert params[1].arguments.choices == ['debug', 'release']
    }

    @Test
    void buildPresetChoiceKeepsPreviousSelectionFirst() {
        steps.params = [CMAKE_BUILD_PRESET: 'release']

        def params = cmake.buildPipelineParams(CMAKE_BUILD_PRESETS: ['debug', 'release'])

        assert params[1].arguments.choices == ['release', 'debug']
    }

    // ── commands ──

    private List<String> batScripts() { steps.callsTo('bat').collect { it.args[0].script } }

    @Test
    void configureAddsArchitectureOnlyForVisualStudioGenerators() {
        cmake.configure(generator: 'Visual Studio 17 2022', platform: 'x64')
        cmake.configure(generator: 'Ninja', platform: 'x64')

        def scripts = batScripts()
        assert scripts[0].contains('-G "Visual Studio 17 2022" -DCMAKE_BUILD_TYPE=Debug -A x64')
        assert !scripts[1].contains(' -A ')
    }

    @Test
    void commandsLoadTheVisualStudioEnvironmentFirst() {
        cmake.build(buildDir: 'out', config: 'Release', platform: 'Win32')

        def script = batScripts()[0]
        assert script.contains('vcvarsall.bat" x86')
        assert script.endsWith('cmake --build "out" --config Release')
    }

    @Test
    void executeWithPresetConfiguresThenBuilds() {
        cmake.parsePresets(PRESETS_JSON)
        def ctx = [:]

        cmake.execute([CMAKE_BUILD_PRESET: 'windows-release', CMAKE_ARGS: '-DFOO=1'], ctx)

        def scripts = batScripts()
        assert scripts[0].contains('cmake --preset "windows" -DFOO=1')
        assert scripts[1].contains('cmake --build --preset "windows-release"')
        assert ctx.cmakeBuildPreset == 'windows-release'
        assert ctx.buildEngine == 'CMake'
    }
}
