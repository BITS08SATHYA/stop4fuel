#!/usr/bin/env bash
# One-time import of the 3 tank dip charts into a running StopForFuel backend
# via the authenticated /api/dip-charts/import endpoint.
#
# Usage:
#   ./scripts/import-dip-charts.sh <COGNITO_ACCESS_TOKEN>
#   DIP_TOKEN=<token> ./scripts/import-dip-charts.sh
#
# Env overrides:
#   API_BASE  (default https://api.stopforfuel.com/api)
#   CSV_DIR   (default /home/sathipa/Documents/Bunk_DB/dumps/Dip_Charts)
set -euo pipefail

TOKEN="${1:-${DIP_TOKEN:-}}"
API="${API_BASE:-https://api.stopforfuel.com/api}"
CSV_DIR="${CSV_DIR:-/home/sathipa/Documents/Bunk_DB/dumps/Dip_Charts}"

if [ -z "$TOKEN" ]; then
    echo "ERROR: provide the Cognito access token as arg 1 or DIP_TOKEN env." >&2
    exit 1
fi
command -v jq >/dev/null || { echo "ERROR: jq is required." >&2; exit 1; }

auth=(-H "Authorization: Bearer $TOKEN")

echo "==> Resolving tanks from $API/tanks"
tanks="$(curl -fsS "${auth[@]}" "$API/tanks")" || { echo "Failed to list tanks (bad/expired token?)." >&2; exit 1; }

tank_id() { echo "$tanks" | jq -r --arg n "$1" '.[] | select(.name==$n) | .id' | head -1; }
tank_product() { echo "$tanks" | jq -r --arg n "$1" '.[] | select(.name==$n) | .product.name' | head -1; }

T_DIESEL="$(tank_id "Tank-3")"
T_PETROL="$(tank_id "Tank-4")"
T_XP="$(tank_id "Tank-5")"

echo "    Tank-3 id=$T_DIESEL ($(tank_product "Tank-3"))  <- hsd.csv"
echo "    Tank-4 id=$T_PETROL ($(tank_product "Tank-4"))  <- ms.csv"
echo "    Tank-5 id=$T_XP ($(tank_product "Tank-5"))  <- xp.csv"
for v in "$T_DIESEL" "$T_PETROL" "$T_XP"; do
    [ -n "$v" ] || { echo "ERROR: could not resolve one of Tank-3/4/5 by name." >&2; exit 1; }
done

import() { # tankId type volumeCol file
    local tid="$1" type="$2" vc="$3" file="$4"
    [ -f "$file" ] || { echo "ERROR: missing $file" >&2; exit 1; }
    local url="$API/dip-charts/import?tankId=$tid&type=$type"
    [ -n "$vc" ] && url="$url&volumeCol=$vc"
    echo ""
    echo "==> Importing $(basename "$file")  ->  tank $tid  ($type ${vc:-grid})"
    curl -fsS "${auth[@]}" -F "file=@$file" "$url" \
        | jq '{tank: .tankName, product: .productName, points: .pointCount, maxDipCm: (.maxDipMm/10), glitchesRepaired: .glitchesRepaired}'
}

import "$T_DIESEL" PER_CM Dip_Volume "$CSV_DIR/hsd.csv"
import "$T_PETROL" PER_CM Dip_Stock  "$CSV_DIR/ms.csv"
import "$T_XP"     GRID   ""         "$CSV_DIR/xp.csv"

echo ""
echo "==> Verifying dip -> stock conversion"
verify() { # tankId dip expected
    local got
    got="$(curl -fsS "${auth[@]}" "$API/dip-charts/convert?tankId=$1&dip=$2" | jq -r '.volume')"
    printf "    tank %s  dip %-5s ->  %s L   (expected ~%s)\n" "$1" "$2" "$got" "$3"
}
verify "$T_DIESEL" 100  9760.78
verify "$T_PETROL" 100  8415.78
verify "$T_XP"     50.3 3327.288

echo ""
echo "Done."
