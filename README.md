# JenkinsLib

A Jenkins Shared Library for Breda University of Applied Sciences game development projects. Provides a modular, parameterized pipeline that supports UE5 and Visual Studio builds with multiple deployment targets.

Credit to [DavidtKate/JenkinsSharedLib](https://github.com/DavidtKate/JenkinsSharedLib) and [Sigma-Erebus/JenkinsLib](https://github.com/Sigma-Erebus/JenkinsLib) for providing part of the foundation upon which this is built.

## Quick Start

1. Copy the root `Jenkinsfile` from this repo into your project repo
2. Create a Pipeline job in Jenkins pointing to your repo
3. Run the job once to populate parameters
4. Configure the parameters in the Jenkins UI (checkboxes, dropdowns, text fields)
5. Run again - your pipeline is ready

Your Jenkinsfile only needs these lines:

```groovy
library identifier: 'JenkinsLib@main',
    retriever: modernSCM([$class: 'GitSCMSource',
        remote: 'https://github.com/BredaUniversityGames/JenkinsLib'])

buasPipeline()
```

All configuration is done via Jenkins UI parameters - no Groovy knowledge required.

## Creating a New Pipeline in Jenkins

### Step 1: Add the Jenkinsfile to Your Project

Copy the root `Jenkinsfile` from this repository into the root of your project repository.
If your project uses Git, commit and push it to the branch you want to build (e.g. `main`).
If your project uses Perforce, submit it to the depot.

### Step 2: Create a Pipeline Job

1. Log in to your Jenkins server
2. Click **New Item** in the left sidebar
3. Enter a name for your job (e.g. `MyGame-Build`)
4. Select **Pipeline** as the job type
5. Click **OK**

### Step 3: Configure the Pipeline Source

Under the **Pipeline** section at the bottom of the job configuration page:

**For Git repositories:**

1. Set **Definition** to `Pipeline script from SCM`
2. Set **SCM** to `Git`
3. Enter your **Repository URL** (e.g. `https://github.com/YourOrg/YourProject.git`)
4. If the repository is private, select the appropriate **Credentials**
   (see [Setting Up Git Credentials](#setting-up-git-credentials) below)
5. Set **Branch Specifier** to the branch you want to build (e.g. `*/main`)
6. Leave **Script Path** as `Jenkinsfile`

**For Perforce repositories:**

1. Set **Definition** to `Pipeline script from SCM`
2. Set **SCM** to `Perforce Software`
3. Select the **Credential** for your Perforce server
   (see [Setting Up Perforce with Jenkins](#setting-up-perforce-with-jenkins) below)
4. Under **Workspace Behaviour**, choose `Manual (custom view)` and enter the depot mapping
   that includes your `Jenkinsfile`
5. Leave **Script Path** as `Jenkinsfile`

### Step 4: First Run (Parameter Population)

1. Click **Save** to save the job configuration
2. Click **Build Now** in the left sidebar
3. This first build will likely fail - that is expected. Its purpose is to register
   all the pipeline parameters (checkboxes, dropdowns, text fields) with Jenkins.
4. After this run completes, go back to the job page. You should now see a
   **Build with Parameters** option in the left sidebar instead of **Build Now**.

### Step 5: Configure Parameters

1. Click **Build with Parameters**
2. You will see all available parameters organized by category:
   - **Version Control** - VCS type, credentials, repository details
   - **Build** - Engine type, build method, paths, configuration
   - **Testing** - Enable/disable tests, test mode, filters
   - **Deployment** - Toggle and configure Steam, itch.io, Google Drive, Epic
   - **Notifications** - Discord webhook and toggles
   - **Optional Stages** - Swarm reviews, Sentry symbols, workspace cleanup
3. Fill in the parameters relevant to your project and leave the rest at their defaults
4. Click **Build** to run your first real build

### Tips

- **Saving parameters:** Jenkins remembers the last values you used. You only need to
  set them up once and they persist across builds.
- **Folder organization:** Consider creating a Jenkins Folder for your project
  (New Item > Folder) and placing the pipeline job inside it. This keeps credentials
  scoped and the dashboard tidy.
- **Build triggers:** Under the job configuration, you can set up automatic build triggers:
  - **Poll SCM** - Jenkins checks for changes on a schedule (e.g. `H/15 * * * *` for every 15 minutes)
  - **Webhook** - Configure your Git hosting or Perforce to notify Jenkins on push/submit
  - **Build periodically** - Run on a fixed schedule (e.g. nightly builds with `H 2 * * *`)

### Setting Up Git Credentials

If your Git repository is private, you need to add credentials in Jenkins:

**For top-level pipelines:**

1. Go to **Manage Jenkins** > **Credentials** > **System** > **Global credentials (unrestricted)**
2. Click **Add Credentials**

**For pipelines inside a folder:**

1. Navigate to the folder in Jenkins
2. Click **Credentials** in the left sidebar
3. Click **(folder)** > **Global credentials (unrestricted)**
4. Click **Add Credentials**

> Folder-scoped credentials are only visible to jobs inside that folder.
> Global credentials are visible to all jobs on the server.

**Then configure the credential:**

1. For HTTPS repositories:
   - **Kind**: `Username with password`
   - **Username**: your Git username
   - **Password**: a personal access token (not your password - most Git hosts require tokens)
   - **ID**: give it a memorable ID (e.g. `git-myproject`)
2. For SSH repositories:
   - **Kind**: `SSH Username with private key`
   - **Username**: `git`
   - **Private Key**: paste your SSH private key
   - **ID**: give it a memorable ID (e.g. `git-ssh-myproject`)
3. Click **Create**

Use the credential ID you chose as the `GIT_CREDENTIALS_ID` pipeline parameter.

## Setting Up Perforce with Jenkins

### Prerequisites

- The [P4 Plugin](https://plugins.jenkins.io/p4/) must be installed on your Jenkins server
  (Manage Jenkins > Plugins > search "P4 Plugin")
- A Perforce user account that Jenkins will use to sync (ideally a dedicated service account)
- The Perforce server must be reachable from the Jenkins agent

### Step 1: Create a Perforce Credential in Jenkins

**For top-level pipelines:**

1. Go to **Manage Jenkins** > **Credentials** > **System** > **Global credentials (unrestricted)**
2. Click **Add Credentials**

**For pipelines inside a folder:**

1. Navigate to the folder in Jenkins
2. Click **Credentials** in the left sidebar
3. Click **(folder)** > **Global credentials (unrestricted)**
4. Click **Add Credentials**

> Folder-scoped credentials are only visible to jobs inside that folder.
> Global credentials are visible to all jobs on the server.

The P4 Plugin supports two credential types: **Password** and **Ticket**. Use whichever
matches your Perforce server's authentication setup.

#### Option A: Password Credential

Use this when your Perforce server accepts standard password authentication.

- **Kind**: `Perforce Password Credential`
- **P4Port**: your Perforce server address (e.g. `ssl:perforce.buas.nl:1666`)
- **Username**: the Perforce username
- **Password**: the Perforce password
- **ID**: give it a memorable ID (e.g. `p4-myproject`) - you will need this for `P4_CREDENTIAL`
- **Description**: optional, e.g. "P4 credential for MyProject"

#### Option B: Ticket Credential

Use this when your Perforce server uses ticket-based authentication (common when the server
uses SAML/SSO, has security level 3+, or when LDAP/AD authentication is configured and
passwords cannot be used directly).

A Perforce ticket is a session token generated by `p4 login`. Our Perforce server uses
SAML authentication, so login happens through the browser rather than with a password.

To obtain a ticket:

1. Open a terminal on the Jenkins agent (or any machine with the `p4` CLI)
2. Set the server address and username if not already configured:

   ```sh
   p4 set P4PORT=ssl:perforce.buas.nl:1666
   p4 set P4USER=your-username
   ```

3. If you are already logged in (e.g. through P4V), you can print your existing ticket
   without re-authenticating:

   ```sh
   p4 tickets
   ```

   If the output includes a hex string for your user, copy it and skip to step 6.
   If no ticket is listed or it has expired, continue with step 4.

4. Log in via SAML to generate a new ticket:

   ```sh
   p4 login -p
   ```

   This opens your default browser to the SAML identity provider login page.
   Complete the authentication in the browser (e.g. university SSO credentials).
   Once authenticated, the terminal prints a ticket value (a hex string like
   `A1B2C3D4E5F6...`). The `-p` flag prints the ticket to stdout instead of storing it
   in the ticket file.

   > If the browser does not open automatically, the terminal will display a URL.
   > Copy and paste it into your browser manually.

5. For a long-lived ticket (recommended for CI), request a non-expiring ticket:

   ```sh
   p4 login -p -a
   ```

   The `-a` flag creates a ticket that is valid from any IP address. Note that the
   Perforce admin must have enabled long-lived or unlimited tickets for the service account.
6. Copy the hex string that is printed - this is your ticket value.

> **Tip:** You can verify a ticket is still valid by running `p4 login -s`. If the ticket
> has expired, simply repeat the `p4 login -p` steps to generate a new one through the
> browser.

Now create the credential in Jenkins. Fill in the fields in order:

- **Scope**: leave as `Global (Jenkins, nodes, items, all child items, etc)` unless you
  need to restrict visibility
- **ID**: optional - give it a memorable ID (e.g. `p4-myproject`) to make it easier to
  reference as `P4_CREDENTIAL`. If left blank, Jenkins will generate one automatically.
- **Description**: optional, e.g. "P4 ticket credential for MyProject"
- **P4Port**: your Perforce server address without the `ssl:` prefix (e.g. `perforce.buas.nl:1666`)
- **SSL connection**: check this box if your server uses SSL (i.e. you would normally
  connect with `ssl:perforce.buas.nl:1666`). When you click **Test Connection** later,
  Jenkins may fail with a "Trust not established" error. If this happens, you need to
  trust the server's SSL fingerprint first - see
  [Trusting the SSL Fingerprint](#trusting-the-ssl-fingerprint) below
- **Username**: the Perforce username
- **Login with ticket value**: select this option and paste the hex string from
  `p4 tickets` or `p4 login -p`

Click **Test Connection** to verify the credential works, then click **Create** to save.

#### Trusting the SSL Fingerprint

If your Perforce server uses SSL and **Test Connection** fails with "Trust not established",
you need to establish SSL trust on the Jenkins agent machine:

1. Open a terminal **on the Jenkins agent** (the machine that runs your builds)
2. Run `p4 trust` to accept the server's SSL certificate:

   ```sh
   p4 -p ssl:perforce.buas.nl:1666 trust
   ```

   This will display the server's fingerprint and ask you to confirm. Type `yes` to accept.

3. Verify trust was established:

   ```sh
   p4 trust -l
   ```

   You should see your server listed with its fingerprint.

> **Important:** The `p4 trust` command must be run as the same OS user that the Jenkins
> agent runs as (e.g. the Jenkins service account). If Jenkins runs as a different user,
> either run the command under that user or copy the `.p4trust` file to that user's home
> directory.

Go back to your credential in Jenkins and click **Test Connection** again - it should now
succeed.

### Step 2: Create a Perforce Workspace

You need a Perforce workspace (client spec) that maps the depot paths you want to build.
A template workspace `jenkins-TemplatePipeline` is provided as a starting point - create
your own workspace based on it.

In P4V:

1. Find the `jenkins-TemplatePipeline` workspace in the depot or workspace list
2. Right-click on it and select **Create/Update Workspace from...**
   (also available via **Edit** > **Create/Update Workspace from...**)
3. Name the new workspace `jenkins-<YourProjectName>` (e.g. `jenkins-MyProject`)
4. Set the **Root** to match the Jenkins agent workspace path. This must match the folder
   Jenkins uses for the job on the build agent (e.g. `C:\Jenkins\workspace\<JOB_NAME>`)
5. Adjust the **View** mapping to point to your project's depot path:

```
//depot/MyProject/... //jenkins-MyProject/MyProject/...
```

> **Tip:** Only map the folder containing your `.uproject` file. Mapping the entire depot wastes
> sync time and disk space.

Alternatively, via the `p4` CLI, you can create a workspace from the template directly:

```sh
p4 client -t jenkins-TemplatePipeline jenkins-MyProject
```

Then edit it to adjust the root and view mapping for your project:

```sh
p4 client jenkins-MyProject
```

### Step 3: Configure the Pipeline Parameters

After your first pipeline run populates the parameters, set:

| Parameter             | Example Value                                                     |
| --------------------- | ----------------------------------------------------------------- |
| `VCS_TYPE`            | `Perforce`                                                        |
| `P4_CREDENTIAL`       | `p4-myproject` (the credential ID from step 1)                    |
| `P4_HOST`             | `ssl:perforce.buas.nl:1666`                                       |
| `P4_WORKSPACE`        | `jenkins-MyProject` (for template source)                         |
| `P4_MAPPING`          | `//depot/MyProject/... //jenkins-MyProject/MyProject/...`         |
| `P4_USE_DEPOT_SOURCE` | `true` if using depot mapping directly, `false` for workspace template |
| `P4_FORCE_CLEAN`      | `false` (set to `true` to force a clean sync every build)         |

### Workspace Template vs Depot Source

The pipeline supports two Perforce sync modes:

- **Workspace template** (`P4_USE_DEPOT_SOURCE = false`): Uses an existing workspace spec as a
  template. Jenkins creates a workspace named `jenkins-<JOB_NAME>` based on the template in
  `P4_WORKSPACE`. This is the simplest option if you already have a workspace configured in P4V.

- **Depot source** (`P4_USE_DEPOT_SOURCE = true`): Uses the raw depot mapping from `P4_MAPPING`
  directly. Jenkins creates a workspace named `jenkins-<JOB_NAME>-<NODE_NAME>`. This is more
  flexible and works well when you don't want to maintain a workspace template in Perforce.

### Troubleshooting

- **"Trust not established"**: Your server uses SSL and Jenkins hasn't trusted the fingerprint yet.
  See [Trusting the SSL Fingerprint](#trusting-the-ssl-fingerprint) for step-by-step instructions.
- **"Client 'xxx' unknown"**: The workspace name in `P4_WORKSPACE` doesn't match an existing
  workspace on the server. Double-check the name in P4V under Connection > Edit Current Workspace.
- **Sync takes too long**: Your view mapping is too broad. Narrow it to only the folders needed
  for building.
- **"File(s) up-to-date"**: This is normal - it means nothing changed since the last sync.

## Pipeline Parameters

### Version Control

| Parameter            | Description                                            |
| -------------------- | ------------------------------------------------------ |
| `VCS_TYPE`           | `Perforce` or `Git`                                    |
| `P4_CREDENTIAL`      | Jenkins credential ID for Perforce                     |
| `P4_HOST`            | Perforce server (default: `ssl:perforce.buas.nl:1666`) |
| `P4_WORKSPACE`       | Perforce workspace template name                       |
| `P4_MAPPING`         | Perforce depot view mapping                            |
| `P4_FORCE_CLEAN`     | Force clean sync                                       |
| `GIT_REPO_URL`       | Git repository URL                                     |
| `GIT_BRANCH`         | Git branch (default: `main`)                           |
| `GIT_CREDENTIALS_ID` | Jenkins credential ID for Git                          |

### Build

| Parameter          | Description                                             |
| ------------------ | ------------------------------------------------------- |
| `BUILD_ENGINE`     | `UE5` or `VisualStudio`                                 |
| `UE5_BUILD_METHOD` | `Blueprint`, `Precompiled`, or `Custom`                 |
| `UE5_ENGINE_ROOT`  | Path to UE5 engine root                                 |
| `UE5_PROJECT_PATH` | Absolute path to `.uproject` file                       |
| `UE5_PROJECT_NAME` | Project name (no extension)                             |
| `UE5_CUSTOM_FLAGS` | Custom RunUAT flags (for Custom method)                 |
| `BUILD_CONFIG`     | `Development`, `Shipping`, `DebugGame`, `Debug`, `Test` |
| `BUILD_PLATFORM`   | `Win64`, `Linux`, `PS5`                                 |
| `VS_MSBUILD_PATH`  | Path to `MSBuild.exe`                                   |
| `VS_PROJECT_PATH`  | Path to `.sln` or `.vcxproj`                            |
| `VS_CONFIG`        | VS build configuration                                  |
| `VS_PLATFORM`      | VS target platform                                      |

### Testing

| Parameter            | Description                                    |
| -------------------- | ---------------------------------------------- |
| `RUN_TESTS`          | Enable automated testing                       |
| `UE5_TEST_MODE`      | `RunAll`, `RunNamed`, or `RunFiltered`         |
| `UE5_TEST_NAMES`     | Semicolon-separated test names                 |
| `UE5_TEST_FILTER`    | `Product`, `Smoke`, `Engine`, `Stress`, `Perf` |
| `VS_TEST_FRAMEWORK`  | `None`, `CTest`, or `GoogleTest`               |
| `VS_TEST_EXECUTABLE` | Path to test executable or CTest build dir     |

### Deployment

| Parameter                                              | Description                            |
| ------------------------------------------------------ | -------------------------------------- |
| `DEPLOY_STEAM`                                         | Enable Steam deployment                |
| `STEAM_CREDENTIAL`                                     | Jenkins credential (username/password) |
| `STEAM_CMD_PATH`                                       | Path to `steamcmd.exe`                 |
| `STEAM_APP_ID`                                         | Steam App ID                           |
| `STEAM_DEPOT_ID`                                       | Steam Depot ID                         |
| `STEAM_BRANCH`                                         | Steam branch to set live               |
| `DEPLOY_ITCH`                                          | Enable itch.io deployment              |
| `ITCH_BUTLER_PATH`                                     | Path to Butler executable              |
| `ITCH_CREDENTIALS_ID`                                  | Jenkins credential for Butler API key  |
| `ITCH_TARGET`                                          | itch.io target (`user/game:channel`)   |
| `DEPLOY_GDRIVE`                                        | Enable Google Drive upload             |
| `GDRIVE_CREDENTIALS_ID`                                | Jenkins credential for service account |
| `GDRIVE_FOLDER_ID`                                     | Google Drive folder ID                 |
| `DEPLOY_EPIC`                                          | Enable Epic Games Store deployment     |
| `EPIC_BPT_PATH`                                        | Path to `BuildPatchTool.exe`           |
| `EPIC_ORG_ID` / `EPIC_PRODUCT_ID` / `EPIC_ARTIFACT_ID` | Epic identifiers                       |
| `EPIC_CLIENT_ID` / `EPIC_CLIENT_SECRET_ID`             | Epic credentials                       |

### Notifications

| Parameter         | Description                                  |
| ----------------- | -------------------------------------------- |
| `NOTIFY_DISCORD`  | Enable Discord notifications (default: true) |
| `DISCORD_WEBHOOK` | Discord webhook URL                          |

### Optional Stages

| Parameter               | Description                                 |
| ----------------------- | ------------------------------------------- |
| `ENABLE_SWARM_REVIEW`   | Enable Helix Swarm code reviews             |
| `UPLOAD_SENTRY_SYMBOLS` | Upload debug symbols to Sentry              |
| `MATCH_BUILD_ID`        | Run MatchBuildID.py for precompiled engines |
| `CLEAN_WORKSPACE`       | Clean workspace after build (default: true) |

## Architecture

### Module Organization

All modules live in `vars/` and follow a domain-prefix naming convention:

| Prefix    | Modules                                                   | Purpose            |
| --------- | --------------------------------------------------------- | ------------------ |
| `vcs`     | `vcsPerforce`, `vcsGit`                                   | Version control    |
| `build`   | `buildUE5`, `buildVS`                                     | Build systems      |
| `deploy`  | `deploySteam`, `deployItch`, `deployGDrive`, `deployEpic` | Deployment targets |
| `notify`  | `notify` (dispatcher), `notifyDiscord`                    | Notifications      |
| `review`  | `reviewSwarm`                                             | Code reviews       |
| `symbols` | `symbolsSentry`                                           | Debug symbols      |
| `util`    | `utilWin`, `utilZip`, `utilPython`                        | Utilities          |
| `test`    | `testRunner`                                              | Generic testing    |

### Design Principles

1. **Stateless modules** - No module-level mutable state. All config passed via Map parameters.
2. **Pluggable notifications** - Add channels by creating `notifyFoo.groovy` and adding 2 lines to the dispatcher.
3. **Parallel deployments** - Independent deploy stages run simultaneously.
4. **Secure credentials** - All secrets use Jenkins `withCredentials`, never raw parameters.

### Adding a New Notification Channel

1. Create `vars/notifySlack.groovy` implementing `send(Map buildInfo, String destination)`
2. Add `NOTIFY_SLACK` boolean and `SLACK_WEBHOOK` string parameters to `buasPipeline.groovy`
3. Add to `notify.groovy`:

   ```groovy
   if (params.NOTIFY_SLACK && params.SLACK_WEBHOOK) {
       notifySlack.send(buildInfo, params.SLACK_WEBHOOK)
   }
   ```

### Overriding Defaults from Code

Pass defaults to `buasPipeline()` for teams that always use the same setup:

```groovy
buasPipeline(
    VCS_TYPE: 'Perforce',
    BUILD_ENGINE: 'UE5',
    DEPLOY_STEAM: true
)
```

These are initial defaults - students can still override them per-run in the Jenkins UI.

## Groups System (for Swarm/Discord Integration)

The `reviewSwarm` and `notifyDiscord` modules use a JSON groups format:

```json
{
  "groups": [
    {
      "name": "TeamLead",
      "discordID": "123456789",
      "swarmID": ["swarmuser1"],
      "type": "user"
    },
    {
      "name": "Developers",
      "discordID": "987654321",
      "swarmID": ["dev1", "dev2"],
      "type": "role"
    }
  ]
}
```

Types: `user` (`<@ID>`), `role` (`<@&ID>`), `channel` (`<#ID>`)

## Python Scripts

| Script                 | Usage                                                |
| ---------------------- | ---------------------------------------------------- |
| `GoogleDriveUpload.py` | Resumable upload to Google Drive via service account |
| `MatchBuildID.py`      | Force-match BuildIDs between engine and plugins      |
