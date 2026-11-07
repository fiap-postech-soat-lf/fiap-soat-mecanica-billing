package br.com.fiap.soat.mecanica.billing.application.saga.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Payload de {@code SolicitarPagamento} (consumido no T04). */
public record SolicitarPagamentoPayload(UUID osId, UUID orcamentoId, BigDecimal valor) {
}
