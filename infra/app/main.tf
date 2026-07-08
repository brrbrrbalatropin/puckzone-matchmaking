resource "azurerm_container_app" "matchmaking" {
  name                         = "puckzone-matchmaking"
  resource_group_name          = data.terraform_remote_state.base.outputs.resource_group_name
  container_app_environment_id = data.terraform_remote_state.base.outputs.container_app_environment_id
  revision_mode                = "Single"

  # Matchmaking valida JWT localmente con el secreto compartido de produccion
  # (el mismo con el que auth firma), leido del remote state de infra/base.
  secret {
    name  = "jwt-secret"
    value = data.terraform_remote_state.base.outputs.jwt_secret
  }

  template {
    # OJO: la cola de matchmaking vive EN MEMORIA (sin BD). Con mas de 1 replica
    # habria colas independientes y jugadores que nunca se emparejan.
    # NO subir hasta externalizar la cola (p. ej. Redis).
    min_replicas = 1
    max_replicas = 1

    container {
      # 0.5/1Gi como el resto: con menos CPU el arranque de Spring supera los
      # ~30s y la liveness probe mata el contenedor antes de abrir el puerto.
      name   = "matchmaking"
      image  = var.image
      cpu    = 0.5
      memory = "1Gi"

      env {
        name        = "PUCKZONE_JWT_SECRET"
        secret_name = "jwt-secret"
      }
      # matchmaking llama a game (POST /games) por HTTP interno del environment:
      # resolucion por nombre de app, sin pasar por internet. HTTP plano porque
      # el cert del dominio .internal. no es confiable para Java (por eso los
      # servicios internos tienen allow_insecure_connections = true).
      env {
        name  = "GAME_BASE_URL"
        value = "http://puckzone-game"
      }
      env {
        name  = "APPLICATIONINSIGHTS_CONNECTION_STRING"
        value = data.terraform_remote_state.base.outputs.application_insights_connection_string
      }

      liveness_probe {
        transport = "HTTP"
        port      = 8082
        path      = "/actuator/health/liveness"
        # Margen para el arranque de Spring; sin esto la probe empieza a fallar
        # de inmediato y ACA reinicia el contenedor en bucle.
        initial_delay = 20
      }
      readiness_probe {
        transport = "HTTP"
        port      = 8082
        path      = "/actuator/health/readiness"
        initial_delay = 10
      }
    }
  }

  # Interno: solo el gateway (en el mismo environment) le habla; nada de internet.
  # allow_insecure permite trafico HTTP plano entre apps del environment (el
  # redirect a HTTPS rompe las llamadas: cert de .internal. no confiable).
  ingress {
    external_enabled           = false
    target_port                = 8082
    transport                  = "auto"
    allow_insecure_connections = true

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  lifecycle {
    # El pipeline actualiza la imagen con az containerapp update; sin esto,
    # cada terraform apply intentaria devolver la app a la imagen inicial.
    ignore_changes = [template[0].container[0].image]
  }
}
