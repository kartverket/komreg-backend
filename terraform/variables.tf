variable "kubernetes_project_id" {
  type = string
}

variable "project_id" {
  type = string
}

variable "image" {
  type = string
}

variable "external_dns_hostname" {
  type = string
}

variable "container_registry" {
  type = string
}

variable "deploy_env" {
  type = string
}

variable "secret_ids" {
  type = set(string)
  default = [
    "dummy_db_username",
    "dummy_db_password"
  ]
}