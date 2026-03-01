# JenkinsLib

A Jenkins Shared Library for Breda University of Applied Sciences game development projects. Provides a modular, composable pipeline that supports UE5 and Visual Studio builds with multiple deployment targets.

Credit to [DavidtKate/JenkinsSharedLib](https://github.com/DavidtKate/JenkinsSharedLib) and [Sigma-Erebus/JenkinsLib](https://github.com/Sigma-Erebus/JenkinsLib) for providing part of the foundation upon which this is built.

## Quick Start

```groovy
library identifier: 'JenkinsLib@main',
    retriever: modernSCM([$class: 'GitSCMSource',
        remote: 'https://github.com/BredaUniversityGames/JenkinsLib'])

stages {
    perforce.sync()
    ue5.build()
    // ue5.test()
    // steam.deploy()
    // itch.deploy()
    discord.notify()
}
```

Comment or uncomment modules to add or remove stages. All configuration is done via Jenkins UI parameters.

## Documentation

See the **[Wiki](../../wiki)** for full documentation:

- **[Quick Start](../../wiki/Quick-Start)** — Set up your first pipeline
- **[Server Setup](../../wiki/Server-Setup)** — Jenkins administration, plugins, security, credentials
- **[Architecture](../../wiki/Architecture)** — Module system, design principles, how to extend

### Stage Reference

| Category | Stages |
| --- | --- |
| Version Control | [perforce.sync()](../../wiki/Perforce), [git.sync()](../../wiki/Git) |
| Build | [ue5.build()](../../wiki/Build-UE5), [vs.build()](../../wiki/Build-VS) |
| Testing | [ue5.test()](../../wiki/Test-UE5), [vs.test()](../../wiki/Test-VS) |
| Deployment | [steam.deploy()](../../wiki/Steam), [itch.deploy()](../../wiki/Itch), [gdrive.deploy()](../../wiki/GDrive), [epic.deploy()](../../wiki/Epic) |
| Code Review | [swarm.review()](../../wiki/Swarm) |
| Debug Symbols | [sentry.upload()](../../wiki/Sentry) |
| Notifications | [discord.notify()](../../wiki/Discord) |
