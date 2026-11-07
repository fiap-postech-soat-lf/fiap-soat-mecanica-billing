package br.com.fiap.soat.mecanica.billing.application.pagamento.usecase;

import br.com.fiap.soat.mecanica.billing.application.pagamento.EventosPagamento;
import br.com.fiap.soat.mecanica.billing.application.pagamento.port.GatewayPagamentoPort;
import br.com.fiap.soat.mecanica.billing.application.pagamento.port.PagamentoMp;
import br.com.fiap.soat.mecanica.billing.config.observability.BusinessEventLogger;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.Pagamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.PagamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.StatusPagamento;
import br.com.fiap.soat.mecanica.billing.domain.webhook.WebhookEvento;
import br.com.fiap.soat.mecanica.billing.domain.webhook.WebhookEventoRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Processa as notificacoes PENDENTES (F6-i, tabela 4.1 da F6), uma transacao por evento: o evento fica travado
 * (FOR UPDATE SKIP LOCKED) ate o fim, e o efeito, o par (payment.id, status) e a outbox saem juntos.
 * O status vem sempre da consulta ao Mercado Pago, nunca do corpo do webhook.
 */
@Service
public class ProcessarNotificacaoPagamentoUseCase {

    static final int LOTE = 10;
    static final String METRICA_SUBSTITUIDO_APROVADO = "pagamento.substituido_aprovado";

    private final WebhookEventoRepository eventos;
    private final PagamentoRepository pagamentos;
    private final OrcamentoRepository orcamentos;
    private final GatewayPagamentoPort gateway;
    private final EventosPagamento eventosPagamento;
    private final TransactionTemplate transacao;
    private final MeterRegistry metricas;
    private final Clock clock;
    private final int maxTentativas;

    @SuppressWarnings("java:S107")
    public ProcessarNotificacaoPagamentoUseCase(WebhookEventoRepository eventos, PagamentoRepository pagamentos,
                                                OrcamentoRepository orcamentos, GatewayPagamentoPort gateway,
                                                EventosPagamento eventosPagamento, TransactionTemplate transacao,
                                                MeterRegistry metricas, Clock clock,
                                                @Value("${app.webhook.processador.max-tentativas:5}") int maxTentativas) {
        this.eventos = eventos;
        this.pagamentos = pagamentos;
        this.orcamentos = orcamentos;
        this.gateway = gateway;
        this.eventosPagamento = eventosPagamento;
        this.transacao = transacao;
        this.metricas = metricas;
        this.clock = clock;
        this.maxTentativas = maxTentativas;
    }

    /** @return quantos eventos foram tratados nesta rodada (ate {@value #LOTE}) */
    public int executar() {
        int tratados = 0;
        while (tratados < LOTE && Boolean.TRUE.equals(transacao.execute(status -> processarProximo()))) {
            tratados++;
        }
        return tratados;
    }

    /** Deve rodar dentro de uma transacao. @return false quando nao ha evento elegivel */
    boolean processarProximo() {
        Instant agora = clock.instant();
        return eventos.travarProximoPendente(agora).map(evento -> {
            processar(evento, agora);
            return true;
        }).orElse(false);
    }

    void processar(WebhookEvento evento, Instant agora) {
        PagamentoMp mp;
        try {
            mp = gateway.consultarPagamento(evento.getMpPaymentId());
        } catch (RuntimeException e) {
            registrarFalha(evento, e, agora);
            return;
        }
        String statusMp = mp.status() == null ? "" : mp.status();
        if (eventos.statusJaAplicado(evento.getMpPaymentId(), statusMp)) {
            evento.processado(statusMp);
            eventos.salvar(evento);
            return;
        }
        switch (statusMp) {
            case "approved", "rejected", "cancelled" -> aplicar(evento, mp, statusMp, agora);
            case "pending", "in_process", "authorized" -> concluir(evento, statusMp, false);
            default -> {
                BusinessEventLogger.statusMpIgnorado(evento.getMpPaymentId(), statusMp);
                concluir(evento, statusMp, true);
            }
        }
    }

    private void aplicar(WebhookEvento evento, PagamentoMp mp, String statusMp, Instant agora) {
        Pagamento pagamento = localizar(mp.externalReference()).orElse(null);
        if (pagamento == null) {
            BusinessEventLogger.pagamentoDesconhecido(evento.getMpPaymentId(), mp.externalReference());
            concluir(evento, statusMp, true);
            return;
        }
        boolean aprovado = "approved".equals(statusMp);
        String mpPaymentId = evento.getMpPaymentId();
        if (pagamento.aceitaResultado()) {
            StatusPagamento de = pagamento.getStatus();
            if (aprovado) {
                pagamento.aprovar(mpPaymentId, agora);
            } else {
                pagamento.recusar(mpPaymentId, motivo(mp, statusMp), agora);
            }
            Pagamento salvo = pagamentos.salvar(pagamento);
            UUID sagaId = sagaDe(salvo);
            if (aprovado) {
                eventosPagamento.confirmado(salvo, sagaId);
            } else {
                eventosPagamento.recusado(salvo, sagaId);
            }
            BusinessEventLogger.pagamentoStatus(salvo.getId(), salvo.getOsId(), de, salvo.getStatus(), salvo.getMotivo());
        } else if (pagamento.getStatus() == StatusPagamento.SUBSTITUIDO) {
            BusinessEventLogger.pagamentoEmCobrancaSubstituida(pagamento.getId(), pagamento.getOsId(), mpPaymentId,
                    statusMp);
            if (aprovado) {
                metricas.counter(METRICA_SUBSTITUIDO_APROVADO).increment();
            }
        } else {
            BusinessEventLogger.pagamentoJaAprovado(pagamento.getId(), pagamento.getOsId(), mpPaymentId, statusMp);
        }
        eventos.registrarStatusAplicado(mpPaymentId, statusMp, pagamento.getId(), agora);
        concluir(evento, statusMp, false);
    }

    /** external_reference = pagamentoId (F6-g); qualquer outro valor nao e nosso. */
    private java.util.Optional<Pagamento> localizar(String externalReference) {
        if (externalReference == null || externalReference.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            return pagamentos.buscarPorId(UUID.fromString(externalReference.trim()));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }

    private UUID sagaDe(Pagamento pagamento) {
        return orcamentos.buscarPorId(pagamento.getOrcamentoId()).map(Orcamento::getSagaId)
                .orElseThrow(() -> new IllegalStateException(
                        "Orcamento do pagamento nao encontrado: " + pagamento.getOrcamentoId()));
    }

    private static String motivo(PagamentoMp mp, String statusMp) {
        return mp.statusDetail() == null || mp.statusDetail().isBlank() ? statusMp : mp.statusDetail();
    }

    private void concluir(WebhookEvento evento, String statusMp, boolean ignorado) {
        if (ignorado) {
            evento.ignorado(statusMp);
        } else {
            evento.processado(statusMp);
        }
        eventos.salvar(evento);
    }

    /** O Mercado Pago falhou: nova tentativa com backoff e, na ultima, ERRO. Nada mais foi gravado ainda. */
    private void registrarFalha(WebhookEvento evento, RuntimeException erro, Instant agora) {
        boolean esgotado = evento.falhou(erro.getClass().getSimpleName() + ": " + erro.getMessage(), agora,
                maxTentativas);
        eventos.salvar(evento);
        BusinessEventLogger.webhookProcessamentoFalhou(evento.getMpPaymentId(), evento.getTentativas(), esgotado,
                erro.getClass().getSimpleName());
    }
}
