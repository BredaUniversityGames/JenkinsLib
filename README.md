# JenkinsLib

A Jenkins Shared Library for Breda University of Applied Sciences game development projects. Provides a modular, composable pipeline that supports UE5 and Visual Studio builds with multiple deployment targets.

Credit to [DavidtKate/JenkinsSharedLib](https://github.com/DavidtKate/JenkinsSharedLib) and [Sigma-Erebus/JenkinsLib](https://github.com/Sigma-Erebus/JenkinsLib) for providing part of the foundation upon which this is built.

## Quick Start

```groovy
library identifier: 'JenkinsLib@main',
    retriever: modernSCM([$class: 'GitSCMSource',
        remote: 'https://github.com/BredaUniversityGames/JenkinsLib'])

stages {
    vcs.perforce()
    build.ue5()
    // test.ue5()
    // deploy.steam()
    // deploy.itch()
    notify.discord()
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
| Version Control | [vcs.perforce()](../../wiki/stages/vcs/Perforce), [vcs.git()](../../wiki/stages/vcs/Git) |
| Build | [build.ue5()](../../wiki/stages/build/UE5), [build.vs()](../../wiki/stages/build/VS) |
| Testing | [test.ue5()](../../wiki/stages/test/UE5), [test.vs()](../../wiki/stages/test/VS) |
| Deployment | [deploy.steam()](../../wiki/stages/deploy/Steam), [deploy.itch()](../../wiki/stages/deploy/Itch), [deploy.gdrive()](../../wiki/stages/deploy/GDrive), [deploy.epic()](../../wiki/stages/deploy/Epic) |
| Code Review | [review.swarm()](../../wiki/stages/review/Swarm) |
| Debug Symbols | [symbols.sentry()](../../wiki/stages/symbols/Sentry) |
| Notifications | [notify.discord()](../../wiki/stages/notify/Discord) |
