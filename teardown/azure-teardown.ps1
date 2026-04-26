#!/usr/bin/env pwsh
# Tear down resources created by deploy/azure-deploy.ps1 (or .sh).
#
# IMPORTANT: the RG (FGF-EDI-SANDBOX) is a shared sandbox, so this script does
# NOT delete the resource group. It deletes only the named resources.
#
# Usage:
#   pwsh ./teardown/azure-teardown.ps1           # interactive confirmation
#   pwsh ./teardown/azure-teardown.ps1 --yes     # skip confirmation

$ErrorActionPreference = 'Stop'

# If deploy wrote a state file, auto-load it.
$STATE_FILE = Join-Path $PSScriptRoot '..\deploy\.last-deploy.env'
if (Test-Path $STATE_FILE) {
    Get-Content $STATE_FILE | ForEach-Object {
        if ($_ -match '^\s*([A-Z_]+)\s*=\s*"(.*)"\s*$') {
            Set-Variable -Name $Matches[1] -Value $Matches[2] -Scope Script
        }
    }
    Write-Host "Loaded state from $STATE_FILE"
}

# ---- override / fill in if not auto-loaded ----
if (-not $RG)  { $RG  = 'FGF-EDI-SANDBOX' }
if (-not $APP) { $APP = 'phase2-as2' }
if (-not $CAE) { $CAE = '' }
if (-not $SA)  { $SA  = '' }
if (-not $ACR) { $ACR = '' }
# ------------------------------------------------

$missing = @()
if (-not $CAE) { $missing += 'CAE' }
if (-not $SA)  { $missing += 'SA' }
if (-not $ACR) { $missing += 'ACR' }
if ($missing.Count -gt 0) {
    Write-Host "Set these variables at the top of the script first: $($missing -join ', ')"
    Write-Host ''
    Write-Host "Candidates in ${RG}:"
    az resource list -g $RG --query "[?starts_with(name, 'phase2')].{name:name, type:type}" -o table
    exit 1
}

Write-Host "About to delete from RG '$RG':"
Write-Host "  Container App:               $APP"
Write-Host "  Container Apps Environment:  $CAE"
Write-Host "  Storage Account:             $SA  (Azure Files shares: config, data)"
Write-Host "  Container Registry:          $ACR"
Write-Host ''
Write-Host "The resource group '$RG' itself will NOT be deleted."
Write-Host ''

$autoYes = ($args.Count -gt 0 -and $args[0] -eq '--yes')
if (-not $autoYes) {
    $ans = Read-Host "Type 'yes' to proceed"
    if ($ans -ne 'yes') { Write-Host 'Aborted.'; exit 1 }
}

function Remove-AzResource {
    param([string]$Label, [scriptblock]$Action)
    Write-Host ">>> $Label"
    & $Action 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Host '   (not found, skipping)' }
    $global:LASTEXITCODE = 0
}

# Order matters: app depends on env + ACR; env owns the storage registrations.
Remove-AzResource 'Deleting container app' {
    az containerapp delete -g $RG -n $APP --yes
}
Remove-AzResource 'Deleting container apps environment' {
    az containerapp env delete -g $RG -n $CAE --yes
}
Remove-AzResource 'Deleting storage account (drops config + data file shares)' {
    az storage account delete -g $RG -n $SA --yes
}
Remove-AzResource 'Deleting container registry' {
    az acr delete -g $RG -n $ACR --yes
}

if (Test-Path $STATE_FILE) {
    Remove-Item $STATE_FILE -Force
    Write-Host ">>> Removed stale state file: $STATE_FILE"
}

Write-Host ''
Write-Host "DONE. Remaining phase2-prefixed resources in ${RG} (should be empty):"
az resource list -g $RG --query "[?starts_with(name, 'phase2')].{name:name, type:type}" -o table
