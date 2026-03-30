# ============================================================
# Terraform Import Commands
# ============================================================
# Run these commands once to import existing resources into state.
# After import, run `terraform plan` to verify zero diff.
#
# Usage: cd infra/environments/prod && run each command below
#
# --- Networking ---
# terraform import module.networking.aws_vpc.main vpc-0b28cfa838497958c
# terraform import module.networking.aws_internet_gateway.main igw-0ae962b14e85a28a2
# terraform import 'module.networking.aws_subnet.public[0]' subnet-0f96f8251f8118733
# terraform import 'module.networking.aws_subnet.public[1]' subnet-01defdee04c0a72ad
# terraform import 'module.networking.aws_subnet.private[0]' subnet-0c239093f82148326
# terraform import 'module.networking.aws_subnet.private[1]' subnet-03b488648f9effc82
# terraform import module.networking.aws_route_table.public rtb-0857fd22afc3c983e
# terraform import 'module.networking.aws_route_table.private[0]' rtb-0f6a50fbb7def51ef
# terraform import 'module.networking.aws_eip.nat[0]' <nat-eip-allocation-id>
# terraform import 'module.networking.aws_nat_gateway.main[0]' nat-0b7729c4e2c3ad61d
#
# --- Security Groups ---
# terraform import 'module.networking.aws_security_group.alb[0]' sg-0d733885cb5fc8109
# terraform import 'module.networking.aws_security_group.ecs[0]' sg-014ed56bc468ae73a
# terraform import 'module.networking.aws_security_group.rds[0]' sg-064c6c0909e2d8624
#
# --- ECR ---
# terraform import 'module.ecr.aws_ecr_repository.repos["backend"]' stopforfuel-backend
# terraform import 'module.ecr.aws_ecr_repository.repos["frontend"]' stopforfuel-frontend
#
# --- ALB ---
# terraform import module.alb.aws_lb.main arn:aws:elasticloadbalancing:ap-south-1:607856468014:loadbalancer/app/stopforfuel-alb/fd7c108956b60fb4
# terraform import module.alb.aws_lb_target_group.backend arn:aws:elasticloadbalancing:ap-south-1:607856468014:targetgroup/stopforfuel-backend-tg/678070de5e06d794
# terraform import module.alb.aws_lb_target_group.frontend arn:aws:elasticloadbalancing:ap-south-1:607856468014:targetgroup/stopforfuel-frontend-tg/5cf15ca9baf95b66
# terraform import module.alb.aws_lb_listener.https <https-listener-arn>
# terraform import module.alb.aws_lb_listener.http <http-listener-arn>
#
# --- ECS ---
# terraform import module.ecs.aws_ecs_cluster.main stopforfuel-cluster
# terraform import module.ecs.aws_ecs_service.backend stopforfuel-cluster/stopforfuel-backend-service
# terraform import module.ecs.aws_ecs_service.frontend stopforfuel-cluster/stopforfuel-frontend-service
# terraform import module.ecs.aws_ecs_task_definition.backend stopforfuel-backend
# terraform import module.ecs.aws_ecs_task_definition.frontend stopforfuel-frontend
#
# --- RDS ---
# terraform import module.rds.aws_db_subnet_group.main stopforfuel-db-subnet
# terraform import module.rds.aws_db_instance.main stopforfuel-db
#
# --- S3 ---
# terraform import module.s3.aws_s3_bucket.main stopforfuel-frontend-prod
#
# --- Secrets Manager ---
# terraform import aws_secretsmanager_secret.db_credentials arn:aws:secretsmanager:ap-south-1:607856468014:secret:stopforfuel/db-credentials-o18KfV
#
# --- IAM ---
# terraform import aws_iam_role.ecs_execution stopforfuel-ecs-execution-role
