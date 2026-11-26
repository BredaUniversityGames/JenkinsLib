<#
.SYNOPSIS
  Snapshots your working copy and runs a job on the local test Jenkins.

.DESCRIPTION
  1. Writes your working copy (including uncommitted and new files, minus
     .gitignore'd ones) to the local branch "jenkins-local". Your current branch,
     staging area and history are not touched; the previous snapshot is replaced.
  2. Runs the job with REFRESH_PARAMETERS so its parameters match the snapshot.
  3. Unless -RefreshOnly, runs the job again with the values from
     params/<job>.properties (plus -Param overrides) and streams the console.

  Exits with 0 when the last build succeeded, 1 otherwise.

.EXAMPLE
  .\run-local.ps1 cmake-local
.EXAMPLE
  .\run-local.ps1 all-modules -RefreshOnly
.EXAMPLE
  .\run-local.ps1 cmake-local -Param @{ CMAKE_GENERATOR = 'Visual Studio 18 2026' }
#>
param(
    [Parameter(Mandatory, Position = 0)]
    [ValidateSet('cmake-local', 'all-modules', 'cmake', 'ue5', 'vs')]
    [string]$Job,
    [switch]$RefreshOnly,
    [switch]$NoSnapshot,
    [hashtable]$Param = @{},
    [string]$JenkinsUrl = 'http://localhost:8095',
    [string]$Distro = 'Ubuntu'
)

$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'JenkinsLocal.psm1') -Force

$snapshotRef = 'refs/heads/jenkins-local'
$repo = (& git -C $PSScriptRoot rev-parse --show-toplevel).Trim()

function Write-Step([string]$text) { Write-Host "`n== $text" -ForegroundColor Cyan }

function Update-Snapshot {
    Write-Step 'Snapshot working copy -> jenkins-local'
    $tempIndex = [IO.Path]::Combine([IO.Path]::GetTempPath(), "jenkins-local-index-$PID")
    $previousIndex = $env:GIT_INDEX_FILE
    try {
        # A throwaway index: start from HEAD, add everything, write a tree.
        $env:GIT_INDEX_FILE = $tempIndex
        & git -C $repo read-tree HEAD
        # safecrlf=false: skip the "LF will be replaced by CRLF" warning per file
        & git -C $repo -c core.safecrlf=false add -A
        if ($LASTEXITCODE -ne 0) { throw 'git add failed' }
        $tree = (& git -C $repo write-tree).Trim()
    } finally {
        if ($previousIndex) { $env:GIT_INDEX_FILE = $previousIndex } else { Remove-Item Env:GIT_INDEX_FILE -ErrorAction SilentlyContinue }
        Remove-Item $tempIndex -ErrorAction SilentlyContinue
    }

    $commit = (& git -C $repo commit-tree $tree -p HEAD -m "jenkins-local snapshot of $(& git -C $repo rev-parse --abbrev-ref HEAD)").Trim()
    & git -C $repo update-ref $snapshotRef $commit
    # Forget old snapshots right away so git gc can drop them
    & git -C $repo reflog expire --expire=now --expire-unreachable=now $snapshotRef 2>$null

    $changed = @(& git -C $repo diff --name-only HEAD $commit)
    Write-Host "jenkins-local = $($commit.Substring(0, 10)) (HEAD + $($changed.Count) changed file(s))"
}

function Read-JobParams {
    $file = Join-Path $PSScriptRoot "params\$Job.properties"
    $values = [ordered]@{}
    if (Test-Path $file) {
        $repoUrl = 'file:///' + ($repo -replace '\\', '/')
        foreach ($line in Get-Content $file) {
            if ($line -match '^\s*(#|$)') { continue }
            $key, $value = $line -split '=', 2
            $values[$key.Trim()] = $value.Trim().Replace('{{REPO_URL}}', $repoUrl)
        }
    }
    foreach ($key in $Param.Keys) { $values[$key] = [string]$Param[$key] }
    return $values
}

function Wait-ForBuild($jenkins, $queueUrl) {
    $queuePath = ([Uri]$queueUrl).AbsolutePath
    while ($true) {
        $item = Get-ResponseText (Invoke-Jenkins -Jenkins $jenkins -Path "$queuePath/api/json") | ConvertFrom-Json
        if ($item.PSObject.Properties['executable'] -and $item.executable) { return $item.executable.url }
        if ($item.cancelled) { throw 'The build was cancelled while queued.' }
        Start-Sleep -Seconds 1
    }
}

function Show-Console($jenkins, $buildUrl) {
    $buildPath = ([Uri]$buildUrl).AbsolutePath
    $start = 0
    do {
        $resp = Invoke-Jenkins -Jenkins $jenkins -Path "$buildPath/logText/progressiveText?start=$start"
        # Drop Jenkins' hidden console markup (ESC[8m ha:////... ESC[0m)
        $text = (Get-ResponseText $resp) -replace '\x1b\[8mha:[^\x1b]*\x1b\[0m', ''
        if ($text) { Write-Host -NoNewline $text }
        $start = Get-HeaderValue $resp 'X-Text-Size'
        $more = (Get-HeaderValue $resp 'X-More-Data') -eq 'true'
        if ($more) { Start-Sleep -Milliseconds 700 }
    } while ($more)
    $build = Get-ResponseText (Invoke-Jenkins -Jenkins $jenkins -Path "$buildPath/api/json?tree=result") | ConvertFrom-Json
    return $build.result
}

function Start-JobBuild($jenkins, [System.Collections.IDictionary]$values, [string]$label) {
    Write-Step "$Job : $label"
    $resp = Invoke-Jenkins -Jenkins $jenkins -Method Post -Path "job/JenkinsLib-Sandbox/job/$Job/buildWithParameters" -Body $values
    $buildUrl = Wait-ForBuild $jenkins (Get-HeaderValue $resp 'Location')
    Write-Host "$buildUrl`n"
    $result = Show-Console $jenkins $buildUrl
    $color = if ($result -eq 'SUCCESS' -or $result -eq 'NOT_BUILT') { 'Green' } else { 'Red' }
    Write-Host "`nResult: $result" -ForegroundColor $color
    return $result
}

<#
  Splits the wanted values into the ones the job accepts right now and the
  ones it doesn't: names it doesn't declare yet, and choice values that aren't
  in the choice list (Jenkins rejects those with HTTP 500).
#>
function Split-ParamValues($jenkins, [System.Collections.IDictionary]$wanted) {
    $json = Get-ResponseText (Invoke-Jenkins -Jenkins $jenkins -Path "job/JenkinsLib-Sandbox/job/$Job/api/json?tree=property[parameterDefinitions[name,choices]]") | ConvertFrom-Json
    $definitions = @{}
    foreach ($property in @($json.property)) {
        if ($property.PSObject.Properties['parameterDefinitions']) {
            foreach ($definition in @($property.parameterDefinitions)) { $definitions[$definition.name] = $definition }
        }
    }
    $accepted = [ordered]@{}
    $rejected = [ordered]@{}
    foreach ($name in $wanted.Keys) {
        $definition = $definitions[$name]
        $isChoice = $definition -and $definition.PSObject.Properties['choices'] -and $null -ne $definition.choices
        if (-not $definition -or ($isChoice -and @($definition.choices) -notcontains $wanted[$name])) {
            $rejected[$name] = $wanted[$name]
        } else {
            $accepted[$name] = $wanted[$name]
        }
    }
    return @{ Accepted = $accepted; Rejected = $rejected }
}

function Invoke-Refresh($jenkins, [System.Collections.IDictionary]$wanted) {
    $values = [ordered]@{}
    foreach ($name in $wanted.Keys) { $values[$name] = $wanted[$name] }
    $values['REFRESH_PARAMETERS'] = 'true'
    $values['CLEAN_WORKSPACE'] = 'false'
    $split = Split-ParamValues $jenkins $values
    $result = Start-JobBuild $jenkins $split.Accepted 'refresh parameters'
    return @{ Result = $result; Rejected = $split.Rejected }
}

if (-not $NoSnapshot) { Update-Snapshot }

$jenkins = Connect-Jenkins -Url $JenkinsUrl -Distro $Distro
$wanted = Read-JobParams

# Parameters can change with the code, so always refresh them first. The
# refresh gets the wanted values too, because parameter-time code uses them
# (e.g. git.sync() lists the branches of GIT_REPO_URL).
$refresh = Invoke-Refresh $jenkins $wanted
if ($refresh.Result -eq 'NOT_BUILT' -and $refresh.Rejected.Count -gt 0) {
    # Values the job didn't accept before may be valid now (new parameters,
    # a branch list that now includes jenkins-local): refresh once more.
    $retry = Split-ParamValues $jenkins $refresh.Rejected
    if ($retry.Accepted.Count -gt 0) { $refresh = Invoke-Refresh $jenkins $wanted }
}
if ($refresh.Result -ne 'NOT_BUILT') { exit 1 }
if ($RefreshOnly) { exit 0 }

$wanted['REFRESH_PARAMETERS'] = 'false'
$split = Split-ParamValues $jenkins $wanted
foreach ($name in $split.Rejected.Keys) {
    Write-Warning "Job $Job doesn't accept $name=$($split.Rejected[$name]); Jenkins will use the default."
}
$result = Start-JobBuild $jenkins $split.Accepted 'build'
if ($result -eq 'SUCCESS') { exit 0 } else { exit 1 }
