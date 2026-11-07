package br.com.fiap.soat.mecanica.billing.application.saga;

import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.billing.application.mensageria.OutboxPort;
import br.com.fiap.soat.mecanica.billing.application.saga.RespostasSaga.Resposta;
import br.com.fiap.soat.mecanica.billing.application.saga.dto.CancelarOrcamentoPayload;
import br.com.fiap.soat.mecanica.billing.application.saga.dto.GerarOrcamentoPayload;
import br.com.fiap.soat.mecanica.billing.config.observability.BusinessEventLogger;
import br.com.fiap.soat.mecanica.billing.config.observability.Mdc;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.ItemOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoInconsistenteException;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.StatusOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.PagamentoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lado do Billing na Saga do OS (tabela 4.1 da F6). Roda dentro da transacao do ProcessadorMensagemSql: o
 * orcamento, a linha de comando_atendido e a resposta na outbox sao confirmados juntos, ou nenhum.
 * <p>
 * Antes de aplicar um GerarOrcamento, consulta a cadeia de ids da OS (F6-c): um reenvio reemite a resposta
 * guardada, sem efeito de negocio. O MDC e aberto com os ids do envelope para o MensagemFactory herdar o
 * causationId mesmo quando nao ha o SqsConsumidor na frente.
 */
@Service
public class ParticipanteSaga {

    public static final String GERAR_ORCAMENTO = "GerarOrcamento";
    public static final String CANCELAR_ORCAMENTO = "CancelarOrcamento";
    public static final String SOLICITAR_PAGAMENTO = "SolicitarPagamento";
    static final String MOTIVO_SUBSTITUIDO = "SUBSTITUIDO";

    private final OrcamentoRepository orcamentos;
    private final PagamentoRepository pagamentos;
    private final ComandosAtendidos comandos;
    private final RespostasSaga respostas;
    private final OutboxPort outbox;
    private final Clock clock;
    private final Duration ttl;

    public ParticipanteSaga(OrcamentoRepository orcamentos, PagamentoRepository pagamentos,
                            ComandosAtendidos comandos, RespostasSaga respostas, OutboxPort outbox, Clock clock,
                            @Value("${app.orcamento.ttl}") Duration ttl) {
        this.orcamentos = orcamentos;
        this.pagamentos = pagamentos;
        this.comandos = comandos;
        this.respostas = respostas;
        this.outbox = outbox;
        this.clock = clock;
        this.ttl = ttl;
    }

    /** {@code GerarOrcamento}: OrcamentoGerado, ou OrcamentoFalhou se os itens ou o total forem inconsistentes. */
    public void gerarOrcamento(MensagemEnvelope envelope, GerarOrcamentoPayload comando) {
        try (var ignored = contexto(envelope)) {
            UUID osId = comando.osId();
            Optional<ComandoAtendido> guardado = comandos.buscar(osId, GERAR_ORCAMENTO)
                    .filter(c -> c.contem(envelope.messageId(), envelope.causationId()));
            if (guardado.isPresent()) {
                reemitir(osId, GERAR_ORCAMENTO, envelope, guardado.get());
                return;
            }
            Resposta resposta = aplicarGeracao(comando, envelope);
            comandos.registrar(osId, GERAR_ORCAMENTO, envelope.messageId(), resposta);
            outbox.registrar(RespostasSaga.DESTINO, respostas.envelope(resposta, envelope.sagaId(), osId));
        }
    }

    /**
     * {@code CancelarOrcamento}: pelo orcamentoId do payload, entao um cancelamento que chega depois da nova
     * rodada so atinge o orcamento antigo. Sem resposta e sem linha em comando_atendido: idempotente pelo estado.
     */
    public void cancelarOrcamento(MensagemEnvelope envelope, CancelarOrcamentoPayload comando) {
        try (var ignored = contexto(envelope)) {
            Optional<Orcamento> encontrado = orcamentos.buscarPorId(comando.orcamentoId())
                    .filter(o -> o.getOsId().equals(comando.osId()));
            if (encontrado.isEmpty()) {
                BusinessEventLogger.cancelamentoSemEfeito(comando.osId(), comando.orcamentoId(), "inexistente");
                return;
            }
            Orcamento orcamento = encontrado.get();
            StatusOrcamento de = orcamento.getStatus();
            if (de == StatusOrcamento.APROVADO && pagamentos.buscarVigentePorOs(orcamento.getOsId()).isPresent()) {
                BusinessEventLogger.cancelamentoSemEfeito(orcamento.getOsId(), orcamento.getId(),
                        "aprovado com pagamento");
                return;
            }
            if (!orcamento.cancelar(comando.motivo())) {
                BusinessEventLogger.cancelamentoSemEfeito(orcamento.getOsId(), orcamento.getId(), "status " + de);
                return;
            }
            orcamentos.salvar(orcamento);
            BusinessEventLogger.orcamentoStatus(orcamento.getId(), orcamento.getOsId(), de, orcamento.getStatus(),
                    comando.motivo());
        }
    }

    // ---- apoio -------------------------------------------------------------------------------------

    private Resposta aplicarGeracao(GerarOrcamentoPayload comando, MensagemEnvelope envelope) {
        UUID osId = comando.osId();
        Optional<Orcamento> vivo = orcamentos.buscarVivoPorOs(osId);
        if (vivo.isPresent() && vivo.get().getStatus() == StatusOrcamento.APROVADO) {
            return falhou(osId, "Ja existe orcamento aprovado para a OS: " + vivo.get().getId());
        }
        Orcamento novo;
        try {
            novo = Orcamento.gerar(osId, envelope.sagaId(), comando.clienteId(), itens(comando),
                    comando.valorTotal(), ttl, clock.instant());
        } catch (OrcamentoInconsistenteException e) {
            return falhou(osId, e.getMessage());
        }
        vivo.ifPresent(anterior -> substituir(anterior));
        orcamentos.salvar(novo);
        BusinessEventLogger.orcamentoStatus(novo.getId(), osId, null, novo.getStatus(), null);
        return respostas.orcamentoGerado(novo);
    }

    /** Nova rodada com um GERADO anterior: cancela o anterior antes de gravar o novo (indice unico parcial, F6-d). */
    private void substituir(Orcamento anterior) {
        StatusOrcamento de = anterior.getStatus();
        anterior.cancelar(MOTIVO_SUBSTITUIDO);
        orcamentos.salvar(anterior);
        BusinessEventLogger.orcamentoStatus(anterior.getId(), anterior.getOsId(), de, anterior.getStatus(),
                MOTIVO_SUBSTITUIDO);
    }

    private Resposta falhou(UUID osId, String motivo) {
        BusinessEventLogger.orcamentoFalhou(osId, motivo);
        return respostas.orcamentoFalhou(osId, motivo);
    }

    /** O servico entra com quantidade 1 e a mao de obra como valor; cada peca, com a sua quantidade e valor. */
    private static List<ItemOrcamento> itens(GerarOrcamentoPayload comando) {
        List<ItemOrcamento> itens = new ArrayList<>();
        if (comando.itens() == null) {
            return itens;
        }
        comando.itens().forEach(servico -> {
            itens.add(ItemOrcamento.servico(servico.servicoId(), servico.descricao(), servico.precoMaoDeObra()));
            if (servico.pecas() != null) {
                servico.pecas().forEach(p -> itens.add(
                        ItemOrcamento.peca(p.pecaId(), p.descricao(), p.quantidade(), p.valorUnitario())));
            }
        });
        return itens;
    }

    private void reemitir(UUID osId, String tipoComando, MensagemEnvelope envelope, ComandoAtendido guardado) {
        comandos.acrescentar(osId, tipoComando, envelope.messageId());
        Resposta resposta = new Resposta(guardado.tipoResposta(), guardado.payloadResposta());
        outbox.registrar(RespostasSaga.DESTINO, respostas.envelope(resposta, envelope.sagaId(), osId));
        BusinessEventLogger.comandoReemitido(osId, tipoComando, guardado.tipoResposta());
    }

    private static Mdc.Escopo contexto(MensagemEnvelope envelope) {
        Map<String, Object> valores = new LinkedHashMap<>();
        valores.put(Mdc.CORRELATION_ID, envelope.correlationId());
        valores.put(Mdc.SAGA_ID, envelope.sagaId());
        valores.put(Mdc.OS_ID, envelope.osId());
        valores.put(Mdc.MESSAGE_ID, envelope.messageId());
        return Mdc.abrir(valores);
    }
}
