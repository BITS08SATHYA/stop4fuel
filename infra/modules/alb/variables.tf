variable "project_name" {
  type = string
}

variable "environment" {
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

variable "certificate_arn" {
  type = string
}

variable "enable_waf" {
  description = "Attach AWS WAFv2 managed rule groups + rate limit to the ALB"
  type        = bool
  default     = true
}
