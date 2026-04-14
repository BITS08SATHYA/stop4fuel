#!/usr/bin/env bash
# Run ad-hoc SQL against the prod RDS via a throwaway Fargate task.
#
# Usage:
#   AWS_PROFILE=stopforfuel-prod ./scripts/rds-sql-oneoff.sh path/to/file.sql
#   AWS_PROFILE=stopforfuel-prod ./scripts/rds-sql-oneoff.sh -c "SELECT 1;"
#
# Requires: aws CLI, jq. Region defaults to ap-south-1.

set -euo pipefail

REGION="${AWS_REGION:-ap-south-1}"
CLUSTER="${ECS_CLUSTER:-stopforfuel-cluster}"
FAMILY="${TASK_FAMILY:-stopforfuel-psql-oneoff}"
SECRET_NAME="${DB_SECRET:-stopforfuel/db-credentials}"
LOG_GROUP="/ecs/stopforfuel-psql-oneoff"
IMAGE="${PSQL_IMAGE:-postgres:16-alpine}"

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <sql-file> | -c \"<inline sql>\"" >&2
  exit 1
fi

if [[ "$1" == "-c" ]]; then
  SQL="$2"
else
  SQL="$(cat "$1")"
fi

echo "==> Resolving AWS account + networking"
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
SECRET_ARN=$(aws secretsmanager describe-secret --secret-id "$SECRET_NAME" --region "$REGION" --query ARN --output text)

VPC_ID=$(aws ec2 describe-vpcs --region "$REGION" --filters "Name=tag:Name,Values=stopforfuel-vpc" --query 'Vpcs[0].VpcId' --output text)
SUBNETS=$(aws ec2 describe-subnets --region "$REGION" \
  --filters "Name=vpc-id,Values=$VPC_ID" "Name=tag:Name,Values=stopforfuel-private-*" \
  --query 'Subnets[].SubnetId' --output text | tr '\t' ',')
SG_ID=$(aws ec2 describe-security-groups --region "$REGION" \
  --filters "Name=vpc-id,Values=$VPC_ID" "Name=group-name,Values=stopforfuel-ecs-sg" \
  --query 'SecurityGroups[0].GroupId' --output text)

EXEC_ROLE="arn:aws:iam::${ACCOUNT_ID}:role/stopforfuel-ecs-execution-role"
TASK_ROLE="arn:aws:iam::${ACCOUNT_ID}:role/stopforfuel-ecs-task-role"

echo "    Account:  $ACCOUNT_ID"
echo "    Subnets:  $SUBNETS"
echo "    SG:       $SG_ID"
echo "    Secret:   $SECRET_ARN"

echo "==> Ensuring CloudWatch log group"
aws logs create-log-group --log-group-name "$LOG_GROUP" --region "$REGION" 2>/dev/null || true

echo "==> Registering task definition ($FAMILY)"
# Pass SQL via an env var; container runs psql reading it from $SQL.
TASKDEF=$(cat <<JSON
{
  "family": "$FAMILY",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "256",
  "memory": "512",
  "executionRoleArn": "$EXEC_ROLE",
  "taskRoleArn": "$TASK_ROLE",
  "containerDefinitions": [
    {
      "name": "psql",
      "image": "$IMAGE",
      "essential": true,
      "entryPoint": ["sh", "-c"],
      "command": ["echo \"\$SQL\" | psql \"host=\$DB_HOST port=5432 dbname=\$DB_NAME user=\$DB_USER password=\$DB_PASSWORD sslmode=require\" -v ON_ERROR_STOP=1"],
      "secrets": [
        {"name": "DB_HOST",     "valueFrom": "$SECRET_ARN:host::"},
        {"name": "DB_USER",     "valueFrom": "$SECRET_ARN:username::"},
        {"name": "DB_PASSWORD", "valueFrom": "$SECRET_ARN:password::"},
        {"name": "DB_NAME",     "valueFrom": "$SECRET_ARN:dbname::"}
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "$LOG_GROUP",
          "awslogs-region": "$REGION",
          "awslogs-stream-prefix": "psql"
        }
      }
    }
  ]
}
JSON
)

TASKDEF_ARN=$(aws ecs register-task-definition --region "$REGION" --cli-input-json "$TASKDEF" \
  --query 'taskDefinition.taskDefinitionArn' --output text)
echo "    $TASKDEF_ARN"

echo "==> Running task"
OVERRIDES=$(jq -n --arg sql "$SQL" '{containerOverrides:[{name:"psql",environment:[{name:"SQL",value:$sql}]}]}')
TASK_ARN=$(aws ecs run-task \
  --region "$REGION" \
  --cluster "$CLUSTER" \
  --launch-type FARGATE \
  --task-definition "$TASKDEF_ARN" \
  --network-configuration "awsvpcConfiguration={subnets=[$SUBNETS],securityGroups=[$SG_ID],assignPublicIp=DISABLED}" \
  --overrides "$OVERRIDES" \
  --query 'tasks[0].taskArn' --output text)

TASK_ID="${TASK_ARN##*/}"
echo "    $TASK_ARN"
echo "==> Waiting for task to finish..."
aws ecs wait tasks-stopped --region "$REGION" --cluster "$CLUSTER" --tasks "$TASK_ARN"

EXIT_CODE=$(aws ecs describe-tasks --region "$REGION" --cluster "$CLUSTER" --tasks "$TASK_ARN" \
  --query 'tasks[0].containers[0].exitCode' --output text)
STOP_REASON=$(aws ecs describe-tasks --region "$REGION" --cluster "$CLUSTER" --tasks "$TASK_ARN" \
  --query 'tasks[0].stoppedReason' --output text)

echo "==> Logs"
aws logs get-log-events --region "$REGION" \
  --log-group-name "$LOG_GROUP" \
  --log-stream-name "psql/psql/$TASK_ID" \
  --query 'events[].message' --output text || true

echo
echo "==> Exit: $EXIT_CODE  ($STOP_REASON)"
exit "${EXIT_CODE:-1}"
