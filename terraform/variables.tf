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

variable "deploy_env" {
  type = string
}

variable "secret_ids" {
  type    = set(string)
  default = [
    "db_matrikkel_jdbc_url",
    "db_matrikkel_username",
    "db_matrikkel_password"
  ]
}