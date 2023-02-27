terraform {
  backend "gcs" {
    prefix = "komreg-backend"
  }
}

provider "kubernetes" {
  config_path = "~/.kube/config"
}

provider "google" {
  project = var.project_id
}

locals {
  environment = var.deploy_env
  namespace   = "komreg"
  app_name    = "komreg-backend"
}