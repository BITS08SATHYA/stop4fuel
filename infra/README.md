# StopForFuel Infrastructure (Terraform)

## Architecture

| | Dev | Production |
|--|-----|------------|
| **Region** | us-east-1 | ap-south-1 |
| **Compute** | EC2 + docker-compose | ECS Fargate |
| **Database** | PostgreSQL in Docker | RDS PostgreSQL |
| **Load Balancer** | None (direct IP) | ALB with HTTPS |
| **Config** | SSM Parameter Store | SSM Parameter Store |
| **Secrets** | Secrets Manager | Secrets Manager |

## Quick Start

### 1. Create State Buckets (one-time)
```bash
./infra/scripts/setup-state.sh
```

### 2. Deploy Dev Environment
```bash
cd infra/environments/dev
cp terraform.tfvars.example terraform.tfvars  # Fill in your values
terraform init
terraform apply -var="db_password=YOUR_PASSWORD"
```

### 3. Spin Up / Down Dev (save costs)
```bash
# Start dev (creates EC2)
./infra/scripts/infra-up.sh dev

# Stop dev (destroys EC2, keeps VPC/S3/SSM — all free)
./infra/scripts/infra-down.sh dev
```

### 4. Import Prod Resources (one-time)
```bash
cd infra/environments/prod
terraform init
# Run import commands (see internal docs for resource IDs)
terraform plan  # Should show zero changes
```

## GitHub Secrets Required

| Secret | Description |
|--------|-------------|
| `DEV_AWS_ACCESS_KEY_ID` | IAM key for dev account |
| `DEV_AWS_SECRET_ACCESS_KEY` | IAM secret for dev account |
| `PROD_AWS_ACCESS_KEY_ID` | IAM key for prod account |
| `PROD_AWS_SECRET_ACCESS_KEY` | IAM secret for prod account |
| `EC2_SSH_KEY` | SSH private key for dev EC2 access |

All other config (Cognito, API URLs, ECR registry, etc.) is stored in AWS SSM Parameter Store and fetched during deployment.

## CI/CD Flow

```
Push to dev  → GitHub Actions → Fetch SSM config → Build images → Push to ECR → SSH deploy to EC2
Push to main → GitHub Actions → Fetch SSM config → Build images → Push to ECR → ECS update-service
```
