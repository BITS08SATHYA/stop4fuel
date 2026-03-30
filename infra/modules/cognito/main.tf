# Import-only module — manages existing Cognito User Pools
# Use: terraform import module.cognito.aws_cognito_user_pool.main <pool-id>

resource "aws_cognito_user_pool" "main" {
  name = "stopforfuel-pool"

  lifecycle {
    ignore_changes = all
  }
}
