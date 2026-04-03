output "vpc_id" {
  value = aws_vpc.main.id
}

output "public_subnet_ids" {
  value = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  value = aws_subnet.private[*].id
}

output "ec2_security_group_id" {
  value = aws_security_group.ec2.id
}

output "alb_security_group_id" {
  value = length(aws_security_group.alb) > 0 ? aws_security_group.alb[0].id : null
}

output "ecs_security_group_id" {
  value = length(aws_security_group.ecs) > 0 ? aws_security_group.ecs[0].id : null
}

output "rds_security_group_id" {
  value = length(aws_security_group.rds) > 0 ? aws_security_group.rds[0].id : null
}
