data "template_file" "efs_mount" {
  template = "${file("${path.module}/scripts/setup-efs.sh")}"

  vars = {
    efs_mount_target_dns = "${var.efs_mount_target_dns}"
  }
}

resource "aws_instance" "manager" {
  ami                         = "${var.ami}"
  availability_zone           = "${var.availability_zone}"
  instance_type               = "${var.instance_type}"
  vpc_security_group_ids      = ["${var.security_group}"]
  key_name                    = "${var.key_name}"
  subnet_id                   = "${var.subnet_id}"
  source_dest_check           = false
  associate_public_ip_address = true

  connection {
    type        = "ssh"
    user        = "${var.provision_user}"
    private_key = "${file("${var.ssh_key_path}")}"
    timeout     = "${var.connection_timeout}"
  }

  provisioner "file" {
    source      = "${var.ssh_key_path}"
    destination = "~/key.pem"
  }

  provisioner "file" {
    source      = "${path.module}/scripts/install-docker.sh"
    destination = "/tmp/install-docker.sh"
  }

  provisioner "file" {
    content     = "${data.template_file.efs_mount.rendered}"
    destination = "/tmp/setup-efs.sh"
  }

  provisioner "file" {
    source      = "${path.module}/scripts/provision-first-manager.sh"
    destination = "/tmp/provision-first-manager.sh"
  }

  provisioner "remote-exec" {
    inline = [
      "chmod +x /tmp/provision-first-manager.sh",
      "chmod +x /tmp/install-docker.sh",
      "chmod +x /tmp/setup-efs.sh",
      "sudo /tmp/install-docker.sh",
      "sudo /tmp/setup-efs.sh",
      "if [ ${count.index} -eq 0 ]; then sudo /tmp/provision-first-manager.sh ${self.private_ip}; fi",
    ]
  }

  tags = {
    Name = "${format("%s-%02d", var.name, count.index + 1)}"
    Type = "terraform"
  }
}
