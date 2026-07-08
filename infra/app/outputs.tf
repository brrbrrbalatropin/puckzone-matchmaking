output "matchmaking_internal_fqdn" {
  description = "FQDN interno de matchmaking; el gateway lo usa como MATCHMAKING_SERVICE_URL"
  value       = "https://${azurerm_container_app.matchmaking.name}.internal.${data.terraform_remote_state.base.outputs.container_app_environment_default_domain}"
}
