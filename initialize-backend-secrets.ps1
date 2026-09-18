# Run in the PowerShell that already contains the original application environment.
[CmdletBinding()]
param()
. (Join-Path $PSScriptRoot 'scripts/ops/Backend.Common.ps1')
Protect-BackendDirectory
if (Test-Path -LiteralPath $script:BackendSecrets) {
    $existing = Read-BackendSecrets
    foreach ($name in @('JWT_SECRET', 'ASSIGNMENT_CODE_PEPPER')) {
        $candidate = [Environment]::GetEnvironmentVariable($name, 'Process')
        if ($candidate -and $candidate -cne $existing[$name]) { throw 'Existing store differs from this environment. Refusing to overwrite or rotate keys.' }
    }
    $existing.Clear()
    Write-Host "Encrypted secret store already valid: $script:BackendSecrets"
    exit 0
}
$store = [ordered]@{Version = 1}
foreach ($name in @('JWT_SECRET', 'ASSIGNMENT_CODE_PEPPER')) {
    $value = [Environment]::GetEnvironmentVariable($name, 'Process')
    if ([string]::IsNullOrWhiteSpace($value) -or $value.Length -lt 32) { throw "Original $name is missing or shorter than 32 characters. No file written; do not replace the original key." }
    $store[$name] = ConvertTo-SecureString -String $value -AsPlainText -Force
    $value = $null
}
$tempPath = Join-Path $script:BackendLocal ('secrets-' + [guid]::NewGuid().ToString('N') + '.tmp')
try {
    [pscustomobject]$store | Export-Clixml -LiteralPath $tempPath -Encoding UTF8
    $verified = Read-BackendSecrets -Path $tempPath
    foreach ($name in @('JWT_SECRET', 'ASSIGNMENT_CODE_PEPPER')) {
        if ($verified[$name] -cne [Environment]::GetEnvironmentVariable($name, 'Process')) { throw 'Secret round-trip check failed.' }
    }
    $verified.Clear()
    Move-Item -LiteralPath $tempPath -Destination $script:BackendSecrets
    Write-Host "Original secrets encrypted and verified: $script:BackendSecrets"
    Write-Host 'Bound to this Windows user and computer. No plaintext keys were printed or persisted.'
} finally {
    if (Test-Path -LiteralPath $tempPath) { Remove-Item -LiteralPath $tempPath }
    $store.Clear()
}
