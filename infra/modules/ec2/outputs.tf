output "instance_id" {
  value = aws_instance.main.id
}

output "elastic_ip" {
  value = aws_eip.main.public_ip
}

output "private_ip" {
  value = aws_instance.main.private_ip
}
