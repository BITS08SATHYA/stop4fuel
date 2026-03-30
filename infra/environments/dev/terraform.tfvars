aws_region     = "us-east-1"
aws_profile    = "sathya-nyu"
project_name   = "stopforfuel"
environment    = "dev"
vpc_cidr       = "10.1.0.0/16"

ec2_instance_type = "t3.small"
ec2_key_name      = "stopforfuel-key"
allowed_ssh_cidr  = "0.0.0.0/0"

# Cognito (existing dev pool)
cognito_user_pool_id = "REDACTED"
cognito_client_id    = "REDACTED"
cognito_domain       = "stopforfuel-dev.auth.us-east-1.amazoncognito.com"

# db_password — pass via: terraform apply -var="db_password=YOUR_PASSWORD"
# or set TF_VAR_db_password environment variable
