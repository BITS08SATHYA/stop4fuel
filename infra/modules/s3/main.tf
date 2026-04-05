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
