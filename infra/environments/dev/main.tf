locals {
  aws_account_id = "REDACTED"
  ecr_registry   = "${local.aws_account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"
  azs            = ["${var.aws_region}a", "${var.aws_region}b"]
}

# ============================================================
# Networking — VPC + public subnets + security group
# ============================================================
module "networking" {
  source = "../../modules/networking"

  project_name           = var.project_name
  environment            = var.environment
  vpc_cidr               = var.vpc_cidr
  public_subnet_cidrs    = ["10.1.1.0/24", "10.1.2.0/24"]
  private_subnet_cidrs   = []
  availability_zones     = local.azs
  enable_nat_gateway     = false
  enable_private_subnets = false
  allowed_ssh_cidr       = var.allowed_ssh_cidr
}

# ============================================================
# ECR — Import existing repos
# ============================================================
module "ecr" {
  source = "../../modules/ecr"

  project_name     = var.project_name
  environment      = var.environment
  repository_names = ["backend", "frontend"]
}

# ============================================================
# IAM Role for EC2 — ECR pull + SSM read + S3 access
# ============================================================
resource "aws_iam_role" "ec2" {
  name = "${var.project_name}-${var.environment}-ec2-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Environment = var.environment
  }
}

resource "aws_iam_role_policy" "ec2_permissions" {
  name = "${var.project_name}-${var.environment}-ec2-permissions"
  role = aws_iam_role.ec2.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken",
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "ssm:GetParameter",
          "ssm:GetParameters",
          "ssm:GetParametersByPath"
        ]
        Resource = "arn:aws:ssm:${var.aws_region}:${local.aws_account_id}:parameter/${var.project_name}/*"
      },
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Resource = "arn:aws:secretsmanager:${var.aws_region}:${local.aws_account_id}:secret:${var.project_name}/*"
      },
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:ListBucket"
        ]
        Resource = [
          module.s3.bucket_arn,
          "${module.s3.bucket_arn}/*"
        ]
      }
    ]
  })
}

resource "aws_iam_instance_profile" "ec2" {
  name = "${var.project_name}-${var.environment}-ec2-profile"
  role = aws_iam_role.ec2.name
}

# ============================================================
# EC2 — Dev server with Docker + docker-compose
# ============================================================
module "ec2" {
  source = "../../modules/ec2"

  project_name         = var.project_name
  environment          = var.environment
  instance_type        = var.ec2_instance_type
  subnet_id            = module.networking.public_subnet_ids[0]
  security_group_id    = module.networking.ec2_security_group_id
  iam_instance_profile = aws_iam_instance_profile.ec2.name
  key_name             = var.ec2_key_name
  ecr_registry         = local.ecr_registry
  aws_region           = var.aws_region
}

# ============================================================
# S3 Bucket
# ============================================================
module "s3" {
  source = "../../modules/s3"

  bucket_name = "${var.project_name}-${var.environment}"
  environment = var.environment
}

# ============================================================
# Secrets Manager — DB credentials
# ============================================================
resource "aws_secretsmanager_secret" "db_credentials" {
  name = "${var.project_name}/db-credentials"

  tags = {
    Environment = var.environment
  }
}

resource "aws_secretsmanager_secret_version" "db_credentials" {
  secret_id = aws_secretsmanager_secret.db_credentials.id
  secret_string = jsonencode({
    username = "postgres"
    password = var.db_password
  })
}

# ============================================================
# SSM Parameter Store — All config
# ============================================================
module "ssm" {
  source = "../../modules/ssm"

  project_name = var.project_name
  environment  = var.environment

  parameters = {
    "cognito/user-pool-id" = var.cognito_user_pool_id
    "cognito/client-id"    = var.cognito_client_id
    "cognito/domain"       = var.cognito_domain
    "cognito/issuer-uri"   = "https://cognito-idp.${var.aws_region}.amazonaws.com/${var.cognito_user_pool_id}"
    "cognito/jwk-uri"      = "https://cognito-idp.${var.aws_region}.amazonaws.com/${var.cognito_user_pool_id}/.well-known/jwks.json"
    "region"               = var.aws_region
    "api-url"              = "http://${module.ec2.elastic_ip}:8080/api"
    "landing-url"          = "http://${module.ec2.elastic_ip}:3000"
    "cors-origins"         = "http://${module.ec2.elastic_ip}:3000"
    "ecr-registry"         = local.ecr_registry
    "deploy-target"        = "ec2"
    "ec2-host"             = module.ec2.elastic_ip
  }
}
