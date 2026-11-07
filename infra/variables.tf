variable "aws_region" {
  type        = string
  description = "Regiao AWS. O Learner Lab permite apenas us-east-1 e us-west-2."
  default     = "us-east-1"
}

# Segredos do Mercado Pago (F6-m). Sem default: sem eles o plan falha. O servico-cd.yml passa
# TF_VAR_mp_access_token e TF_VAR_mp_webhook_secret a partir dos secrets da org.
variable "mp_access_token" {
  type        = string
  description = "Access token da aplicacao mecanica-fase4 no Mercado Pago (credenciais de teste)."
  sensitive   = true

  validation {
    condition     = length(trimspace(var.mp_access_token)) > 0
    error_message = "mp_access_token nao pode ser vazio."
  }
}

variable "mp_webhook_secret" {
  type        = string
  description = "Segredo da assinatura (x-signature) do webhook da aplicacao no Mercado Pago."
  sensitive   = true

  validation {
    condition     = length(trimspace(var.mp_webhook_secret)) > 0
    error_message = "mp_webhook_secret nao pode ser vazio."
  }
}
