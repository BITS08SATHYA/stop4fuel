variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "aws_profile" {
  type = string
}

variable "aws_account_id" {
  type        = string
  description = "AWS account ID for ECR registry and IAM ARNs"
}

variable "project_name" {
  type    = string
  default = "stopforfuel"
}

variable "environment" {
  type    = string
  default = "dev"
}

variable "vpc_cidr" {
  type    = string
  default = "10.1.0.0/16"
}

variable "ec2_instance_type" {
  type    = string
  default = "t3.small"
}

variable "ec2_key_name" {
  type        = string
  default     = "stopforfuel-key"
  description = "Name of existing EC2 key pair"
}

variable "allowed_ssh_cidr" {
  type    = string
  default = "0.0.0.0/0"
}

# Cognito
variable "cognito_user_pool_id" {
  type = string
}

variable "cognito_client_id" {
  type = string
}

variable "cognito_domain" {
  type = string
}

variable "db_password" {
  type      = string
  sensitive = true
}
