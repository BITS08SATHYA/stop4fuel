variable "bucket_name" {
  type = string
}

variable "environment" {
  type = string
}

variable "allowed_cors_origins" {
  type    = list(string)
  default = []
}
