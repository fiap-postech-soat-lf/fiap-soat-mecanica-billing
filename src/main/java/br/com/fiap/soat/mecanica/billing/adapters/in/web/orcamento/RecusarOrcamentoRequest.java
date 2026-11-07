package br.com.fiap.soat.mecanica.billing.adapters.in.web.orcamento;

import jakarta.validation.constraints.Size;

public record RecusarOrcamentoRequest(@Size(max = 500) String motivo) {
}
