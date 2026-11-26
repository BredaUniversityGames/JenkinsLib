# Shared helpers for start-agent.ps1 and run-local.ps1.
# Works in Windows PowerShell 5.1 and PowerShell 7.

Set-StrictMode -Version Latest

function Get-AdminPassword {
    param([string]$Distro)
    $password = & wsl.exe -d $Distro -- bash -c 'cat "${JENKINSLIB_LOCAL_HOME:-$HOME/jenkinslib-local}/admin-password"' 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $password) {
        throw "Could not read the admin password from WSL ($Distro). Run test/jenkins/local/setup-controller.sh in WSL first."
    }
    return ($password | Select-Object -First 1).Trim()
}

<#
  Returns an object for calling the Jenkins API as admin:
  .Url, .Headers (Basic auth + CSRF crumb) and .Session (the crumb is tied to it).
#>
function Connect-Jenkins {
    param(
        [string]$Url,
        [string]$Distro
    )
    $Url = $Url.TrimEnd('/')
    $password = Get-AdminPassword -Distro $Distro
    $basic = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("admin:$password"))
    $headers = @{ Authorization = "Basic $basic" }

    try {
        $crumb = Invoke-RestMethod -Uri "$Url/crumbIssuer/api/json" -Headers $headers -SessionVariable session -UseBasicParsing
    } catch {
        throw "Jenkins is not reachable at $Url. Is start-controller.sh running in WSL? ($($_.Exception.Message))"
    }
    $headers[$crumb.crumbRequestField] = $crumb.crumb

    return [pscustomobject]@{ Url = $Url; Headers = $headers; Session = $session }
}

function Invoke-Jenkins {
    param(
        [Parameter(Mandatory)] $Jenkins,
        [Parameter(Mandatory)] [string]$Path,
        [string]$Method = 'Get',
        $Body = $null
    )
    $request = @{
        Uri             = "$($Jenkins.Url)/$($Path.TrimStart('/'))"
        Method          = $Method
        Headers         = $Jenkins.Headers
        WebSession      = $Jenkins.Session
        UseBasicParsing = $true
    }
    if ($null -ne $Body) { $request.Body = $Body }
    return Invoke-WebRequest @request
}

function Get-ResponseText {
    param($Response)
    # PowerShell 7 returns byte[] for content types it doesn't treat as text
    if ($Response.Content -is [byte[]]) { return [Text.Encoding]::UTF8.GetString($Response.Content) }
    return [string]$Response.Content
}

function Get-HeaderValue {
    param($Response, [string]$Name)
    $value = $Response.Headers[$Name]
    if ($null -eq $value) { return $null }
    return @($value)[0]   # PowerShell 7 returns string[], 5.1 a string
}

Export-ModuleMember -Function Get-AdminPassword, Connect-Jenkins, Invoke-Jenkins, Get-ResponseText, Get-HeaderValue
