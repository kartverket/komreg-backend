terraform {
  backend "gcs" {
    prefix = "komreg-backend"
  }
}

provider "kubernetes" {
  config_path = "~/.kube/config"
}

locals {
  environment = var.deploy_env
  namespace   = "komreg"
  app_name    = "komreg-backend"
}