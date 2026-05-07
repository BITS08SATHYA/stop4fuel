resource "aws_s3_bucket" "main" {
  bucket = var.bucket_name

  tags = {
    Environment = var.environment
  }
}

resource "aws_s3_bucket_public_access_block" "main" {
  bucket = aws_s3_bucket.main.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "main" {
  bucket = aws_s3_bucket.main.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_cors_configuration" "main" {
  count  = length(var.allowed_cors_origins) > 0 ? 1 : 0
  bucket = aws_s3_bucket.main.id

  cors_rule {
    allowed_origins = var.allowed_cors_origins
    allowed_methods = ["GET", "HEAD"]
    allowed_headers = ["*"]
    expose_headers  = ["Content-Disposition", "Content-Length", "Content-Type", "ETag", "Accept-Ranges"]
    max_age_seconds = 3000
  }
}

# Document folder structure
locals {
  folder_prefixes = [
    "documents/",
    "employees/",
    "invoices/",
    "payments/",
    "reports/",
    "statements/",
  ]
}

resource "aws_s3_object" "folders" {
  for_each = toset(local.folder_prefixes)

  bucket = aws_s3_bucket.main.id
  key    = each.value
  content_type = "application/x-directory"
}
