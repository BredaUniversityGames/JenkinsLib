# JenkinsLib

A Jenkins Shared Library for Breda University of Applied Sciences game development projects. Provides a modular, composable pipeline that supports UE5, Visual Studio, and CMake builds with multiple deployment targets.

Credit to [DavidtKate/JenkinsSharedLib](https://github.com/DavidtKate/JenkinsSharedLib) and [Sigma-Erebus/JenkinsLib](https://github.com/Sigma-Erebus/JenkinsLib) for providing part of the foundation upon which this is built.

## Quick Start

```groovy
@Library('JenkinsLib') _

stages {
    perforce.sync()
    ue5.build()
    // ue5.test()
    // steam.deploy()
    // itch.deploy()
    discord.alert()
}
```

Comment or uncomment modules to add or remove stages. All configuration is done via Jenkins UI parameters.

### Matrix Builds

Build multiple platform/config combinations from a single Jenkinsfile using `matrix()`:

```groovy
stages {
    perforce.sync()
    matrix(UE5_BUILD_PLATFORM: ['Win64', 'PS4'],
           UE5_BUILD_CONFIG: ['Development', 'Shipping']) {
        ue5.build()
        only(UE5_BUILD_PLATFORM: 'Win64', UE5_BUILD_CONFIG: 'Shipping') {
            steam.deploy()
        }
    }
    discord.alert()
}
```

Stages outside the matrix run once; stages inside repeat for each combination. Use `only()` to restrict specific stages to certain combinations. See **[Matrix Builds](../../wiki/Matrix)** for details.

## Documentation

See the **[Wiki](../../wiki)** for full documentation:

- **[Quick Start](../../wiki/Quick-Start)** — Set up your first pipeline
- **[Server Setup](../../wiki/Server-Setup)** — Jenkins administration, plugins, security, credentials
- **[Architecture](../../wiki/Architecture)** — Module system, design principles, how to extend
- **[Matrix Builds](../../wiki/Matrix)** — Multi-axis builds for platform/config combinations

### Stage Reference

| Category | Stages |
| --- | --- |
| Version Control | [perforce.sync()](../../wiki/Perforce), [git.sync()](../../wiki/Git) |
| Build | [ue5.build()](../../wiki/Build-UE5), [vs.build()](../../wiki/Build-VS), [cmake.build()](../../wiki/Build-CMake) |
| Testing | [ue5.test()](../../wiki/Test-UE5), [vs.test()](../../wiki/Test-VS), [cmake.test()](../../wiki/Test-CMake) |
| Deployment | [steam.deploy()](../../wiki/Steam), [itch.deploy()](../../wiki/Itch), [gdrive.deploy()](../../wiki/GDrive), [epic.deploy()](../../wiki/Epic) |
| Code Review | [swarm.review()](../../wiki/Swarm) |
| Debug Symbols | [sentry.upload()](../../wiki/Sentry) |
| Notifications | [discord.alert()](../../wiki/Discord) |
| Matrix | [matrix()](../../wiki/Matrix), [only()](../../wiki/Matrix#only-conditional-filter) |
