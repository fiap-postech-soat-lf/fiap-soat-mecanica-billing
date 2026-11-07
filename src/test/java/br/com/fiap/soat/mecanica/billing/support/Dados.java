package br.com.fiap.soat.mecanica.billing.support;

import br.com.fiap.soat.mecanica.billing.domain.orcamento.ItemOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.Pagamento;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Massa de dados dos ITs: orcamentos e pagamentos novos, de OS e clientes unicos (os ITs compartilham o banco). */
public final class Dados {

    private Dados() {
    }

    public static Orcamento orcamento(UUID osId, UUID clienteId, Duration ttl) {
        return Orcamento.gerar(osId, UUID.randomUUID(), clienteId,
                List.of(ItemOrcamento.servico(UUID.randomUUID(), "Troca de oleo", new BigDecimal("80.00")),
                        ItemOrcamento.peca(UUID.randomUUID(), "Filtro de oleo", 2, new BigDecimal("39.90"))),
                new BigDecimal("159.80"), ttl, Instant.now());
    }

    public static Pagamento pagamento(Orcamento orcamento) {
        return Pagamento.criar(orcamento.getOsId(), orcamento.getId(), orcamento.getClienteId(),
                orcamento.getValorTotal(), Instant.now());
    }
}
