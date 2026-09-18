[CmdletBinding()]
param()
. (Join-Path $PSScriptRoot 'scripts/ops/Backend.Common.ps1')
$backendProcess = Get-ProjectBackend
if (-not $backendProcess) { Write-Host 'Project backend is not running.'; exit 0 }
Stop-ProjectBackend -BackendProcess $backendProcess
