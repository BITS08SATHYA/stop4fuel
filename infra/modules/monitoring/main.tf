# ============================================================
# SNS topic for alarm notifications
# ============================================================
resource "aws_sns_topic" "alarms" {
  name = "${var.project_name}-${var.environment}-alarms"

  tags = {
    Environment = var.environment
  }
}

resource "aws_sns_topic_subscription" "alarms_email" {
  count     = var.alarm_email != "" ? 1 : 0
  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

locals {
  alarm_actions = [aws_sns_topic.alarms.arn]
  ok_actions    = [aws_sns_topic.alarms.arn]
  app_dims = {
    application = var.app_tag_value
    env         = var.environment
  }
}

# ============================================================
# ECS alarms — backend
# ============================================================
resource "aws_cloudwatch_metric_alarm" "backend_cpu_high" {
  alarm_name          = "${var.project_name}-backend-cpu-high"
  alarm_description   = "Backend ECS CPU > 80% for 10 min"
  namespace           = "AWS/ECS"
  metric_name         = "CPUUtilization"
  statistic           = "Average"
  period              = 60
  evaluation_periods  = 10
  datapoints_to_alarm = 10
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    ClusterName = var.ecs_cluster_name
    ServiceName = var.ecs_backend_service_name
  }
  alarm_actions = local.alarm_actions
  ok_actions    = local.ok_actions
}

resource "aws_cloudwatch_metric_alarm" "backend_memory_high" {
  alarm_name          = "${var.project_name}-backend-memory-high"
  alarm_description   = "Backend ECS memory > 85% for 10 min"
  namespace           = "AWS/ECS"
  metric_name         = "MemoryUtilization"
  statistic           = "Average"
  period              = 60
  evaluation_periods  = 10
  datapoints_to_alarm = 10
  threshold           = 85
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    ClusterName = var.ecs_cluster_name
    ServiceName = var.ecs_backend_service_name
  }
  alarm_actions = local.alarm_actions
  ok_actions    = local.ok_actions
}

# ============================================================
# ALB alarms — backend target group
# ============================================================
resource "aws_cloudwatch_metric_alarm" "backend_unhealthy_hosts" {
  alarm_name          = "${var.project_name}-backend-unhealthy-hosts"
  alarm_description   = "At least one backend target has been unhealthy for 3 min"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "UnHealthyHostCount"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 3
  datapoints_to_alarm = 3
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    TargetGroup  = var.backend_tg_arn_suffix
    LoadBalancer = var.alb_arn_suffix
  }
  alarm_actions = local.alarm_actions
  ok_actions    = local.ok_actions
}

resource "aws_cloudwatch_metric_alarm" "backend_5xx_rate" {
  alarm_name          = "${var.project_name}-backend-5xx-rate"
  alarm_description   = "Backend 5xx rate > 1% over 5 min"
  evaluation_periods  = 5
  datapoints_to_alarm = 5
  threshold           = 1
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"

  metric_query {
    id          = "rate"
    expression  = "IF(requests > 0, 100 * errors / requests, 0)"
    label       = "Backend 5xx %"
    return_data = true
  }
  metric_query {
    id = "errors"
    metric {
      namespace   = "AWS/ApplicationELB"
      metric_name = "HTTPCode_Target_5XX_Count"
      stat        = "Sum"
      period      = 60
      dimensions = {
        TargetGroup  = var.backend_tg_arn_suffix
        LoadBalancer = var.alb_arn_suffix
      }
    }
  }
  metric_query {
    id = "requests"
    metric {
      namespace   = "AWS/ApplicationELB"
      metric_name = "RequestCount"
      stat        = "Sum"
      period      = 60
      dimensions = {
        TargetGroup  = var.backend_tg_arn_suffix
        LoadBalancer = var.alb_arn_suffix
      }
    }
  }

  alarm_actions = local.alarm_actions
  ok_actions    = local.ok_actions
}

# ============================================================
# RDS alarms
# ============================================================
resource "aws_cloudwatch_metric_alarm" "rds_cpu_high" {
  alarm_name          = "${var.project_name}-rds-cpu-high"
  alarm_description   = "RDS CPU > 80% for 10 min"
  namespace           = "AWS/RDS"
  metric_name         = "CPUUtilization"
  statistic           = "Average"
  period              = 60
  evaluation_periods  = 10
  datapoints_to_alarm = 10
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    DBInstanceIdentifier = var.rds_instance_id
  }
  alarm_actions = local.alarm_actions
  ok_actions    = local.ok_actions
}

resource "aws_cloudwatch_metric_alarm" "rds_free_storage_low" {
  alarm_name          = "${var.project_name}-rds-free-storage-low"
  alarm_description   = "RDS free storage < 2 GB"
  namespace           = "AWS/RDS"
  metric_name         = "FreeStorageSpace"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  threshold           = 2147483648
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    DBInstanceIdentifier = var.rds_instance_id
  }
  alarm_actions = local.alarm_actions
  ok_actions    = local.ok_actions
}

# ============================================================
# Custom Micrometer alarms
# ============================================================
resource "aws_cloudwatch_metric_alarm" "shift_close_slow" {
  alarm_name          = "${var.project_name}-shift-close-slow"
  alarm_description   = "Shift close p95 > 10s sustained for 15 min"
  namespace           = var.metrics_namespace
  metric_name         = "shift.close.duration"
  extended_statistic  = "p95"
  period              = 300
  evaluation_periods  = 3
  datapoints_to_alarm = 3
  threshold           = 10
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  dimensions          = local.app_dims
  alarm_actions       = local.alarm_actions
  ok_actions          = local.ok_actions
}

resource "aws_cloudwatch_metric_alarm" "ai_request_slow" {
  alarm_name          = "${var.project_name}-ai-request-slow"
  alarm_description   = "Anthropic request p95 > 15s sustained for 15 min"
  namespace           = var.metrics_namespace
  metric_name         = "ai.request.duration"
  extended_statistic  = "p95"
  period              = 300
  evaluation_periods  = 3
  datapoints_to_alarm = 3
  threshold           = 15
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  dimensions          = local.app_dims
  alarm_actions       = local.alarm_actions
  ok_actions          = local.ok_actions
}

# ============================================================
# Dashboard
# ============================================================
resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = "${var.project_name}-${var.environment}"

  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          title   = "Backend ECS — CPU & Memory %"
          region  = var.aws_region
          view    = "timeSeries"
          stacked = false
          metrics = [
            ["AWS/ECS", "CPUUtilization", "ClusterName", var.ecs_cluster_name, "ServiceName", var.ecs_backend_service_name],
            [".", "MemoryUtilization", ".", ".", ".", "."]
          ]
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6
        properties = {
          title  = "ALB — Requests & 5xx"
          region = var.aws_region
          view   = "timeSeries"
          metrics = [
            ["AWS/ApplicationELB", "RequestCount", "TargetGroup", var.backend_tg_arn_suffix, "LoadBalancer", var.alb_arn_suffix, { stat = "Sum" }],
            [".", "HTTPCode_Target_5XX_Count", ".", ".", ".", ".", { stat = "Sum", yAxis = "right" }],
            [".", "TargetResponseTime", ".", ".", ".", ".", { stat = "p95", yAxis = "right" }]
          ]
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 6
        width  = 12
        height = 6
        properties = {
          title  = "Business — Invoices & Payments (per minute)"
          region = var.aws_region
          view   = "timeSeries"
          metrics = [
            [var.metrics_namespace, "invoices.created", "application", var.app_tag_value, "env", var.environment, { stat = "Sum" }],
            [".", "payments.recorded", ".", ".", ".", ".", { stat = "Sum" }],
            [".", "shifts.opened", ".", ".", ".", ".", { stat = "Sum" }],
            [".", "shifts.closed", ".", ".", ".", ".", { stat = "Sum" }]
          ]
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 6
        width  = 12
        height = 6
        properties = {
          title  = "Latency p95 — HTTP / Shift close / AI"
          region = var.aws_region
          view   = "timeSeries"
          metrics = [
            [var.metrics_namespace, "http.server.requests", "application", var.app_tag_value, "env", var.environment, { stat = "p95", label = "HTTP p95 (s)" }],
            [".", "shift.close.duration", ".", ".", ".", ".", { stat = "p95", label = "Shift close p95 (s)" }],
            [".", "ai.request.duration", ".", ".", ".", ".", { stat = "p95", label = "AI p95 (s)" }]
          ]
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 12
        width  = 12
        height = 6
        properties = {
          title  = "JVM — Heap used & GC pauses"
          region = var.aws_region
          view   = "timeSeries"
          metrics = [
            [var.metrics_namespace, "jvm.memory.used", "application", var.app_tag_value, "env", var.environment, "area", "heap", { stat = "Average" }],
            [".", "jvm.gc.pause", ".", ".", ".", ".", { stat = "Sum", yAxis = "right", label = "GC pause total (s)" }]
          ]
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 12
        width  = 12
        height = 6
        properties = {
          title  = "DB pool — Hikari connections"
          region = var.aws_region
          view   = "timeSeries"
          metrics = [
            [var.metrics_namespace, "hikaricp.connections.active", "application", var.app_tag_value, "env", var.environment, { stat = "Average" }],
            [".", "hikaricp.connections.pending", ".", ".", ".", ".", { stat = "Average" }],
            [".", "hikaricp.connections.idle", ".", ".", ".", ".", { stat = "Average" }]
          ]
        }
      },
      {
        type   = "metric"
        x      = 0
        y      = 18
        width  = 24
        height = 6
        properties = {
          title  = "RDS — CPU & Free storage"
          region = var.aws_region
          view   = "timeSeries"
          metrics = [
            ["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", var.rds_instance_id, { stat = "Average" }],
            [".", "FreeStorageSpace", ".", ".", { stat = "Average", yAxis = "right" }],
            [".", "DatabaseConnections", ".", ".", { stat = "Average" }]
          ]
        }
      }
    ]
  })
}
