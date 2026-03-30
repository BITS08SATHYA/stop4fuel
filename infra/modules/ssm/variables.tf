variable "project_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "parameters" {
  type        = map(string)
  description = "Map of parameter name suffix to value (e.g., cognito/user-pool-id => value)"
}
