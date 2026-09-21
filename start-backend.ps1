[CmdletBinding()]
param([switch]$Restart, [string]$JarPath, [ValidateSet('dev', 'prod')][string]$Profile = 'dev', [string]$ConfigFile)
. (Join-Path $PSScriptRoot 'scripts/ops/Backend.Common.ps1')
$savedRecord = $null
$launchRecordPath = Join-Path $script:BackendLocal 'backend-launch.json'
$recordToRead = if (Test-Path -LiteralPath $launchRecordPath) { $launchRecordPath } else { $script:BackendRecord }
if (Test-Path -LiteralPath $recordToRead) {
    try { $savedRecord = Get-Content -LiteralPath $recordToRead -Raw | ConvertFrom-Json }
    catch { throw "Cannot read the previous backend launch record: $recordToRead" }
}
if (-not $PSBoundParameters.ContainsKey('Profile') -and $savedRecord.profile -eq 'prod') { $Profile = 'prod' }
if ($Profile -eq 'prod' -and $savedRecord.profile -eq 'prod') {
    if (-not $JarPath) { $JarPath = $savedRecord.jar }
    if (-not $ConfigFile) { $ConfigFile = $savedRecord.configFile }
}
if (-not $JarPath) { $JarPath = Join-Path $PSScriptRoot 'yf-bev2-api/target/yf-bev2-api.jar' }
$jar = (Resolve-Path -LiteralPath $JarPath).Path
$allowed = (Join-Path $script:BackendRoot 'yf-bev2-api/target') + [IO.Path]::DirectorySeparatorChar
if (-not $jar.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase) -or [IO.Path]::GetExtension($jar) -ne '.jar') { throw 'JAR must be in this project target directory.' }
$secrets = Read-BackendSecrets
try {
    $configArgument = ''
    if ($Profile -eq 'prod') {
        if (-not $ConfigFile) { $ConfigFile = Join-Path $script:BackendLocal 'application-production.properties' }
        $configPath = (Resolve-Path -LiteralPath $ConfigFile).Path
        if (-not $configPath.StartsWith($script:BackendLocal + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Production configuration must be in the ignored .local directory.' }
        if ([IO.File]::ReadAllText($configPath) -match 'REPLACE_WITH_') { throw 'Fill in the production configuration before startup.' }
        $configArgument = ' "--spring.config.additional-location=' + ([Uri]$configPath).AbsoluteUri + '"'
    } elseif ($ConfigFile) { throw 'ConfigFile is only supported with -Profile prod.' }
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
        $arguments = '-jar "' + $jar + '" --spring.profiles.active=' + $Profile + $configArgument + ' --logging.level.root=INFO --logging.level.com.yf=INFO'
        $child = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory (Join-Path $script:BackendRoot 'yf-bev2-api') -WindowStyle Hidden -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru
        $handle = $child.Handle
    } finally {
        foreach ($name in $previous.Keys) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    }
    @{pid=$child.Id; jar=$jar; profile=$Profile; configFile=$configPath; log=$stdout; stderr=$stderr; startedAt=$stamp} | ConvertTo-Json | Set-Content -LiteralPath $script:BackendRecord -Encoding UTF8
    $clock = [Diagnostics.Stopwatch]::StartNew()
    $lastSize = -1; $lastProgress = 0
    while ($clock.Elapsed.TotalSeconds -lt 90 -and -not $child.HasExited) {
        try {
            $reply = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8080/api/sys/config/detail' -Method Post -ContentType 'application/json' -Body '{}' -TimeoutSec 2
            if ($reply.StatusCode -eq 200 -and ($reply.Content | ConvertFrom-Json).code -eq 0) {
                @{jar=$jar; profile=$Profile; configFile=$configPath} | ConvertTo-Json | Set-Content -LiteralPath $launchRecordPath -Encoding UTF8
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
