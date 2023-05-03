resource "google_service_account" "komreg_backend_runtime_sa" {
  project      = var.project_id
  account_id   = "komreg-backend-runtime"
  display_name = "komreg-backend-runtime"
  description  = "SA for runtime integrations with GCP services"
}

resource "google_secret_manager_secret_iam_member" "komreg_backend_runtime_sa_secret_manager_iam_binding" {
  for_each = var.secret_ids

  project   = var.project_id
  secret_id = each.key
  role      = "roles/secretmanager.secretAccessor"
  member    = "serviceAccount:${google_service_account.komreg_backend_runtime_sa.email}"
}

resource "google_secret_manager_secret_iam_member" "komreg_backend_runtime_sa_secret_manager_iam_binding_v" {
  for_each = var.secret_ids

  project   = var.project_id
  secret_id = each.key
  role      = "roles/secretmanager.viewer"
  member    = "serviceAccount:${google_service_account.komreg_backend_runtime_sa.email}"
}

resource "google_service_account_iam_member" "komreg_backend_runtime_sa_iam_impersonate" {
  service_account_id = google_service_account.komreg_backend_runtime_sa.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.kubernetes_project_id}.svc.id.goog[komreg-main/komreg-backend]"
}