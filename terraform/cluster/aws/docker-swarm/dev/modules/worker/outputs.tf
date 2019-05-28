output "worker_ips" {
  value = "${aws_instance.workers.*.private_ip}"
}
