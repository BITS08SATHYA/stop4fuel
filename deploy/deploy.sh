#!/bin/bash
set -e

REGION="us-east-1"
ACCOUNT="971422715802"
ECR_BASE="$ACCOUNT.dkr.ecr.$REGION.amazonaws.com"

echo "==> Authenticating Docker with ECR..."
aws ecr get-login-password --region $REGION | docker login --username AWS --password-stdin $ECR_BASE

echo "==> Pulling latest images..."
docker pull $ECR_BASE/stopforfuel-backend:latest
docker pull $ECR_BASE/stopforfuel-frontend:latest

echo "==> Starting services..."
docker compose -f /home/ubuntu/docker-compose.prod.yml up -d

echo "==> Done! Services:"
echo "    Frontend: http://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4):3000"
echo "    Backend:  http://$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4):8080"
