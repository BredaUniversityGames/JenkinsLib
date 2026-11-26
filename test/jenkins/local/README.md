# Local test Jenkins

A Jenkins on your own PC for trying the library in the Groovy sandbox,
without committing:

- **Controller** in WSL (Ubuntu), at <http://localhost:8095>
- **Build agent** on Windows (this PC), labelled `Windows`
- Library and jobs load from a **snapshot of your working copy**

The controller and agent run on different operating systems, like a real
setup, so code that wrongly runs on the controller fails here too.

## Setup (once)

In WSL (no `sudo` needed; everything goes into `~/jenkinslib-local`):

```sh
bash /mnt/c/Projects/BredaUniversityGames/JenkinsLib/test/jenkins/local/setup-controller.sh
```

It downloads a Temurin JDK 21, Jenkins LTS and the plugins in `plugins.txt`,
and creates an admin password in `~/jenkinslib-local/admin-password`.
Delete `~/jenkinslib-local` to start over.

## Start

1. Controller, in a WSL terminal (stop with Ctrl+C):

   ```sh
   bash /mnt/c/Projects/BredaUniversityGames/JenkinsLib/test/jenkins/local/start-controller.sh
   ```

2. Agent, in a PowerShell terminal (stop with Ctrl+C):

   ```powershell
   .\test\jenkins\local\start-agent.ps1
   ```

   It uses the JDK 21 that `gradlew test` downloaded. Builds run as your
   Windows user with your PATH and tools.

Log in at <http://localhost:8095> as `admin` with the password from
`~/jenkinslib-local/admin-password`.

## Run a job

```powershell
.\test\jenkins\local\run-local.ps1 cmake-local
```

1. Snapshots your working copy, including uncommitted and new files, to the
   local branch `jenkins-local`. Your branch, staging area and history aren't
   touched, and the snapshot replaces the previous one. It's never pushed
   unless you run `git push --all`.
2. Refreshes the job's parameters, then builds with the values in
   `params/<job>.properties`, streaming the console. Add
   `-Param @{ NAME = 'value' }` to override values.
3. Exits with 0 when the build succeeded.

`-RefreshOnly` stops after the parameter refresh, which runs all the code
that executes while parameters are declared.

| Job | What it's for |
| --- | --- |
| `cmake-local` | Builds `test/jenkins/sample-cmake` end to end (Git, CMake matrix, CTest, Zip). Needs CMake, Ninja and Visual Studio. |
| `all-modules` | Registers every module. Use with `-RefreshOnly`; it can't build. |
| `cmake`, `ue5`, `vs` | The Jenkinsfiles for the real server (`test/jenkins/`). Need real tools and credentials to build. |

## Finding sandbox blocks

A blocked call fails the build with
`RejectedAccessException: Scripts not permitted to use <signature>`.
Jenkins stops at the first one. To see the next, approve the signature under
**Manage Jenkins → In-process Script Approval** and run again; on this local
server that's fine, but clear the approvals afterwards so it stays strict.
Never approve them on the real server: the goal is a library that needs none.

`casc.yaml` sets `UE5_ENGINE_ROOT` on `windows-agent` (to `C:\Games\Epic Games`;
change it if your engines live elsewhere), the way Server-Setup.md describes
for real agents.

## Test-server-only settings

These are acceptable here and must not be copied to a real server:

- `hudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true`: the library and jobs
  come from a local repository.
- `protocol.file.allow=always` for the agent process only (`start-agent.ps1`):
  the build's checkout fetches the wiki submodule from your working copy.
- A single local admin account.
