variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "instance_type" {
  type    = string
  default = "t3.small"
}

variable "subnet_id" {
  type = string
}

variable "security_group_id" {
  type = string
}

variable "iam_instance_profile" {
  type = string
}

variable "key_name" {
  type    = string
  default = ""
}

variable "ecr_registry" {
  type = string
}

variable "aws_region" {
  type = string
}
