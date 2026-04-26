#!/usr/bin/env bash
# Deploy phase2-demo-spring-boot to Azure Container Apps.
#
# Prereqs (run once yourself):
#   az login
#   az account set --subscription <sub-id>
#   az extension add --name containerapp --upgrade
#   az provider register -n Microsoft.App
#   az provider register -n Microsoft.OperationalInsights
#
# Usage:
#   bash deploy/azure-deploy.sh                  # auto-named (uses random prefix)
#   bash deploy/azure-deploy.sh <tenant-id>      # named per-tenant, e.g. "acme" -> phase2-acme
#
# What it provisions:
#   - Resource group (created if missing)
#   - ACR (builds the image in Azure, no local Docker needed)
#   - Storage account + two file shares (config, data) with the local config/
#     uploaded once so the app starts. Replace those files before production.
#   - Container Apps environment + a single container app with 443 ingress
#     and the two file shares mounted at /app/config and /app/data.
#
# Result: https://<app-fqdn>/as2  and  https://<app-fqdn>/as2mdn

set -euo pipefail

# ---- configure these ----
LOC="canadacentral"
RG="FGF-EDI-SANDBOX"
PREFIX="phase2$RANDOM"          # unique-ish; used for ACR + storage names
ACR="${PREFIX}acr"
SA="${PREFIX}sa"
CAE="${PREFIX}-env"
IMAGE_TAG="v1"

# Tenant id: optional positional arg. Drives the Container App name and the
# per-tenant state file. When omitted, the app name uses the random prefix so
# repeat runs don't collide on the previous deployment's "phase2-as2".
TENANT_ID="${1:-}"
if [[ -z "$TENANT_ID" ]]; then
  APP="${PREFIX}-as2"
  TENANT_ID="${PREFIX}"
else
  APP="phase2-${TENANT_ID}"
fi
# -------------------------

IMAGE="${ACR}.azurecr.io/phase2-demo-spring-boot:${IMAGE_TAG}"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo ">>> Resource group"
az group create -n "$RG" -l "$LOC" >/dev/null

echo ">>> ACR"
az acr create -g "$RG" -n "$ACR" --sku Basic --admin-enabled true >/dev/null

echo ">>> Build image in ACR (uploads the repo, builds remotely)"
az acr build -r "$ACR" \
  -f "$REPO_ROOT/phase2-demo-spring-boot/Dockerfile" \
  -t "phase2-demo-spring-boot:${IMAGE_TAG}" \
  "$REPO_ROOT"

echo ">>> Storage account + file shares"
az storage account create -g "$RG" -n "$SA" -l "$LOC" --sku Standard_LRS --kind StorageV2 >/dev/null
SA_KEY=$(az storage account keys list -g "$RG" -n "$SA" --query "[0].value" -o tsv)
az storage share-rm create -g "$RG" --storage-account "$SA" -n config --quota 1 >/dev/null
az storage share-rm create -g "$RG" --storage-account "$SA" -n data   --quota 10 >/dev/null

echo ">>> Seed config share with local phase2-demo-spring-boot/config/"
for f in "$REPO_ROOT"/phase2-demo-spring-boot/config/*; do
  az storage file upload --account-name "$SA" --account-key "$SA_KEY" \
    --share-name config --source "$f" >/dev/null
done

echo ">>> Container Apps environment"
az containerapp env create -g "$RG" -n "$CAE" -l "$LOC" >/dev/null

echo ">>> Register the two file shares with the environment"
az containerapp env storage set -g "$RG" -n "$CAE" --storage-name config-share \
  --azure-file-account-name "$SA" --azure-file-account-key "$SA_KEY" \
  --azure-file-share-name config --access-mode ReadWrite >/dev/null
az containerapp env storage set -g "$RG" -n "$CAE" --storage-name data-share \
  --azure-file-account-name "$SA" --azure-file-account-key "$SA_KEY" \
  --azure-file-share-name data --access-mode ReadWrite >/dev/null

ACR_USER=$(az acr credential show -n "$ACR" --query username -o tsv)
ACR_PASS=$(az acr credential show -n "$ACR" --query "passwords[0].value" -o tsv)

echo ">>> Create the container app (ingress on 443, target 8080)"
# Sizing: 0.25 vCPU is enough for AS2's bursty crypto workload; 1.0 GiB memory
# is required because phase2's classpath (BouncyCastle FIPS + ph-commons stack
# + dual logback/Log4j2) peaks at ~400-450 MB during JVM startup + cert-factory
# init. Smaller memory has caused OOM during the 5-min cert-factory reload.
az containerapp create -g "$RG" -n "$APP" --environment "$CAE" \
  --image "$IMAGE" \
  --registry-server "${ACR}.azurecr.io" \
  --registry-username "$ACR_USER" --registry-password "$ACR_PASS" \
  --ingress external --target-port 8080 --transport http \
  --min-replicas 1 --max-replicas 1 \
  --cpu 0.25 --memory 1.0Gi >/dev/null

echo ">>> Attach volumes via YAML patch (the CLI has no one-shot flag for this)"
TMP=$(mktemp)
az containerapp show -g "$RG" -n "$APP" -o yaml > "$TMP"
python - "$TMP" <<'PY'
import sys, yaml
p = sys.argv[1]
d = yaml.safe_load(open(p))
tmpl = d["properties"]["template"]
tmpl["volumes"] = [
  {"name":"config","storageType":"AzureFile","storageName":"config-share"},
  {"name":"data","storageType":"AzureFile","storageName":"data-share"},
]
tmpl["containers"][0]["volumeMounts"] = [
  {"volumeName":"config","mountPath":"/app/config"},
  {"volumeName":"data","mountPath":"/app/data"},
]
yaml.safe_dump(d, open(p,"w"))
PY
az containerapp update -g "$RG" -n "$APP" --yaml "$TMP" >/dev/null
rm -f "$TMP"

FQDN=$(az containerapp show -g "$RG" -n "$APP" --query properties.configuration.ingress.fqdn -o tsv)

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"
PER_TENANT_STATE_FILE="${DEPLOY_DIR}/.last-deploy-${TENANT_ID}.env"
LATEST_STATE_FILE="${DEPLOY_DIR}/.last-deploy.env"

cat > "$PER_TENANT_STATE_FILE" <<EOF
# Written by deploy/azure-deploy.sh on $(date -u +"%Y-%m-%dT%H:%M:%SZ")
TENANT_ID="$TENANT_ID"
RG="$RG"
LOC="$LOC"
APP="$APP"
CAE="$CAE"
SA="$SA"
ACR="$ACR"
FQDN="$FQDN"
EOF
# Also overwrite the legacy single-deploy state file so existing teardown
# scripts that read .last-deploy.env keep working without args.
cp "$PER_TENANT_STATE_FILE" "$LATEST_STATE_FILE"
echo ">>> Wrote state files: $PER_TENANT_STATE_FILE (tenant) and $LATEST_STATE_FILE (latest)"

echo ""
echo "DONE"
echo "  AS2 receive URL:  https://${FQDN}/as2"
echo "  MDN receive URL:  https://${FQDN}/as2mdn"
echo ""
echo "Next: upload your real certs.p12 + partnerships.xml to file share 'config'"
echo "and restart the app:"
echo "  az storage file upload --account-name $SA --share-name config --source <file>"
echo "  az containerapp revision restart -g $RG -n $APP --revision \$(az containerapp revision list -g $RG -n $APP --query '[0].name' -o tsv)"
