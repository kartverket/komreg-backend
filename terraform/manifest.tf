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
              application = "komreg-backend"
              namespace   = "komreg"
            }
          ]
          external = [
            {
              host = "154.224.228.35.bc.googleusercontent.com"
              ip   = "35.228.224.154"
              ports : [
                {
                  name     = "komreg-parameter-database-connection"
                  protocol = "TCP"
                  port     = 5432
                }
              ]
            }
          ]
        }
      }
    }
  }
}