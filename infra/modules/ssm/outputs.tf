output "parameter_arns" {
  value = { for k, v in aws_ssm_parameter.params : k => v.arn }
}
