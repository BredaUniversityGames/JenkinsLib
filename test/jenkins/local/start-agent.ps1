<#
.SYNOPSIS
  Connects this PC to the local test Jenkins as the "windows-agent" build agent.

.DESCRIPTION
  Runs in the foreground; Ctrl+C disconnects. Start the controller in WSL first
  (start-controller.sh). Builds run as your Windows user, with your PATH and tools.

.PARAMETER Java
  java.exe to run the agent with. Defaults to a JDK 21 that Gradle downloaded
  (~/.gradle/jdks), then JAVA_HOME, then java on PATH.
#>
param(
    [string]$JenkinsUrl = 'http://localhost:8095',
    [string]$AgentName = 'windows-agent',
    [string]$WorkDir = 'C:\JenkinsAgent',
    [string]$Distro = 'Ubuntu',
    [string]$Java
)

$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'JenkinsLocal.psm1') -Force

function Find-Java {
    $gradleJdks = Join-Path $env:USERPROFILE '.gradle\jdks'
    if (Test-Path $gradleJdks) {
        $candidate = Get-ChildItem $gradleJdks -Directory -Filter '*21*' |
            ForEach-Object { Join-Path $_.FullName 'bin\java.exe' } |
            Where-Object { Test-Path $_ } |
            Select-Object -First 1
        if ($candidate) { return $candidate }
    }
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        return Join-Path $env:JAVA_HOME 'bin\java.exe'
    }
    $onPath = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    throw 'No Java found. Run .\gradlew.bat test once (it downloads JDK 21), or pass -Java <path to java.exe>.'
}

if (-not $Java) { $Java = Find-Java }
Write-Host "Java:  $Java"

$jenkins = Connect-Jenkins -Url $JenkinsUrl -Distro $Distro

New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null
$agentJar = Join-Path $WorkDir 'agent.jar'
Invoke-WebRequest -Uri "$($jenkins.Url)/jnlpJars/agent.jar" -OutFile $agentJar -UseBasicParsing

# The agent's secret is in its connection file (admin only)
$jnlp = [xml](Get-ResponseText (Invoke-Jenkins -Jenkins $jenkins -Path "computer/$AgentName/jenkins-agent.jnlp"))
$secret = $jnlp.jnlp.'application-desc'.argument[0]

# Builds clone this repo from your working copy (file://), including the wiki
# submodule. Git blocks file:// for submodules by default; allow it for this
# agent process only (your global git config is unchanged).
$env:GIT_CONFIG_COUNT = '1'
$env:GIT_CONFIG_KEY_0 = 'protocol.file.allow'
$env:GIT_CONFIG_VALUE_0 = 'always'

Write-Host "Agent: $AgentName -> $($jenkins.Url) (work dir $WorkDir)"
& $Java -jar $agentJar -url $jenkins.Url -name $AgentName -secret $secret -webSocket -workDir $WorkDir
