# infra/ do serviço billing

Terraform do Billing, aplicado pelo `servico-cd.yml@v1` antes do build da imagem.

> Na F6 o `infra/` ganhou os segredos do Mercado Pago (F6-m) e o consumidor de `billing-comandos` ligado pelo `app_config`.

## Recursos

| Arquivo | Recursos |
|---|---|
| `data.tf` | Lê do SSM a VPC, as subnets e o SG dos nodes (`/mecanica/k8s/*`) e a chave pública do JWT (`/mecanica/identity/jwt-public-key`), publicada pelo Identity (ordem B4, F4-a) |
| `ecr.tf` | Repositório `mecanica-billing` (`MUTABLE`, `force_delete`, scan on push) e lifecycle das 10 imagens mais recentes |
| `rds.tf` | Módulo `fiap-soat-mecanica-db//modules/rds-postgres?ref=v1`: RDS, secret e SG `mecanica-billing-db`, banco `billing_db` |
| `sns.tf` | Tópico `mecanica-billing-eventos` e SSM `/mecanica/billing/billing-eventos-topic-arn` (lido pelo `infra/` do OS, que assina na `os-saga-respostas`) |
| `secrets.tf` | Secrets `mercadopago-access-token` e `mercadopago-webhook-secret` (`recovery_window_in_days = 0`), expostos só no `app_secrets` |
| `sqs.tf` | Fila `mecanica-billing-comandos` (visibility 30 s, retenção 4 dias) + DLQ, redrive após 5 recebimentos, e SSM `/mecanica/billing/billing-comandos-queue-url`. Sem queue policy: o OS envia direto com a `LabRole`, na mesma conta |

- Tudo é destrutível sem sobra (regras 3.3 do plano da F2).
- Ordem B4: `identity → execucao → billing → os → -auth`. O OS lê o SSM deste serviço no `plan`.

## Comandos

```bash
terraform -chdir=infra init -backend=false
terraform -chdir=infra validate
terraform -chdir=infra test   # provider aws mockado, sem credencial
```

## Contrato com o CD (`servico-cd.yml@v1`)

O `infra/outputs.tf` precisa expor:

| Output | Tipo | Uso no CD |
|---|---|---|
| `ecr_repository_url` | `string` | Repositório da imagem (`mecanica-billing`) |
| `app_config` | `map(string)` | Vira o ConfigMap `mecanica-billing-config` |
| `app_secrets` | `map(string)`, `sensitive = true` | Vira o Secret `mecanica-billing-secret` |

- Backend S3 configurado pelo CD: bucket `mecanica-tfstate-<accountId>`, key `fiap-soat-mecanica-billing/terraform.tfstate`. O `backend.tf` fica só com `backend "s3" {}`.
- Provider AWS `~> 6.0`, `required_version = ">= 1.10.0, < 2.0.0"`.
- O CD passa `TF_VAR_mp_access_token` e `TF_VAR_mp_webhook_secret`. As duas variáveis são `sensitive`, sem default e não aceitam valor vazio: sem elas o `plan` falha.
- **Webhook do Mercado Pago (F6-j):** a URL `<api-url>/webhooks/mercadopago` muda a cada subida do API Gateway e **não é Terraform**. Ela é gravada na aplicação `mecanica-fase4` (MCP `save_webhook` ou painel) a cada janela, depois do CD do `-auth`.
- Nomes de filas, tópicos, RDS e SSM seguem a tabela 3.4 do plano da F1.

### Chaves de mensageria no `app_config`

As chaves do `app_config` viram variáveis de ambiente do pod (`envFrom`).

| Chave | Valor | Propriedade |
|---|---|---|
| `APP_MESSAGING_CONSUMIDOR_FILAS` | URLs das filas consumidas, separadas por vírgula | `app.messaging.consumidor.filas` |
| `APP_MESSAGING_DLQS` | URLs das DLQs dessas filas | `app.messaging.dlqs` (gauge `dlq.depth`) |
| `MESSAGING_DESTINO_<NOME>` | ARN do tópico ou URL da fila | declarada no `application.properties` do serviço como `app.messaging.destinos.<nome>=${MESSAGING_DESTINO_<NOME>:}` |

Os destinos usam variável explícita porque a ligação relaxada do Spring não preserva o `-` das chaves de mapa vindas de variáveis de ambiente (`execucao-comandos` viraria `execucao.comandos`). A role do pod precisa de `sns:Publish` nos tópicos e de `sqs:SendMessage`, `ReceiveMessage`, `DeleteMessage` e `GetQueueAttributes` nas filas.

Valores atuais do `app_config` (6 chaves): `SPRING_DATASOURCE_URL`, `AWS_REGION`, `JWT_PUBLIC_KEY` (PEM com `\n` escapado), `APP_MESSAGING_DLQS` (URL da DLQ), `APP_MESSAGING_CONSUMIDOR_FILAS` (URL da `billing-comandos`) e `MESSAGING_DESTINO_BILLING_EVENTOS` (ARN do tópico). O `app_secrets` (4 chaves) tem usuário e senha do banco, `MP_ACCESS_TOKEN` e `MP_WEBHOOK_SECRET`.
