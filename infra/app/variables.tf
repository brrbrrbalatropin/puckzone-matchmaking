variable "subscription_id" {
  description = "Suscripcion de Azure (identificador, no credencial)"
  type        = string
  default     = "d5860455-eb5f-4995-a11c-8be250730e90"
}

# El pipeline CI/CD es dueno del deploy de imagenes: aqui solo se fija la imagen
# inicial. El job de deploy del workflow actualiza el tag sin pasar por Terraform
# (por eso main.tf ignora cambios de imagen, ver lifecycle).
variable "image" {
  description = "Imagen inicial de la Container App"
  type        = string
  default     = "ghcr.io/brrbrrbalatropin/puckzone-matchmaking:latest"
}
