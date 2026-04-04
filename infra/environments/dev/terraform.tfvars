aws_region     = "us-east-1"
aws_profile    = "your-aws-profile"
aws_account_id = "YOUR_AWS_ACCOUNT_ID"
project_name   = "stopforfuel"
environment    = "dev"
vpc_cidr       = "10.1.0.0/16"

ec2_instance_type = "t3.small"
ec2_key_name      = "stopforfuel-key"
allowed_ssh_cidr  = "YOUR_IP/32"

# Cognito
cognito_user_pool_id = "YOUR_COGNITO_USER_POOL_ID"
cognito_client_id    = "YOUR_COGNITO_CLIENT_ID"
cognito_domain       = "YOUR_COGNITO_DOMAIN.auth.us-east-1.amazoncognito.com"

# db_password — pass via: terraform apply -var="db_password=YOUR_PASSWORD"
# or set TF_VAR_db_password environment variable
