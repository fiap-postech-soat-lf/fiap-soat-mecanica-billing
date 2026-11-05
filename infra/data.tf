# Publicados pelo -k8s (cluster.yml, ADR-F4-14). Sem eles o servico-cd nem chega aqui:
# o porteiro pula o deploy com o EKS desligado.
data "aws_ssm_parameter" "vpc_id" {
  name = "/mecanica/k8s/vpc-id"
}

data "aws_ssm_parameter" "subnet_ids" {
  name = "/mecanica/k8s/subnet-ids"
}

data "aws_ssm_parameter" "node_security_group_id" {
  name = "/mecanica/k8s/node-security-group-id"
}

# Publicada pelo Identity. Leitura direta: na ordem B4 (F4-a) o Identity sempre aplica antes.
data "aws_ssm_parameter" "jwt_public_key" {
  name = "/mecanica/identity/jwt-public-key"
}
