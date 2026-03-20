#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# StopForFuel — Full Deploy Script
# Usage: ./deploy.sh [backend|frontend|all]
# Default: all
# ============================================================

TARGET="${1:-all}"

# --- Config ---
AWS_PROFILE="sathya-nyu"
AWS_REGION="us-east-1"
AWS_ACCOUNT="REDACTED"
ECR_BASE="${AWS_ACCOUNT}.dkr.ecr.${AWS_REGION}.amazonaws.com"

EC2_IP="100.52.228.13"
EC2_USER="ubuntu"
PEM_KEY="/home/sathipa/Aws_Keys/stopforfuel-key.pem"
SSH_CMD="ssh -i ${PEM_KEY} -o StrictHostKeyChecking=no ${EC2_USER}@${EC2_IP}"

BACKEND_IMAGE="${ECR_BASE}/stopforfuel-backend:latest"
FRONTEND_IMAGE="${ECR_BASE}/stopforfuel-frontend:latest"
COMPOSE_FILE="/home/ubuntu/docker-compose.prod.yml"

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

# --- Colors ---
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
NC='\033[0m'

step() { echo -e "\n${CYAN}▶ $1${NC}"; }
ok()   { echo -e "${GREEN}✓ $1${NC}"; }
warn() { echo -e "${YELLOW}⚠ $1${NC}"; }

# ============================================================
# Step 1: ECR Login (local)
# ============================================================
step "Logging into ECR (local)..."
aws ecr get-login-password --region ${AWS_REGION} --profile ${AWS_PROFILE} \
  | docker login --username AWS --password-stdin ${ECR_BASE}
ok "ECR login successful"

# ============================================================
# Step 2: Build & Push Backend
# ============================================================
build_backend() {
    step "Building backend Docker image..."
    docker build -t ${BACKEND_IMAGE} "${PROJECT_DIR}/backend"
    ok "Backend image built"

    step "Pushing backend image to ECR..."
    docker push ${BACKEND_IMAGE}
    ok "Backend image pushed"
}

# ============================================================
# Step 3: Build & Push Frontend
# ============================================================
build_frontend() {
    step "Building frontend Docker image..."
    docker build -t ${FRONTEND_IMAGE} "${PROJECT_DIR}/frontend"
    ok "Frontend image built"

    step "Pushing frontend image to ECR..."
    docker push ${FRONTEND_IMAGE}
    ok "Frontend image pushed"
}

# ============================================================
# Step 4: Deploy on EC2
# ============================================================
deploy_ec2() {
    step "Deploying on EC2 (${EC2_IP})..."

    ${SSH_CMD} << 'REMOTE_EOF'
set -e
ECR_BASE="REDACTED.dkr.ecr.us-east-1.amazonaws.com"
COMPOSE_FILE="/home/ubuntu/docker-compose.prod.yml"

echo "  → ECR login on EC2..."
aws ecr get-login-password --region us-east-1 \
  | docker login --username AWS --password-stdin ${ECR_BASE}

echo "  → Pulling latest images..."
docker pull ${ECR_BASE}/stopforfuel-backend:latest
docker pull ${ECR_BASE}/stopforfuel-frontend:latest

echo "  → Restarting services..."
docker compose -f ${COMPOSE_FILE} up -d

echo "  → Cleaning old images..."
docker image prune -f

echo "  → Done! Running containers:"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
REMOTE_EOF

    ok "EC2 deployment complete"
}

# ============================================================
# Run based on target
# ============================================================
case "${TARGET}" in
    backend)
        build_backend
        deploy_ec2
        ;;
    frontend)
        build_frontend
        deploy_ec2
        ;;
    all)
        build_backend
        build_frontend
        deploy_ec2
        ;;
    *)
        echo "Usage: ./deploy.sh [backend|frontend|all]"
        exit 1
        ;;
esac

echo ""
ok "🚀 Deployment finished! App: http://${EC2_IP}:3000"
