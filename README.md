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
| Version Control | [perforce.sync()](../../wiki/stages/vcs/Perforce), [git.sync()](../../wiki/stages/vcs/Git) |
| Build | [ue5.build()](../../wiki/stages/build/Build-UE5), [vs.build()](../../wiki/stages/build/Build-VS) |
| Testing | [ue5.test()](../../wiki/stages/test/Test-UE5), [vs.test()](../../wiki/stages/test/Test-VS) |
| Deployment | [steam.deploy()](../../wiki/stages/deploy/Steam), [itch.deploy()](../../wiki/stages/deploy/Itch), [gdrive.deploy()](../../wiki/stages/deploy/GDrive), [epic.deploy()](../../wiki/stages/deploy/Epic) |
| Code Review | [swarm.review()](../../wiki/stages/review/Swarm) |
| Debug Symbols | [sentry.upload()](../../wiki/stages/symbols/Sentry) |
| Notifications | [discord.notify()](../../wiki/stages/notify/Discord) |
