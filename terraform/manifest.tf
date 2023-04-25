resource "kubernetes_manifest" "komreg-backend_application" {
  manifest = {
    apiVersion = "skiperator.kartverket.no/v1alpha1"
    kind       = "Application"

    metadata = {
      name      = local.app_name
      namespace = local.namespace
    }
    spec = {
      image = var.image
      port  = 8080

      ingresses = [
        var.external_dns_hostname
      ]
      replicas = {
        targetCpuUtilization = 80
        max                  = 1
        min                  = 1
      }
      gcp = {
        auth = {
          serviceAccount = google_service_account.komreg_backend_runtime_sa.email
        }
      }
      env = [
        {
          name  = "GOOGLE_CLOUD_PROJECT"
          value = var.project_id
        },
        {
          name  = "environment"
          value = local.environment
        }
      ]
      liveness = {
        path             = "/actuator/health"
        port             = 8080
        timeout          = 60
        failureThreshold = 1000
      }
      readiness = {
        path             = "/actuator/health"
        port             = 8080
        timeout          = 60
        failureThreshold = 1000
      }
      resources = {
        limits = {
          memory = "8Gi"
          cpu    = "4"
        }
        requests = {
          memory = "256Mi"
          cpu    = "30m"
        }
      }
      accessPolicy = {
        outbound = {
          external = [
            {
              host = "nnridb170.statkart.no"
              ip   = "159.162.49.137"

              ports = [
                {
                  name     = "matrikkel-dev"
                  port     = 1521
                  protocol = "TCP"
                }
              ]
            }
          ]
        }
      }
    }
  }
  field_manager {
    force_conflicts = true
  }
}
