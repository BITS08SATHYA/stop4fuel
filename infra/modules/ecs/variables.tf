variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "subnet_ids" {
  type = list(string)
}

variable "security_group_id" {
  type = string
}

variable "backend_target_group_arn" {
  type = string
}

variable "frontend_target_group_arn" {
  type = string
}

variable "ecr_registry" {
  type = string
}

variable "backend_cpu" {
  type    = number
  default = 512
}

variable "backend_memory" {
  type    = number
  default = 1024
}

variable "frontend_cpu" {
  type    = number
  default = 256
}

variable "frontend_memory" {
  type    = number
  default = 512
}

variable "execution_role_arn" {
  type = string
}

variable "task_role_arn" {
  type    = string
  default = ""
}

variable "db_secret_arn" {
  type = string
}

variable "anthropic_api_key_secret_arn" {
  description = "Secrets Manager ARN for ANTHROPIC_API_KEY (plain string secret)"
  type        = string
  default     = ""
}

variable "assign_public_ip" {
  description = "Assign public IP to Fargate tasks. Set true when running in public subnets without NAT."
  type        = bool
  default     = false
}
