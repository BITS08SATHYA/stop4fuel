# ============================================================
# Terraform Import Commands
# ============================================================
# Run these commands once to import existing resources into state.
# After import, run `terraform plan` to verify zero diff.
#
# Usage: cd infra/environments/prod && run each command below
#
# ===================== BATCH 1: Foundation =====================
#
# --- VPC & Internet Gateway ---
# terraform import module.networking.aws_vpc.main vpc-0b28cfa838497958c
# terraform import module.networking.aws_internet_gateway.main igw-0ae962b14e85a28a2
#
# --- IAM Role ---
# terraform import aws_iam_role.ecs_execution stopforfuel-ecs-execution-role
#
# --- Secrets Manager ---
# terraform import aws_secretsmanager_secret.db_credentials arn:aws:secretsmanager:ap-south-1:607856468014:secret:stopforfuel/db-credentials-o18KfV
#
# --- ECR Repositories ---
# terraform import 'module.ecr.aws_ecr_repository.repos["backend"]' stopforfuel-backend
# terraform import 'module.ecr.aws_ecr_repository.repos["frontend"]' stopforfuel-frontend
#
# ===================== BATCH 2: Subnets, EIP, Security Groups =====================
#
# --- Subnets ---
# terraform import 'module.networking.aws_subnet.public[0]' subnet-0f96f8251f8118733
# terraform import 'module.networking.aws_subnet.public[1]' subnet-01defdee04c0a72ad
# terraform import 'module.networking.aws_subnet.private[0]' subnet-0c239093f82148326
# terraform import 'module.networking.aws_subnet.private[1]' subnet-03b488648f9effc82
#
# --- NAT Elastic IP ---
# terraform import 'module.networking.aws_eip.nat[0]' eipalloc-06ff7309dc7394466
#
# --- Security Groups ---
# terraform import 'module.networking.aws_security_group.alb[0]' sg-0d733885cb5fc8109
# terraform import 'module.networking.aws_security_group.ecs[0]' sg-014ed56bc468ae73a
# terraform import 'module.networking.aws_security_group.rds[0]' sg-064c6c0909e2d8624
# NOTE: EC2 SG (module.networking.aws_security_group.ec2) likely doesn't exist in prod — Terraform will create it (harmless)
#
# ===================== BATCH 3: Route Tables, NAT GW, Associations =====================
#
# --- Route Tables ---
# terraform import module.networking.aws_route_table.public rtb-0857fd22afc3c983e
# terraform import 'module.networking.aws_route_table.private[0]' rtb-0f6a50fbb7def51ef
#
# --- NAT Gateway ---
# terraform import 'module.networking.aws_nat_gateway.main[0]' nat-0b7729c4e2c3ad61d
#
# --- Route Table Associations (format: subnet-id/route-table-id) ---
# terraform import 'module.networking.aws_route_table_association.public[0]' subnet-0f96f8251f8118733/rtb-0857fd22afc3c983e
# terraform import 'module.networking.aws_route_table_association.public[1]' subnet-01defdee04c0a72ad/rtb-0857fd22afc3c983e
# terraform import 'module.networking.aws_route_table_association.private[0]' subnet-0c239093f82148326/rtb-0f6a50fbb7def51ef
# terraform import 'module.networking.aws_route_table_association.private[1]' subnet-03b488648f9effc82/rtb-0f6a50fbb7def51ef
#
# ===================== BATCH 4: ALB =====================
#
# terraform import module.alb.aws_lb.main arn:aws:elasticloadbalancing:ap-south-1:607856468014:loadbalancer/app/stopforfuel-alb/fd7c108956b60fb4
# terraform import module.alb.aws_lb_target_group.backend arn:aws:elasticloadbalancing:ap-south-1:607856468014:targetgroup/stopforfuel-backend-tg/678070de5e06d794
# terraform import module.alb.aws_lb_target_group.frontend arn:aws:elasticloadbalancing:ap-south-1:607856468014:targetgroup/stopforfuel-frontend-tg/5cf15ca9baf95b66
# terraform import module.alb.aws_lb_listener.https arn:aws:elasticloadbalancing:ap-south-1:607856468014:listener/app/stopforfuel-alb/fd7c108956b60fb4/fd4b51c8332b6e4b
# terraform import module.alb.aws_lb_listener.http arn:aws:elasticloadbalancing:ap-south-1:607856468014:listener/app/stopforfuel-alb/fd7c108956b60fb4/b2edb04fa40a83d1
#
# ===================== BATCH 5: IAM Policies =====================
#
# terraform import aws_iam_role_policy_attachment.ecs_execution_basic "stopforfuel-ecs-execution-role/arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
# terraform import aws_iam_role_policy.ecs_execution_secrets "stopforfuel-ecs-execution-role:SecretsManagerAccess"
# NOTE: AWS has an extra inline policy "CloudWatchLogsAccess" not in Terraform — left unmanaged
#
# ===================== BATCH 6: RDS =====================
#
# terraform import module.rds.aws_db_subnet_group.main stopforfuel-db-subnet
# terraform import module.rds.aws_db_instance.main stopforfuel-db
#
# ===================== BATCH 7: ECS =====================
#
# --- CloudWatch Log Groups (import if they exist) ---
# terraform import 'module.ecs.aws_cloudwatch_log_group.backend' /ecs/stopforfuel-backend
# terraform import 'module.ecs.aws_cloudwatch_log_group.frontend' /ecs/stopforfuel-frontend
#
# --- Cluster ---
# terraform import module.ecs.aws_ecs_cluster.main stopforfuel-cluster
#
# --- Task Definitions ---
# terraform import module.ecs.aws_ecs_task_definition.backend stopforfuel-backend
# terraform import module.ecs.aws_ecs_task_definition.frontend stopforfuel-frontend
#
# --- Services ---
# terraform import module.ecs.aws_ecs_service.backend stopforfuel-cluster/stopforfuel-backend-service
# terraform import module.ecs.aws_ecs_service.frontend stopforfuel-cluster/stopforfuel-frontend-service
#
# ===================== BATCH 8: ECR Lifecycle + SSM =====================
#
# --- ECR Lifecycle Policies — don't exist in AWS, Terraform will create them ---
#
# --- SSM Parameters — none exist in AWS, Terraform will create them ---
