package br.com.fiap.soat.mecanica.billing.application.pagamento.usecase;

import br.com.fiap.soat.mecanica.billing.application.mensageria.OutboxPort;
import br.com.fiap.soat.mecanica.billing.application.pagamento.EmissorDeCobranca;
import br.com.fiap.soat.mecanica.billing.application.saga.RespostasSaga;
import br.com.fiap.soat.mecanica.billing.config.observability.BusinessEventLogger;
import br.com.fiap.soat.mecanica.billing.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.billing.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.Pagamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.PagamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.StatusPagamento;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Nova cobranca depois de uma recusa (F6-h): o vigente RECUSADO vira SUBSTITUIDO, nasce um PENDENTE com outra
 * preferencia e sai um CobrancaCriada com o sagaId do orcamento. O Mercado Pago e chamado antes de qualquer
 * escrita; se falhar, nada e gravado.
 */
@Service
public class NovaCobrancaUseCase {

    private final PagamentoRepository pagamentos;
    private final OrcamentoRepository orcamentos;
    private final EmissorDeCobranca emissor;
    private final RespostasSaga respostas;
    private final OutboxPort outbox;
    private final Clock clock;

    public NovaCobrancaUseCase(PagamentoRepository pagamentos, OrcamentoRepository orcamentos,
                               EmissorDeCobranca emissor, RespostasSaga respostas, OutboxPort outbox, Clock clock) {
        this.pagamentos = pagamentos;
        this.orcamentos = orcamentos;
        this.emissor = emissor;
        this.respostas = respostas;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public Pagamento executar(UUID osId) {
        Pagamento vigente = pagamentos.buscarVigentePorOs(osId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento nao encontrado para a OS: " + osId));
        if (vigente.getStatus() != StatusPagamento.RECUSADO) {
            throw new RegraNegocioException("Nova cobranca exige pagamento vigente RECUSADO (status atual: "
                    + vigente.getStatus() + ")");
        }
        Orcamento orcamento = orcamentos.buscarPorId(vigente.getOrcamentoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Orcamento do pagamento nao encontrado: " + vigente.getOrcamentoId()));

        Pagamento novo = emissor.emitir(orcamento);

        StatusPagamento de = vigente.getStatus();
        vigente.substituir(clock.instant());
        pagamentos.salvar(vigente);
        Pagamento salvo = pagamentos.salvar(novo);

        outbox.registrar(RespostasSaga.DESTINO,
                respostas.envelope(respostas.cobrancaCriada(salvo), orcamento.getSagaId(), osId));
        BusinessEventLogger.pagamentoStatus(vigente.getId(), osId, de, vigente.getStatus(), "NOVA_COBRANCA");
        BusinessEventLogger.pagamentoStatus(salvo.getId(), osId, null, salvo.getStatus(), null);
        BusinessEventLogger.cobrancaCriada(salvo.getId(), osId);
        return salvo;
    }
}
