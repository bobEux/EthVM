resource "aws_vpc" "docker-swarm-vpc" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags {
    Name = "docker-swarm"
  }

}

resource "aws_subnet" "subnet-managers" {
  cidr_block        = "10.0.0.0/24"
  vpc_id            = "${aws_vpc.docker-swarm-vpc.id}"
  availability_zone = "${var.region}${var.manager_zone}"
}

resource "aws_subnet" "subnet-workers" {
  cidr_block        = "10.0.1.0/24"
  vpc_id            = "${aws_vpc.docker-swarm-vpc.id}"
  availability_zone = "${var.region}${var.worker_zone}"
}

resource "aws_internet_gateway" "internet-gateway" {
  vpc_id = "${aws_vpc.docker-swarm-vpc.id}"
}

resource "aws_eip" "manager-ip" {
  instance = "${module.managers.root_manager_id}"
  vpc      = true
}

resource "aws_eip" "nat-ip" {
  vpc = true
}

resource "aws_nat_gateway" "nat_gateway_internal_to_external" {
  allocation_id = "${aws_eip.nat-ip.id}"
  subnet_id     = "${aws_subnet.subnet-managers.id}"
}

resource "aws_route_table" "route-table-workers-to-world" {
  vpc_id = "${aws_vpc.docker-swarm-vpc.id}"

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = "${aws_nat_gateway.nat_gateway_internal_to_external.id}"
  }
}

resource "aws_route_table" "route-table-managers-to-world" {
  vpc_id = "${aws_vpc.docker-swarm-vpc.id}"

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = "${aws_internet_gateway.internet-gateway.id}"
  }
}

resource "aws_route_table_association" "subnet-association-managers" {
  subnet_id      = "${aws_subnet.subnet-managers.id}"
  route_table_id = "${aws_route_table.route-table-managers-to-world.id}"
}

resource "aws_route_table_association" "subnet-association-workers" {
  subnet_id      = "${aws_subnet.subnet-workers.id}"
  route_table_id = "${aws_route_table.route-table-workers-to-world.id}"
}
