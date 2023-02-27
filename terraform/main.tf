terraform {
  backend "gcs" {
    prefix = "komreg-backend"
  }
  required_version = "~> 1.0"
  required_providers {
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.6"
    }
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