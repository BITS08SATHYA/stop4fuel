#!/bin/bash
set -euo pipefail

# ============================================================
# One-time setup: Create S3 buckets for Terraform state
# ============================================================

echo "Creating Terraform state buckets..."

# Dev state bucket (us-east-1, ${AWS_DEV_PROFILE:-default} profile)
echo "→ Creating dev state bucket..."
aws s3api create-bucket \
  --bucket stopforfuel-terraform-state-dev \
  --region us-east-1 \
  --profile ${AWS_DEV_PROFILE:-default}

aws s3api put-bucket-versioning \
  --bucket stopforfuel-terraform-state-dev \
  --versioning-configuration Status=Enabled \
  --profile ${AWS_DEV_PROFILE:-default}

aws s3api put-bucket-encryption \
  --bucket stopforfuel-terraform-state-dev \
  --server-side-encryption-configuration '{
    "Rules": [{"ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "AES256"}}]
  }' \
  --profile ${AWS_DEV_PROFILE:-default}

aws s3api put-public-access-block \
  --bucket stopforfuel-terraform-state-dev \
  --public-access-block-configuration '{
    "BlockPublicAcls": true,
    "IgnorePublicAcls": true,
    "BlockPublicPolicy": true,
    "RestrictPublicBuckets": true
  }' \
  --profile ${AWS_DEV_PROFILE:-default}

echo "✓ Dev state bucket created"

# Prod state bucket (ap-south-1, stopforfuel-prod profile)
echo "→ Creating prod state bucket..."
aws s3api create-bucket \
  --bucket stopforfuel-terraform-state-prod \
  --region ap-south-1 \
  --create-bucket-configuration LocationConstraint=ap-south-1 \
  --profile stopforfuel-prod

aws s3api put-bucket-versioning \
  --bucket stopforfuel-terraform-state-prod \
  --versioning-configuration Status=Enabled \
  --profile stopforfuel-prod

aws s3api put-bucket-encryption \
  --bucket stopforfuel-terraform-state-prod \
  --server-side-encryption-configuration '{
    "Rules": [{"ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "AES256"}}]
  }' \
  --profile stopforfuel-prod

aws s3api put-public-access-block \
  --bucket stopforfuel-terraform-state-prod \
  --public-access-block-configuration '{
    "BlockPublicAcls": true,
    "IgnorePublicAcls": true,
    "BlockPublicPolicy": true,
    "RestrictPublicBuckets": true
  }' \
  --profile stopforfuel-prod

echo "✓ Prod state bucket created"
echo ""
echo "Done! You can now run: cd infra/environments/dev && terraform init"
