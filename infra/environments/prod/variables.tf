variable "aws_region" {
  type    = string
  default = "ap-south-1"
}

variable "aws_profile" {
  type    = string
  default = "stopforfuel-prod"
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

# Cognito (existing)
variable "cognito_user_pool_id" {
  type    = string
  default = "ap-south-1_1jBWQOLC2"
}

variable "cognito_client_id" {
  type    = string
  default = "4mgdk5ld0hfc5hglc3tvklr5li"
}

variable "cognito_domain" {
  type    = string
  default = "stopforfuel-prod.auth.ap-south-1.amazoncognito.com"
}

# ACM certificate for ALB HTTPS
variable "acm_certificate_arn" {
  type    = string
  default = "arn:aws:acm:ap-south-1:607856468014:certificate/e716a28c-5a0c-4a06-a7b9-903a478cc755"
}
