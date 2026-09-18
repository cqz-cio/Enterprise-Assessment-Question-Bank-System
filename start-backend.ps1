[CmdletBinding()]
param([switch]$Restart, [string]$JarPath)
. (Join-Path $PSScriptRoot 'scripts/ops/Backend.Common.ps1')
if (-not $JarPath) { $JarPath = Join-Path $PSScriptRoot 'yf-bev2-api/target/yf-bev2-api.jar' }
$jar = (Resolve-Path -LiteralPath $JarPath).Path
$allowed = (Join-Path $script:BackendRoot 'yf-bev2-api/target') + [IO.Path]::DirectorySeparatorChar
if (-not $jar.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase) -or [IO.Path]::GetExtension($jar) -ne '.jar') { throw 'JAR must be in this project target directory.' }
$secrets = Read-BackendSecrets
try {
    $running = Get-ProjectBackend
    if ($running -and -not $Restart) { Write-Host "Project backend already running: PID $($running.ProcessId). Use -Restart to restart it."; exit 0 }
    $java = (Get-Command java.exe -ErrorAction Stop).Source
    if ($running) { Stop-ProjectBackend -BackendProcess $running }
    $listener = @(Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue)
    if ($listener.Count) { throw 'Port 8080 is already in use; no unrelated process was stopped.' }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $logDir = Join-Path $script:BackendRoot 'work/codex-logs'
    New-Item -ItemType Directory -Force -Path $logDir | Out-Null
    $stdout = Join-Path $logDir "$stamp-backend.log"
    $stderr = Join-Path $logDir "$stamp-backend.stderr.log"
    $previous = @{}
    try {
        foreach ($name in $secrets.Keys) {
            $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
            [Environment]::SetEnvironmentVariable($name, $secrets[$name], 'Process')
        }
        $arguments = '-jar "' + $jar + '" --logging.level.root=INFO --logging.level.com.yf=INFO'
        $child = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory (Join-Path $script:BackendRoot 'yf-bev2-api') -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
        $handle = $child.Handle
    } finally {
        foreach ($name in $previous.Keys) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    }
    @{pid=$child.Id; jar=$jar; log=$stdout; stderr=$stderr; startedAt=$stamp} | ConvertTo-Json | Set-Content -LiteralPath $script:BackendRecord -Encoding UTF8
    $clock = [Diagnostics.Stopwatch]::StartNew()
    $lastSize = -1; $lastProgress = 0
    while ($clock.Elapsed.TotalSeconds -lt 90 -and -not $child.HasExited) {
        try {
            $reply = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8080/api/sys/config/detail' -Method Post -ContentType 'application/json' -Body '{}' -TimeoutSec 2
            if ($reply.StatusCode -eq 200 -and ($reply.Content | ConvertFrom-Json).code -eq 0) {
                Write-Host "Backend ready: PID $($child.Id); http://localhost:8080/"
                Write-Host "Log: $stdout"
                Write-Host "Secrets: $script:BackendSecrets (Windows user encrypted)"
                exit 0
            }
        } catch { }
        $size = (Get-Item -LiteralPath $stdout).Length + (Get-Item -LiteralPath $stderr).Length
        if ($size -ne $lastSize) { $lastSize = $size; $lastProgress = $clock.Elapsed.TotalSeconds }
        if (($clock.Elapsed.TotalSeconds - $lastProgress) -ge 60) { break }
        Start-Sleep -Milliseconds 500
    }
    $failed = Get-ProjectBackend
    if ($failed -and $failed.ProcessId -eq $child.Id) { Stop-ProjectBackend -BackendProcess $failed }
    throw "Backend did not become ready within 90 seconds. See $stdout and $stderr"
} finally { $secrets.Clear() }
