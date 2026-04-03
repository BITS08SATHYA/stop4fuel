#!/bin/bash
set -euo pipefail

# ============================================================
# Tear down dev EC2 (keeps VPC, SG, S3, ECR, SSM — all free)
# Usage: ./infra-down.sh [dev]
# ============================================================

ENV="${1:-dev}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_DIR="$SCRIPT_DIR/../environments/$ENV"

if [ ! -d "$ENV_DIR" ]; then
  echo "Error: Environment '$ENV' not found at $ENV_DIR"
  exit 1
fi

cd "$ENV_DIR"

echo "▶ This will destroy the EC2 instance and release the Elastic IP."
echo "  VPC, security groups, S3, ECR, and SSM parameters will be kept (they're free)."
echo ""

read -p "Proceed? (y/n): " CONFIRM
if [ "$CONFIRM" != "y" ]; then
  echo "Aborted."
  exit 0
fi

echo ""
echo "▶ Destroying EC2 instance ($ENV)..."
terraform destroy -target=module.ec2 -auto-approve

echo ""
echo "============================================================"
echo "✓ EC2 destroyed. Costs reduced to near zero."
echo "  Run ./infra-up.sh $ENV to recreate."
echo "============================================================"
