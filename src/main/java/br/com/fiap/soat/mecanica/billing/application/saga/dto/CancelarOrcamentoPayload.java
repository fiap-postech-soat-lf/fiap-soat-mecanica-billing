package br.com.fiap.soat.mecanica.billing.application.saga.dto;

import java.util.UUID;

/** Payload de {@code CancelarOrcamento} (compensacao do OS). */
public record CancelarOrcamentoPayload(UUID osId, UUID orcamentoId, String motivo) {
}
