package br.com.fiap.soat.mecanica.billing.application.saga.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Payload de {@code GerarOrcamento} (contrato do OS, docs de contratos consumidos). */
public record GerarOrcamentoPayload(UUID osId, UUID clienteId, List<ItemServico> itens, BigDecimal valorTotal) {

    public record ItemServico(UUID servicoId, String descricao, BigDecimal precoMaoDeObra, List<PecaOrcada> pecas) {
    }

    public record PecaOrcada(UUID pecaId, String descricao, int quantidade, BigDecimal valorUnitario) {
    }
}
