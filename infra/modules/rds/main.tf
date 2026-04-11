resource "aws_db_subnet_group" "main" {
  name        = "${var.project_name}-db-subnet"
  description = "Private subnets for RDS"
  subnet_ids  = var.subnet_ids

  tags = {
    Name        = "${var.project_name}-db-subnet"
    Environment = var.environment
  }
}

resource "aws_db_instance" "main" {
  identifier     = "${var.project_name}-db"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.instance_class

  allocated_storage = var.allocated_storage
  storage_type      = "gp3"

  db_name  = var.db_name
  username = var.db_username
  # Password managed via Secrets Manager — set manually or via rotation
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [var.security_group_id]

  publicly_accessible = false
  skip_final_snapshot  = true
  deletion_protection  = true

  enabled_cloudwatch_logs_exports = ["iam-db-auth-error", "postgresql", "upgrade"]

  backup_retention_period = 7
  backup_window           = "23:26-23:56"
  maintenance_window      = "thu:06:02-thu:06:32"

  tags = {
    Name        = "${var.project_name}-db"
    Environment = var.environment
  }

  lifecycle {
    ignore_changes = [
      engine_version,
      manage_master_user_password,
      password
    ]
  }
}
