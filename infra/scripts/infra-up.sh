#!/bin/bash
set -euo pipefail

# ============================================================
# Spin up dev infrastructure (EC2 + EIP)
# Usage: ./infra-up.sh [dev]
# ============================================================

ENV="${1:-dev}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_DIR="$SCRIPT_DIR/../environments/$ENV"

if [ ! -d "$ENV_DIR" ]; then
  echo "Error: Environment '$ENV' not found at $ENV_DIR"
  exit 1
fi

cd "$ENV_DIR"

echo "▶ Initializing Terraform ($ENV)..."
terraform init

echo ""
echo "▶ Planning infrastructure ($ENV)..."
terraform plan -target=module.ec2

echo ""
read -p "Apply? (y/n): " CONFIRM
if [ "$CONFIRM" != "y" ]; then
  echo "Aborted."
  exit 0
fi

echo ""
echo "▶ Creating EC2 instance..."
terraform apply -target=module.ec2 -auto-approve

# Get the Elastic IP
EIP=$(terraform output -raw ec2_elastic_ip 2>/dev/null || echo "unknown")

echo ""
echo "============================================================"
echo "✓ Dev environment is up!"
echo ""
echo "  Frontend: http://$EIP:3000"
echo "  Backend:  http://$EIP:8080/api"
echo "  SSH:      ssh -i ~/Aws_Keys/stopforfuel-key.pem ubuntu@$EIP"
echo ""
echo "  Update GitHub Environment 'dev' variable EC2_HOST = $EIP"
echo "============================================================"
