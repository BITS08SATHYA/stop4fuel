#!/bin/bash
set -euo pipefail

# Log output for debugging
exec > >(tee /var/log/user-data.log) 2>&1
echo "Starting user_data script at $(date)"

# Update system
apt-get update -y
apt-get upgrade -y

# Install Docker
apt-get install -y apt-transport-https ca-certificates curl software-properties-common
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg
echo "deb [arch=amd64 signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list
apt-get update -y
apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Add ubuntu user to docker group
usermod -aG docker ubuntu

# Install AWS CLI v2
curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "/tmp/awscliv2.zip"
apt-get install -y unzip
unzip -q /tmp/awscliv2.zip -d /tmp
/tmp/aws/install
rm -rf /tmp/aws /tmp/awscliv2.zip

# Fetch config from SSM Parameter Store and write .env file
REGION="${aws_region}"

fetch_param() {
  aws ssm get-parameter --region "$REGION" --name "$1" --query "Parameter.Value" --output text 2>/dev/null || echo ""
}

fetch_secret() {
  aws secretsmanager get-secret-value --region "$REGION" --secret-id "$1" --query "SecretString" --output text 2>/dev/null || echo "{}"
}

# Fetch all config
COGNITO_USER_POOL_ID=$(fetch_param "/stopforfuel/cognito/user-pool-id")
COGNITO_CLIENT_ID=$(fetch_param "/stopforfuel/cognito/client-id")
COGNITO_ISSUER_URI=$(fetch_param "/stopforfuel/cognito/issuer-uri")
COGNITO_JWK_URI=$(fetch_param "/stopforfuel/cognito/jwk-uri")
CORS_ALLOWED_ORIGINS=$(fetch_param "/stopforfuel/cors-origins")
AWS_REGION_VAL=$(fetch_param "/stopforfuel/region")

# Fetch DB password from Secrets Manager
DB_CREDS=$(fetch_secret "stopforfuel/db-credentials")
DB_PASSWORD=$(echo "$DB_CREDS" | python3 -c "import sys, json; print(json.loads(sys.stdin.read()).get('password', 'password'))")

# Write .env file
cat > /home/ubuntu/.env << ENVEOF
DB_PASSWORD=$DB_PASSWORD
COGNITO_USER_POOL_ID=$COGNITO_USER_POOL_ID
COGNITO_CLIENT_ID=$COGNITO_CLIENT_ID
COGNITO_ISSUER_URI=$COGNITO_ISSUER_URI
COGNITO_JWK_URI=$COGNITO_JWK_URI
AWS_REGION=$AWS_REGION_VAL
CORS_ALLOWED_ORIGINS=$CORS_ALLOWED_ORIGINS
AUTH_ENABLED=true
ENVEOF
chown ubuntu:ubuntu /home/ubuntu/.env
chmod 600 /home/ubuntu/.env

# ECR login and pull images
ECR_REGISTRY="${ecr_registry}"
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$ECR_REGISTRY"

docker pull "$ECR_REGISTRY/stopforfuel-backend:latest" || true
docker pull "$ECR_REGISTRY/stopforfuel-frontend:latest" || true

# Copy docker-compose file (will be uploaded by deploy workflow or SCP)
# For now, create a placeholder that will be replaced
echo "user_data script completed at $(date)"
