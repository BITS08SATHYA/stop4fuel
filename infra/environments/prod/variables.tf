variable "aws_region" {
  type    = string
  default = "ap-south-1"
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
  default = "prod"
}

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
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

# ACM certificate for ALB HTTPS
variable "acm_certificate_arn" {
  type = string
}

# CloudWatch alarm notifications (optional)
variable "alarm_email" {
  type        = string
  description = "Email address to subscribe to the alarms SNS topic. Leave empty to skip — you can subscribe via the AWS console later."
  default     = ""
}
