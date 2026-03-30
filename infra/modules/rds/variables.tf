variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "allocated_storage" {
  type    = number
  default = 20
}

variable "db_name" {
  type    = string
  default = "stopforfuel"
}

variable "db_username" {
  type    = string
  default = "stopforfuel_admin"
}

variable "subnet_ids" {
  type = list(string)
}

variable "security_group_id" {
  type = string
}

variable "db_password_secret_arn" {
  type        = string
  description = "ARN of the Secrets Manager secret containing DB credentials"
}
