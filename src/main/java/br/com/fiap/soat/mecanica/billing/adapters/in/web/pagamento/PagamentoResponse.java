package br.com.fiap.soat.mecanica.billing.adapters.in.web.pagamento;

import br.com.fiap.soat.mecanica.billing.domain.pagamento.Pagamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.StatusPagamento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PagamentoResponse(UUID id, UUID osId, UUID orcamentoId, BigDecimal valor, StatusPagamento status,
                                String link, String mpPaymentId, String motivo, Instant criadoEm,
                                Instant atualizadoEm) {

    public static PagamentoResponse de(Pagamento p) {
        return new PagamentoResponse(p.getId(), p.getOsId(), p.getOrcamentoId(), p.getValor(), p.getStatus(),
                p.getLink(), p.getMpPaymentId(), p.getMotivo(), p.getCriadoEm(), p.getAtualizadoEm());
    }
}
