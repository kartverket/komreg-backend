variable "kubernetes_project_id" {
  type = string
}

variable "project_id" {
  type = string
}

variable "deploy_env" {
  type = string
}

variable "secret_ids" {
  type = set(string)

  default = [
    "db_matrikkel_jdbc_url",
    "db_matrikkel_kilde_username",
    "db_matrikkel_kilde_password",
    "db_matrikkel_mottaker_username",
    "db_matrikkel_mottaker_password"
  ]
}
