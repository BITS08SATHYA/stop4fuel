output "ec2_elastic_ip" {
  value       = module.ec2.elastic_ip
  description = "Elastic IP of the dev EC2 instance"
}

output "ec2_instance_id" {
  value = module.ec2.instance_id
}

output "vpc_id" {
  value = module.networking.vpc_id
}

output "ecr_registry" {
  value = local.ecr_registry
}

output "s3_bucket" {
  value = module.s3.bucket_name
}

output "ssh_command" {
  value       = "ssh -i ~/Aws_Keys/stopforfuel-key.pem ubuntu@${module.ec2.elastic_ip}"
  description = "SSH command to connect to dev server"
}

output "frontend_url" {
  value       = "http://${module.ec2.elastic_ip}:3000"
  description = "Dev frontend URL"
}

output "backend_url" {
  value       = "http://${module.ec2.elastic_ip}:8080/api"
  description = "Dev backend API URL"
}
