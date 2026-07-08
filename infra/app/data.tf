# Lee los outputs de infra/base (environment, JWT, App Insights, etc.).
# infra/base vive en el repo puckzone-game; este bloque es el mismo en el
# infra/app de cada repo de servicio.
data "terraform_remote_state" "base" {
  backend = "azurerm"

  config = {
    resource_group_name  = "puckzone-tfstate-rg"
    storage_account_name = "puckzonetfstate"
    container_name       = "tfstate"
    key                  = "base.tfstate"
  }
}
