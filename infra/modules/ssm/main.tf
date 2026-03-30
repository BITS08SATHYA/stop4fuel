resource "aws_ssm_parameter" "params" {
  for_each = var.parameters

  name  = "/${var.project_name}/${each.key}"
  type  = "String"
  value = each.value

  tags = {
    Environment = var.environment
  }
}
