# Sandbox test jobs

These Jenkinsfiles run JenkinsLib as an **untrusted** library, so every call the
Groovy sandbox would block shows up. They're for the refactor in
[#2](https://github.com/BredaUniversityGames/JenkinsLib/issues/2) and
[#4](https://github.com/BredaUniversityGames/JenkinsLib/issues/4); students never use them.

To try them without a server or a commit, use the local test Jenkins in
[`local/`](local/README.md). The steps below are for a real server.

## One-time setup

1. Create a folder, e.g. `JenkinsLib-Sandbox`, and open **Configure**.
2. Under **Pipeline Libraries**, click **Add**:
   - **Name:** `JenkinsLibSandbox` (a different name from the global library, so there's no confusion about which one loads)
   - **Default version:** `dev`
   - **Allow default version to be overridden:** checked
   - **Retrieval method:** Modern SCM → Git → `https://github.com/BredaUniversityGames/JenkinsLib.git`

   Libraries configured on a folder always run in the sandbox, even for admins.
3. Create one Pipeline job per Jenkinsfile in this folder, using **Pipeline script from SCM**
   with the JenkinsLib repo and the script path, e.g. `test/jenkins/cmake.Jenkinsfile`.
   Point the job at the branch you're testing (e.g. `dev` or a `refactor/...` branch).
   The Jenkinsfiles don't pin a library version, so the library comes from the folder's
   **Default version**; change that to test another branch.

## Running a check

1. Run the job once. The first run only declares the parameters.
2. Fill in the parameters and run **Build with Parameters**.
   Use real credentials and paths for an agent you can build on, or leave deploy
   stages out of the Jenkinsfile to focus on build stages.
3. If the build fails with `RejectedAccessException: Scripts not permitted to use ...`,
   that's a sandbox block. Note the signature in issue #2 (Phase 0) or #4 (Phase 2).
4. Check **Manage Jenkins → In-process Script Approval** for pending signatures,
   but **don't approve them**: the goal is for the library to need none.

Some code only runs when certain settings are present, so cover these too:

| Code | When it runs |
| --- | --- |
| UE5 engine detection (`UE5.detectEngines`) | While declaring parameters, if `UE5_ENGINE_ROOT` is set where the parameters are evaluated |
| GDrive token signing and upload | In the GDrive deploy stage |
| UE5 test report (`MarkupBuilder`) | In the UE5 test stage |
| Matrix (`ModuleRegistry` push/pop) | Any Jenkinsfile with `matrix { }` |

## Files

| File | Covers |
| --- | --- |
| `cmake.Jenkinsfile` | `git.sync`, `github.version`, CMake build/test/pack in a matrix with `only`, `zip.pack`, `gdrive.deploy`, `github.release`, `discord.alert` |
| `ue5.Jenkinsfile` | `perforce.sync`, `ue5.build`, `ue5.test`, `steam.deploy`, `itch.deploy`, `sentry.upload`, `discord.alert` |
| `vs.Jenkinsfile` | `git.sync`, `vs.build`, `vs.test`, `zip.pack`, `discord.alert` |
