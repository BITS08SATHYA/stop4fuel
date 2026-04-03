aws_region   = "ap-south-1"
aws_profile  = "stopforfuel-prod"
project_name = "stopforfuel"
environment  = "prod"
vpc_cidr     = "10.0.0.0/16"

# Cognito (existing prod pool)
cognito_user_pool_id = "ap-south-1_1jBWQOLC2"
cognito_client_id    = "4mgdk5ld0hfc5hglc3tvklr5li"
cognito_domain       = "stopforfuel-prod.auth.ap-south-1.amazoncognito.com"

# ACM certificate for api.stopforfuel.com
acm_certificate_arn = "arn:aws:acm:ap-south-1:607856468014:certificate/e716a28c-5a0c-4a06-a7b9-903a478cc755"
