package br.com.fiap.soat.mecanica.billing.adapters.in.web.orcamento;

import br.com.fiap.soat.mecanica.billing.domain.orcamento.ItemOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.StatusOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.TipoItem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrcamentoResponse(UUID id, UUID osId, UUID clienteId, StatusOrcamento status, BigDecimal valorTotal,
                                Instant expiraEm, Instant criadoEm, Instant decididoEm, UUID decididoPor,
                                String motivo, List<ItemResponse> itens) {

    public record ItemResponse(UUID id, TipoItem tipo, UUID referenciaId, String descricao, int quantidade,
                               BigDecimal valorUnitario, BigDecimal valorTotal) {

        static ItemResponse de(ItemOrcamento i) {
            return new ItemResponse(i.id(), i.tipo(), i.referenciaId(), i.descricao(), i.quantidade(),
                    i.valorUnitario(), i.valorTotal());
        }
    }

    public static OrcamentoResponse de(Orcamento o) {
        return new OrcamentoResponse(o.getId(), o.getOsId(), o.getClienteId(), o.getStatus(), o.getValorTotal(),
                o.getExpiraEm(), o.getCriadoEm(), o.getDecididoEm(), o.getDecididoPor(), o.getMotivo(),
                o.getItens().stream().map(ItemResponse::de).toList());
    }
}
