terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  backend "s3" {
    bucket  = "stopforfuel-terraform-state-prod"
    key     = "prod/terraform.tfstate"
    region  = "ap-south-1"
    encrypt = true
    profile = "stopforfuel-prod"
  }
}

provider "aws" {
  region  = var.aws_region
  profile = var.aws_profile
}
