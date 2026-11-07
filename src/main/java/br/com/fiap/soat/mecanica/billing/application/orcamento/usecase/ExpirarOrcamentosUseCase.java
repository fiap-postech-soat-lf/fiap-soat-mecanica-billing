package br.com.fiap.soat.mecanica.billing.application.orcamento.usecase;

import br.com.fiap.soat.mecanica.billing.application.orcamento.EventosOrcamento;
import br.com.fiap.soat.mecanica.billing.config.observability.BusinessEventLogger;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.StatusOrcamento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Expira os GERADO vencidos (F6-f): ate 20 por rodada, travados com FOR UPDATE SKIP LOCKED para as replicas
 * nao expirarem o mesmo orcamento. Cada rodada e uma transacao so (orcamento e OrcamentoExpirado juntos).
 */
@Service
public class ExpirarOrcamentosUseCase {

    static final int LOTE = 20;

    private final OrcamentoRepository orcamentos;
    private final EventosOrcamento eventos;
    private final TransactionTemplate transacao;
    private final Clock clock;

    public ExpirarOrcamentosUseCase(OrcamentoRepository orcamentos, EventosOrcamento eventos,
                                    TransactionTemplate transacao, Clock clock) {
        this.orcamentos = orcamentos;
        this.eventos = eventos;
        this.transacao = transacao;
        this.clock = clock;
    }

    /** @return quantos orcamentos foram expirados nesta rodada */
    public int executar() {
        Integer expirados = transacao.execute(status -> {
            Instant agora = clock.instant();
            List<Orcamento> vencidos = orcamentos.travarVencidos(agora, LOTE);
            for (Orcamento orcamento : vencidos) {
                StatusOrcamento de = orcamento.getStatus();
                orcamento.expirar(agora);
                Orcamento salvo = orcamentos.salvar(orcamento);
                eventos.expirado(salvo);
                BusinessEventLogger.orcamentoStatus(salvo.getId(), salvo.getOsId(), de, salvo.getStatus(), null);
            }
            return vencidos.size();
        });
        return expirados == null ? 0 : expirados;
    }
}
