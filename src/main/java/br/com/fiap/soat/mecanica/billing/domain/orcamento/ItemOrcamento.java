package br.com.fiap.soat.mecanica.billing.domain.orcamento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Item do orcamento. O servico entra com quantidade 1 e a mao de obra como valor unitario; cada peca,
 * com a sua quantidade e valor unitario.
 */
public record ItemOrcamento(UUID id, TipoItem tipo, UUID referenciaId, String descricao, int quantidade,
                            BigDecimal valorUnitario) {

    public static ItemOrcamento servico(UUID servicoId, String descricao, BigDecimal precoMaoDeObra) {
        return new ItemOrcamento(UUID.randomUUID(), TipoItem.SERVICO, servicoId, descricao, 1, precoMaoDeObra);
    }

    public static ItemOrcamento peca(UUID pecaId, String descricao, int quantidade, BigDecimal valorUnitario) {
        return new ItemOrcamento(UUID.randomUUID(), TipoItem.PECA, pecaId, descricao, quantidade, valorUnitario);
    }

    /** quantidade x valor unitario, com 2 casas e HALF_EVEN. */
    public BigDecimal valorTotal() {
        return valorUnitario.multiply(BigDecimal.valueOf(quantidade)).setScale(2, RoundingMode.HALF_EVEN);
    }
}
