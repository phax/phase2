#!/usr/bin/env pwsh
# Deploy phase2-demo-spring-boot to Azure Container Apps (PowerShell 7+).
#
# Prereqs (run once yourself):
#   az login
#   az account set --subscription <sub-id>
#   az extension add --name containerapp --upgrade
#   az provider register -n Microsoft.App
#   az provider register -n Microsoft.OperationalInsights
#
# Usage:
#   pwsh ./deploy/azure-deploy.ps1                  # auto-named (uses random prefix)
#   pwsh ./deploy/azure-deploy.ps1 <tenant-id>      # named per-tenant, e.g. acme -> phase2-acme

param(
    [Parameter(Position = 0)]
    [string]$TenantId
)

$ErrorActionPreference = 'Stop'

function Assert-Az {
    param([string]$What)
    if ($LASTEXITCODE -ne 0) { throw "az failed: $What" }
}

# ---- configure these ----
$LOC       = 'canadacentral'
$RG        = 'FGF-EDI-SANDBOX'
$PREFIX    = "phase2$(Get-Random -Maximum 99999)"
$ACR       = "${PREFIX}acr"
$SA        = "${PREFIX}sa"
$CAE       = "$PREFIX-env"
$IMAGE_TAG = 'v1'

# Tenant id: optional first arg. Drives the Container App name and the
# per-tenant state file. When omitted, the app name uses the random prefix
# so repeat runs don't collide on the previous deployment's name.
if ([string]::IsNullOrWhiteSpace($TenantId)) {
    $APP       = "$PREFIX-as2"
    $TENANT_ID = $PREFIX
} else {
    $APP       = "phase2-$TenantId"
    $TENANT_ID = $TenantId
}
# -------------------------

$IMAGE     = "$ACR.azurecr.io/phase2-demo-spring-boot:$IMAGE_TAG"
$REPO_ROOT = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

Write-Host '>>> Resource group'
az group create -n $RG -l $LOC | Out-Null; Assert-Az 'group create'

Write-Host '>>> ACR'
az acr create -g $RG -n $ACR --sku Basic --admin-enabled true | Out-Null; Assert-Az 'acr create'

Write-Host '>>> Build image in ACR (uploads the repo, builds remotely)'
az acr build -r $ACR `
    -f (Join-Path $REPO_ROOT 'phase2-demo-spring-boot/Dockerfile') `
    -t "phase2-demo-spring-boot:$IMAGE_TAG" `
    $REPO_ROOT
Assert-Az 'acr build'

Write-Host '>>> Storage account + file shares'
az storage account create -g $RG -n $SA -l $LOC --sku Standard_LRS --kind StorageV2 | Out-Null
Assert-Az 'storage account create'
$SA_KEY = az storage account keys list -g $RG -n $SA --query '[0].value' -o tsv
Assert-Az 'storage account keys list'
az storage share-rm create -g $RG --storage-account $SA -n config --quota 1 | Out-Null
Assert-Az 'share-rm create config'
az storage share-rm create -g $RG --storage-account $SA -n data   --quota 10 | Out-Null
Assert-Az 'share-rm create data'

Write-Host '>>> Seed config share with local phase2-demo-spring-boot/config/'
Get-ChildItem (Join-Path $REPO_ROOT 'phase2-demo-spring-boot/config') -File | ForEach-Object {
    az storage file upload --account-name $SA --account-key $SA_KEY `
        --share-name config --source $_.FullName | Out-Null
    Assert-Az "upload $($_.Name)"
}

Write-Host '>>> Container Apps environment'
az containerapp env create -g $RG -n $CAE -l $LOC | Out-Null; Assert-Az 'containerapp env create'

Write-Host '>>> Register the two file shares with the environment'
az containerapp env storage set -g $RG -n $CAE --storage-name config-share `
    --azure-file-account-name $SA --azure-file-account-key $SA_KEY `
    --azure-file-share-name config --access-mode ReadWrite | Out-Null
Assert-Az 'env storage set config'
az containerapp env storage set -g $RG -n $CAE --storage-name data-share `
    --azure-file-account-name $SA --azure-file-account-key $SA_KEY `
    --azure-file-share-name data --access-mode ReadWrite | Out-Null
Assert-Az 'env storage set data'

$ACR_USER = az acr credential show -n $ACR --query username -o tsv;        Assert-Az 'acr cred user'
$ACR_PASS = az acr credential show -n $ACR --query 'passwords[0].value' -o tsv; Assert-Az 'acr cred pass'

Write-Host '>>> Create the container app (ingress on 443, target 8080)'
az containerapp create -g $RG -n $APP --environment $CAE `
    --image $IMAGE `
    --registry-server "$ACR.azurecr.io" `
    --registry-username $ACR_USER --registry-password $ACR_PASS `
    --ingress external --target-port 8080 --transport http `
    --min-replicas 1 --max-replicas 1 `
    --cpu 0.25 --memory 1.0Gi | Out-Null
Assert-Az 'containerapp create'

Write-Host '>>> Attach volumes via JSON patch (valid YAML for --yaml)'
$tmp = [System.IO.Path]::GetTempFileName()
az containerapp show -g $RG -n $APP -o json > $tmp; Assert-Az 'containerapp show'
$d    = Get-Content $tmp -Raw | ConvertFrom-Json -Depth 100
$tmpl = $d.properties.template
$tmpl | Add-Member -NotePropertyName volumes -NotePropertyValue @(
    [pscustomobject]@{ name='config'; storageType='AzureFile'; storageName='config-share' },
    [pscustomobject]@{ name='data';   storageType='AzureFile'; storageName='data-share' }
) -Force
$tmpl.containers[0] | Add-Member -NotePropertyName volumeMounts -NotePropertyValue @(
    [pscustomobject]@{ volumeName='config'; mountPath='/app/config' },
    [pscustomobject]@{ volumeName='data';   mountPath='/app/data' }
) -Force
$d | ConvertTo-Json -Depth 100 | Set-Content $tmp -Encoding utf8
az containerapp update -g $RG -n $APP --yaml $tmp | Out-Null; Assert-Az 'containerapp update'
Remove-Item $tmp -Force -ErrorAction SilentlyContinue

$FQDN = az containerapp show -g $RG -n $APP --query properties.configuration.ingress.fqdn -o tsv
Assert-Az 'get fqdn'

$PER_TENANT_STATE_FILE = Join-Path $PSScriptRoot ".last-deploy-$TENANT_ID.env"
$LATEST_STATE_FILE     = Join-Path $PSScriptRoot '.last-deploy.env'
$ts = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
$lines = @(
    "# Written by deploy/azure-deploy.ps1 on $ts",
    "TENANT_ID=`"$TENANT_ID`"",
    "RG=`"$RG`"",
    "LOC=`"$LOC`"",
    "APP=`"$APP`"",
    "CAE=`"$CAE`"",
    "SA=`"$SA`"",
    "ACR=`"$ACR`"",
    "FQDN=`"$FQDN`""
)
# Write LF line endings so the bash teardown can also source this file.
$content = ($lines -join "`n") + "`n"
[System.IO.File]::WriteAllText($PER_TENANT_STATE_FILE, $content)
# Also overwrite the legacy single-deploy state file so existing teardown
# scripts that read .last-deploy.env keep working without args.
[System.IO.File]::WriteAllText($LATEST_STATE_FILE, $content)
Write-Host ">>> Wrote state files: $PER_TENANT_STATE_FILE (tenant) and $LATEST_STATE_FILE (latest)"

Write-Host ''
Write-Host 'DONE'
Write-Host "  AS2 receive URL:  https://$FQDN/as2"
Write-Host "  MDN receive URL:  https://$FQDN/as2mdn"
Write-Host ''
Write-Host "Next: upload your real certs.p12 + partnerships.xml to file share 'config'"
Write-Host 'and restart the app:'
Write-Host "  az storage file upload --account-name $SA --share-name config --source <file>"
Write-Host "  az containerapp revision restart -g $RG -n $APP --revision (az containerapp revision list -g $RG -n $APP --query '[0].name' -o tsv)"
