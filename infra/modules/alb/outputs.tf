output "alb_arn" {
  value = aws_lb.main.arn
}

output "alb_dns_name" {
  value = aws_lb.main.dns_name
}

output "backend_target_group_arn" {
  value = aws_lb_target_group.backend.arn
}

output "frontend_target_group_arn" {
  value = aws_lb_target_group.frontend.arn
}

output "alb_arn_suffix" {
  value = aws_lb.main.arn_suffix
}

output "backend_target_group_arn_suffix" {
  value = aws_lb_target_group.backend.arn_suffix
}
