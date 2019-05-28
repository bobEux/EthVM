terraform {
  backend "s3" {
    encrypt        = true
    bucket         = "pillar-sandbox-release-eu-west-2-007308900042"
    dynamodb_table = "terraform-state-lock-dynamo"
    region         = "eu-west-2"
    key            = "dev/ethvm.tfstate"
    profile        = "sandbox"
  }
}
