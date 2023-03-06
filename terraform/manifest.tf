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
        max                  = 4
        min                  = 2
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
      ]
      liveness = {
        path = "/actuator/health"
        port = 8080
      }
      readiness = {
        path = "/actuator/health"
        port = 8080
      }
      resources = {
        limits = {
          memory = "3Gi"
        }
        requests = {
          cpu = "30m"
        }
      }
      accessPolicy = {
        outbound = {
          rules = [
            {
              application = "komreg-parameter-backend"
            }
          ]
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
}
