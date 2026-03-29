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

COMPOSE_FILE="/home/ubuntu/docker-compose.prod.yml"

# Frontend needs to know the backend API URL at build time
NEXT_PUBLIC_API_URL="http://${EC2_IP}:8080/api"

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

# --- Build ID (git short SHA + timestamp) ---
GIT_SHA="$(git -C "${PROJECT_DIR}" rev-parse --short HEAD)"
BUILD_TAG="${GIT_SHA}"

BACKEND_IMAGE="${ECR_BASE}/stopforfuel-backend:${BUILD_TAG}"
FRONTEND_IMAGE="${ECR_BASE}/stopforfuel-frontend:${BUILD_TAG}"

# Also tag as latest for convenience
BACKEND_LATEST="${ECR_BASE}/stopforfuel-backend:latest"
FRONTEND_LATEST="${ECR_BASE}/stopforfuel-frontend:latest"

# --- Colors ---
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

step() { echo -e "\n${CYAN}▶ $1${NC}"; }
ok()   { echo -e "${GREEN}✓ $1${NC}"; }
warn() { echo -e "${YELLOW}⚠ $1${NC}"; }
fail() { echo -e "${RED}✗ $1${NC}"; exit 1; }

# ============================================================
# Step 1: ECR Login (local)
# ============================================================
echo -e "${CYAN}Build Tag: ${BUILD_TAG} (git: ${GIT_SHA})${NC}"
echo ""

step "Logging into ECR (local)..."
aws ecr get-login-password --region ${AWS_REGION} --profile ${AWS_PROFILE} \
  | docker login --username AWS --password-stdin ${ECR_BASE}
ok "ECR login successful"

# ============================================================
# Step 2: Build & Push Backend
# ============================================================
build_backend() {
    step "Building backend JAR..."
    cd "${PROJECT_DIR}/backend"
    ./gradlew bootJar -x test
    cd "${PROJECT_DIR}"
    ok "Backend JAR built"

    step "Building backend Docker image (tag: ${BUILD_TAG})..."
    docker build -t ${BACKEND_IMAGE} -t ${BACKEND_LATEST} "${PROJECT_DIR}/backend"
    ok "Backend image built"

    step "Pushing backend image to ECR..."
    docker push ${BACKEND_IMAGE}
    docker push ${BACKEND_LATEST}
    ok "Backend image pushed (${BUILD_TAG} + latest)"
}

# ============================================================
# Step 3: Build & Push Frontend (with --build-arg for API URL)
# ============================================================
build_frontend() {
    step "Building frontend Docker image (tag: ${BUILD_TAG}, API_URL=${NEXT_PUBLIC_API_URL})..."
    docker build \
        --build-arg NEXT_PUBLIC_API_URL="${NEXT_PUBLIC_API_URL}" \
        -t ${FRONTEND_IMAGE} -t ${FRONTEND_LATEST} \
        "${PROJECT_DIR}/frontend"
    ok "Frontend image built"

    step "Pushing frontend image to ECR..."
    docker push ${FRONTEND_IMAGE}
    docker push ${FRONTEND_LATEST}
    ok "Frontend image pushed (${BUILD_TAG} + latest)"
}

# ============================================================
# Step 4: Upload docker-compose.prod.yml & Deploy on EC2
# ============================================================
deploy_ec2() {
    step "Uploading docker-compose.prod.yml to EC2..."
    scp -i ${PEM_KEY} -o StrictHostKeyChecking=no \
        "${PROJECT_DIR}/docker-compose.prod.yml" \
        ${EC2_USER}@${EC2_IP}:${COMPOSE_FILE}
    ok "Compose file uploaded"

    step "Deploying on EC2 (${EC2_IP}) with build ${BUILD_TAG}..."

    ${SSH_CMD} << REMOTE_EOF
set -e
ECR_BASE="REDACTED.dkr.ecr.us-east-1.amazonaws.com"
BUILD_TAG="${BUILD_TAG}"
COMPOSE_FILE="/home/ubuntu/docker-compose.prod.yml"

echo "  → ECR login on EC2..."
aws ecr get-login-password --region us-east-1 \
  | docker login --username AWS --password-stdin \${ECR_BASE}

echo "  → Pulling images (tag: \${BUILD_TAG})..."
docker pull \${ECR_BASE}/stopforfuel-backend:\${BUILD_TAG}
docker pull \${ECR_BASE}/stopforfuel-frontend:\${BUILD_TAG}

echo "  → Updating compose file with build tag..."
sed -i "s|stopforfuel-backend:.*|stopforfuel-backend:\${BUILD_TAG}|g" \${COMPOSE_FILE}
sed -i "s|stopforfuel-frontend:.*|stopforfuel-frontend:\${BUILD_TAG}|g" \${COMPOSE_FILE}

echo "  → Stopping old containers..."
docker compose -f \${COMPOSE_FILE} down || true

sleep 2

echo "  → Starting services..."
docker compose -f \${COMPOSE_FILE} up -d --force-recreate

echo "  → Waiting for services to start..."
sleep 5

echo "  → Cleaning old images..."
docker image prune -f

echo "  → Running containers:"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo ""
echo "  → Deployed build: \${BUILD_TAG}"
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
ok "Deployment finished! App: http://${EC2_IP}:3000"
