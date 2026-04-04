locals {
  aws_account_id = var.aws_account_id
  ecr_registry   = "${local.aws_account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"
  azs            = ["${var.aws_region}a", "${var.aws_region}b"]
}

# ============================================================
# Networking — VPC + public/private subnets + NAT + security groups
# ============================================================
module "networking" {
  source = "../../modules/networking"

  project_name           = var.project_name
  environment            = var.environment
  vpc_cidr               = var.vpc_cidr
  public_subnet_cidrs    = ["10.0.1.0/24", "10.0.2.0/24"]
  private_subnet_cidrs   = ["10.0.3.0/24", "10.0.4.0/24"]
  availability_zones     = local.azs
  enable_nat_gateway     = true
  enable_private_subnets = true
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
# ALB
# ============================================================
module "alb" {
  source = "../../modules/alb"

  project_name      = var.project_name
  environment       = var.environment
  vpc_id            = module.networking.vpc_id
  subnet_ids        = module.networking.public_subnet_ids
  security_group_id = module.networking.alb_security_group_id
  certificate_arn   = var.acm_certificate_arn
}

# ============================================================
# ECS Execution Role
# ============================================================
resource "aws_iam_role" "ecs_execution" {
  name = "${var.project_name}-ecs-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Environment = var.environment
  }
}

resource "aws_iam_role_policy_attachment" "ecs_execution_basic" {
  role       = aws_iam_role.ecs_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "ecs_execution_secrets" {
  name = "${var.project_name}-ecs-secrets-access"
  role = aws_iam_role.ecs_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
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
          "ssm:GetParameter",
          "ssm:GetParameters",
          "ssm:GetParametersByPath"
        ]
        Resource = "arn:aws:ssm:${var.aws_region}:${local.aws_account_id}:parameter/${var.project_name}/*"
      }
    ]
  })
}

# ============================================================
# Secrets Manager — DB credentials (import existing)
# ============================================================
resource "aws_secretsmanager_secret" "db_credentials" {
  name = "${var.project_name}/db-credentials"

  tags = {
    Environment = var.environment
  }

  lifecycle {
    ignore_changes = all
  }
}

# ============================================================
# ECS — Cluster + services
# ============================================================
module "ecs" {
  source = "../../modules/ecs"

  project_name              = var.project_name
  environment               = var.environment
  aws_region                = var.aws_region
  vpc_id                    = module.networking.vpc_id
  subnet_ids                = module.networking.private_subnet_ids
  security_group_id         = module.networking.ecs_security_group_id
  backend_target_group_arn  = module.alb.backend_target_group_arn
  frontend_target_group_arn = module.alb.frontend_target_group_arn
  ecr_registry              = local.ecr_registry
  execution_role_arn        = aws_iam_role.ecs_execution.arn
  db_secret_arn             = aws_secretsmanager_secret.db_credentials.arn

  backend_cpu    = 512
  backend_memory = 1024
  frontend_cpu   = 256
  frontend_memory = 512
}

# ============================================================
# RDS — PostgreSQL (import existing)
# ============================================================
module "rds" {
  source = "../../modules/rds"

  project_name           = var.project_name
  environment            = var.environment
  instance_class         = "db.t4g.micro"
  allocated_storage      = 20
  db_name                = "stopforfuel"
  db_username            = "stopforfuel_admin"
  subnet_ids             = module.networking.private_subnet_ids
  security_group_id      = module.networking.rds_security_group_id
  db_password_secret_arn = aws_secretsmanager_secret.db_credentials.arn
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
    "api-url"              = "https://api.stopforfuel.com/api"
    "landing-url"          = "https://stopforfuel.com"
    "cors-origins"         = "https://app.stopforfuel.com,https://stopforfuel.com,https://www.stopforfuel.com"
    "ecr-registry"         = local.ecr_registry
    "deploy-target"        = "ecs"
    "ecs-cluster"          = module.ecs.cluster_name
    "ecs-backend-service"  = module.ecs.backend_service_name
    "ecs-frontend-service" = module.ecs.frontend_service_name
  }
}
