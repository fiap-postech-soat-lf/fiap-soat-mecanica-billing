module "db" {
  source = "git::https://github.com/fiap-postech-soat-lf/fiap-soat-mecanica-db.git//modules/rds-postgres?ref=v1"

  name_prefix = "mecanica-billing"
  # Sufixo _db evita palavra reservada de DBName (licao da F2); o mock do teste nao pega isso.
  db_name                    = "billing_db"
  vpc_id                     = data.aws_ssm_parameter.vpc_id.insecure_value
  subnet_ids                 = split(",", data.aws_ssm_parameter.subnet_ids.insecure_value)
  allowed_security_group_ids = [data.aws_ssm_parameter.node_security_group_id.insecure_value]
}
