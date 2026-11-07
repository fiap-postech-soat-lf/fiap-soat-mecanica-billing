# Contrato infra -> CD (secao 3.6 do plano da F1): o servico-cd.yml le estes tres outputs.

output "ecr_repository_url" {
  description = "Repositorio da imagem do servico."
  value       = aws_ecr_repository.this.repository_url
}

output "app_config" {
  description = "Vira o ConfigMap mecanica-billing-config."
  value = {
    SPRING_DATASOURCE_URL = module.db.jdbc_url
    AWS_REGION            = var.aws_region
    # --from-env-file nao aceita valor multilinha; o JwtTokenValidator aceita PEM com \n escapado.
    JWT_PUBLIC_KEY = replace(data.aws_ssm_parameter.jwt_public_key.insecure_value, "\n", "\\n")

    APP_MESSAGING_DLQS             = aws_sqs_queue.comandos_dlq.url
    APP_MESSAGING_CONSUMIDOR_FILAS = aws_sqs_queue.comandos.url
    # Variavel explicita: a ligacao relaxada do Spring nao preserva o "-" das chaves de mapa.
    MESSAGING_DESTINO_BILLING_EVENTOS = aws_sns_topic.eventos.arn
  }
}

output "app_secrets" {
  description = "Vira o Secret mecanica-billing-secret."
  sensitive   = true
  value = {
    SPRING_DATASOURCE_USERNAME = module.db.username
    SPRING_DATASOURCE_PASSWORD = module.db.password
    MP_ACCESS_TOKEN            = var.mp_access_token
    MP_WEBHOOK_SECRET          = var.mp_webhook_secret
  }
}
