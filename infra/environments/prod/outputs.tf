output "vpc_id" {
  value = module.networking.vpc_id
}

output "ecr_registry" {
  value = local.ecr_registry
}

output "ecs_cluster" {
  value = module.ecs.cluster_name
}

output "alb_dns_name" {
  value = module.alb.alb_dns_name
}

output "rds_endpoint" {
  value = module.rds.endpoint
}

