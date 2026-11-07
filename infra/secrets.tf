# Segredos do Mercado Pago (F6-m), lidos so pelo Billing via app_secrets. recovery_window_in_days = 0
# porque o destroy e diario e o nome nao pode ficar reservado por 7 dias (regras 3.3 da F2).
resource "aws_secretsmanager_secret" "mp_access_token" {
  name                    = "mercadopago-access-token"
  description             = "Access token da aplicacao mecanica-fase4 no Mercado Pago."
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "mp_access_token" {
  secret_id     = aws_secretsmanager_secret.mp_access_token.id
  secret_string = var.mp_access_token
}

resource "aws_secretsmanager_secret" "mp_webhook_secret" {
  name                    = "mercadopago-webhook-secret"
  description             = "Segredo da assinatura do webhook do Mercado Pago."
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "mp_webhook_secret" {
  secret_id     = aws_secretsmanager_secret.mp_webhook_secret.id
  secret_string = var.mp_webhook_secret
}
