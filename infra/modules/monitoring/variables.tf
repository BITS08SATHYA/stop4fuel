variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "aws_region" {
  type = string
}

variable "alarm_email" {
  type        = string
  description = "Email address to receive CloudWatch alarms. Leave empty to skip SNS subscription."
  default     = ""
}

variable "ecs_cluster_name" {
  type = string
}

variable "ecs_backend_service_name" {
  type = string
}

variable "ecs_frontend_service_name" {
  type = string
}

variable "alb_arn_suffix" {
  type        = string
  description = "ALB ARN suffix (app/xxx/yyy) — used as CloudWatch dimension"
}

variable "backend_tg_arn_suffix" {
  type        = string
  description = "Backend target-group ARN suffix (targetgroup/xxx/yyy) — used as CloudWatch dimension"
}

variable "rds_instance_id" {
  type = string
}

variable "metrics_namespace" {
  type    = string
  default = "StopForFuel/Backend"
}

variable "app_tag_value" {
  type    = string
  default = "stopforfuel-backend"
}
