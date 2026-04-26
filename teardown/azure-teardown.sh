#!/usr/bin/env bash
# Tear down the phase2 AS2 resources created by deploy/azure-deploy.sh.
#
# IMPORTANT: the RG (FGF-EDI-SANDBOX) is a shared sandbox, so this script does
# NOT delete the resource group. It deletes only the four resources the deploy
# script created, by explicit name.
#
# How to find the names: the deploy script generated them with a random suffix
# (PREFIX="phase2$RANDOM"). Find them with:
#   az resource list -g FGF-EDI-SANDBOX -o table | grep -i phase2
#
# Then fill in the vars below and run:
#   bash teardown/azure-teardown.sh
#
# Pass --yes as arg 1 to skip the confirmation prompt (useful for CI).

set -euo pipefail

# If the deploy script wrote a state file, auto-load it so you don't have to
# paste names. Inline values below override (edit if you need to target a
# specific older deploy).
STATE_FILE="$(cd "$(dirname "$0")" && pwd)/../deploy/.last-deploy.env"
if [[ -f "$STATE_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$STATE_FILE"
  echo "Loaded state from $STATE_FILE"
fi

# ---- override / fill in if not auto-loaded ----
RG="${RG:-FGF-EDI-SANDBOX}"
APP="${APP:-phase2-as2}"
CAE="${CAE:-}"
SA="${SA:-}"
ACR="${ACR:-}"
# -----------------------------------------------

AUTO_YES="${1:-}"

missing=()
[[ -z "$CAE" ]] && missing+=("CAE")
[[ -z "$SA" ]]  && missing+=("SA")
[[ -z "$ACR" ]] && missing+=("ACR")
if (( ${#missing[@]} > 0 )); then
  echo "Set these variables at the top of the script first: ${missing[*]}"
  echo ""
  echo "Candidates in $RG:"
  az resource list -g "$RG" --query "[?starts_with(name, 'phase2')].{name:name, type:type}" -o table || true
  exit 1
fi

echo "About to delete from RG '$RG':"
echo "  Container App:               $APP"
echo "  Container Apps Environment:  $CAE"
echo "  Storage Account:             $SA  (Azure Files shares: config, data)"
echo "  Container Registry:          $ACR"
echo ""
echo "The resource group '$RG' itself will NOT be deleted."
echo ""

if [[ "$AUTO_YES" != "--yes" ]]; then
  read -r -p "Type 'yes' to proceed: " ans
  [[ "$ans" == "yes" ]] || { echo "Aborted."; exit 1; }
fi

# Order matters: app depends on env + ACR; env owns the storage registrations;
# storage account and ACR are independent afterwards.

echo ">>> Deleting container app"
az containerapp delete -g "$RG" -n "$APP" --yes >/dev/null 2>&1 || echo "   (not found, skipping)"

echo ">>> Deleting container apps environment"
az containerapp env delete -g "$RG" -n "$CAE" --yes >/dev/null 2>&1 || echo "   (not found, skipping)"

echo ">>> Deleting storage account (drops config + data file shares)"
az storage account delete -g "$RG" -n "$SA" --yes >/dev/null 2>&1 || echo "   (not found, skipping)"

echo ">>> Deleting container registry"
az acr delete -g "$RG" -n "$ACR" --yes >/dev/null 2>&1 || echo "   (not found, skipping)"

if [[ -f "$STATE_FILE" ]]; then
  rm -f "$STATE_FILE"
  echo ">>> Removed stale state file: $STATE_FILE"
fi

echo ""
echo "DONE. Remaining phase2-prefixed resources in $RG (should be empty):"
az resource list -g "$RG" --query "[?starts_with(name, 'phase2')].{name:name, type:type}" -o table || true
