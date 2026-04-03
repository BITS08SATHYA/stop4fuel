variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "repository_names" {
  type    = list(string)
  default = ["backend", "frontend"]
}
