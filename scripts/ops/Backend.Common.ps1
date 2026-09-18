$ErrorActionPreference = 'Stop'
$script:BackendRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$script:BackendLocal = Join-Path $script:BackendRoot '.local'
$script:BackendSecrets = Join-Path $script:BackendLocal 'backend-secrets.clixml'
$script:BackendRecord = Join-Path $script:BackendLocal 'backend-process.json'

function Protect-BackendDirectory {
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT) { throw 'This secret store requires Windows DPAPI.' }
    New-Item -ItemType Directory -Force -Path $script:BackendLocal | Out-Null
    if ((Get-Item -LiteralPath $script:BackendLocal).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Secret directory must not be a link.' }
    $acl = New-Object Security.AccessControl.DirectorySecurity
    $acl.SetAccessRuleProtection($true, $false)
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl.SetOwner($sid)
    foreach ($principal in @($sid, (New-Object Security.Principal.SecurityIdentifier('S-1-5-18')))) {
        $rule = New-Object Security.AccessControl.FileSystemAccessRule($principal, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
        $acl.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $script:BackendLocal -AclObject $acl
}

function Read-BackendSecrets {
    param([string]$Path = $script:BackendSecrets)
    if (-not (Test-Path -LiteralPath $Path)) { throw 'Encrypted secrets missing. Run initialize-backend-secrets.ps1 in the original launch PowerShell first. Do not generate replacement keys.' }
    try {
        $store = Import-Clixml -LiteralPath $Path
        if ($store.Version -ne 1) { throw 'Unsupported secret store version.' }
        $result = @{}
        foreach ($name in @('JWT_SECRET', 'ASSIGNMENT_CODE_PEPPER')) {
            if ($store.$name -isnot [Security.SecureString]) { throw 'Invalid encrypted secret.' }
            $value = (New-Object Net.NetworkCredential('', $store.$name)).Password
            if ([string]::IsNullOrWhiteSpace($value) -or $value.Length -lt 32) { throw 'Invalid secret length.' }
            $result[$name] = $value
            $value = $null
        }
        return $result
    } catch { throw 'Cannot decrypt the secret store. Use the same Windows user and computer; restore the original keys instead of replacing them.' }
}

function Get-ProjectBackend {
    $processes = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -OperationTimeoutSec 10 | Where-Object {
        $_.CommandLine -and $_.CommandLine.Replace('/', '\').Contains((Join-Path $script:BackendRoot 'yf-bev2-api')) -and $_.CommandLine -match '-jar\s'
    })
    if ($processes.Count -gt 1) { throw 'More than one project backend found; refusing an ambiguous stop/start.' }
    if ($processes.Count -eq 1) { return $processes[0] }
}

function Stop-ProjectBackend {
    param($BackendProcess)
    if (-not $BackendProcess) { return }
    # Check identity again immediately before terminating this exact project process.
    $current = Get-ProjectBackend
    if (-not $current -or $current.ProcessId -ne $BackendProcess.ProcessId -or $current.CreationDate -ne $BackendProcess.CreationDate) { throw 'Backend identity changed; stop cancelled.' }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $logDir = Join-Path $script:BackendRoot 'work/codex-logs'
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $stopLog = Join-Path $logDir "$stamp-backend-stop.log"
    $stopError = Join-Path $logDir "$stamp-backend-stop.stderr.log"
    $killer = Start-Process -FilePath "$env:SystemRoot/System32/taskkill.exe" -ArgumentList @('/PID', [string]$current.ProcessId, '/T', '/F') -WindowStyle Hidden -RedirectStandardOutput $stopLog -RedirectStandardError $stopError -PassThru
    $handle = $killer.Handle
    if (-not $killer.WaitForExit(10000)) { $killer.Kill(); throw "Backend stop timed out. See $stopLog" }
    $exitWait = [Diagnostics.Stopwatch]::StartNew()
    while ((Get-Process -Id $current.ProcessId -ErrorAction SilentlyContinue) -and $exitWait.Elapsed.TotalSeconds -lt 5) {
        Start-Sleep -Milliseconds 100
    }
    # Windows PowerShell can return a null ExitCode for a redirected child process.
    # Verify the actual backend state instead of treating that null as a failure.
    $remaining = Get-ProjectBackend
    if ($remaining -and $remaining.ProcessId -eq $current.ProcessId -and $remaining.CreationDate -eq $current.CreationDate) { throw "Backend stop failed. See $stopLog" }
    if (Test-Path -LiteralPath $script:BackendRecord) { Remove-Item -LiteralPath $script:BackendRecord }
    Write-Host "Backend stopped: PID $($current.ProcessId). Log: $stopLog"
}
