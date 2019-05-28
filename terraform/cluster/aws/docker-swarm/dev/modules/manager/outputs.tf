output "root_manager_id" {
  value = "${aws_instance.manager.id}"
}

output "root_manager_public_ip" {
  value = "${aws_instance.manager.public_ip}"
}

output "root_manager_private_ip" {
  value = "${aws_instance.manager.private_ip}"
}

output "swarm_tokens" {
  value = "${data.external.swarm_tokens.result}"
}
