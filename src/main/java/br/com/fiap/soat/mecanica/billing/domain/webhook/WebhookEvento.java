package br.com.fiap.soat.mecanica.billing.domain.webhook;

import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Notificacao do Mercado Pago recebida e ainda nao (ou ja) processada (F6-i). O corpo nunca e guardado: o status
 * do pagamento vem sempre da consulta ao Mercado Pago.
 */
@Getter
public class WebhookEvento {

    private static final int TAMANHO_MAXIMO_ERRO = 500;

    private final UUID id;
    private final String requestId;
    private final String mpPaymentId;
    private final String tipo;
    private final String acao;
    private final Instant recebidoEm;
    private StatusWebhook status;
    private int tentativas;
    private Instant proximaTentativaEm;
    private String statusMp;
    private String ultimoErro;

    @SuppressWarnings("java:S107")
    public WebhookEvento(UUID id, String requestId, String mpPaymentId, String tipo, String acao, Instant recebidoEm,
                         StatusWebhook status, int tentativas, Instant proximaTentativaEm, String statusMp,
                         String ultimoErro) {
        this.id = id;
        this.requestId = requestId;
        this.mpPaymentId = mpPaymentId;
        this.tipo = tipo;
        this.acao = acao;
        this.recebidoEm = recebidoEm;
        this.status = status;
        this.tentativas = tentativas;
        this.proximaTentativaEm = proximaTentativaEm;
        this.statusMp = statusMp;
        this.ultimoErro = ultimoErro;
    }

    /** Evento novo: PENDENTE e elegivel ao processador na hora. */
    public static WebhookEvento receber(String requestId, String mpPaymentId, String tipo, String acao, Instant agora) {
        return new WebhookEvento(UUID.randomUUID(), requestId, mpPaymentId, tipo, acao, agora,
                StatusWebhook.PENDENTE, 0, agora, null, null);
    }

    public void processado(String statusDoMp) {
        concluir(StatusWebhook.PROCESSADO, statusDoMp);
    }

    public void ignorado(String statusDoMp) {
        concluir(StatusWebhook.IGNORADO, statusDoMp);
    }

    /**
     * O Mercado Pago falhou: nova tentativa em 2^tentativas segundos e, na ultima, ERRO.
     *
     * @return true se esta foi a ultima tentativa (o evento virou ERRO)
     */
    public boolean falhou(String erro, Instant agora, int maxTentativas) {
        tentativas++;
        ultimoErro = erro == null ? null : erro.substring(0, Math.min(erro.length(), TAMANHO_MAXIMO_ERRO));
        if (tentativas >= maxTentativas) {
            status = StatusWebhook.ERRO;
            proximaTentativaEm = null;
            return true;
        }
        proximaTentativaEm = agora.plus(Duration.ofSeconds(1L << tentativas));
        return false;
    }

    private void concluir(StatusWebhook novo, String statusDoMp) {
        status = novo;
        statusMp = statusDoMp;
        proximaTentativaEm = null;
    }
}
