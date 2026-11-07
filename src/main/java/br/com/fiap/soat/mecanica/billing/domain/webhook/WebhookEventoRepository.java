package br.com.fiap.soat.mecanica.billing.domain.webhook;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEventoRepository {

    WebhookEvento salvar(WebhookEvento evento);

    /**
     * O PENDENTE mais antigo ja elegivel ({@code proxima_tentativa_em <= agora}), travado com
     * FOR UPDATE SKIP LOCKED: as replicas nao processam o mesmo evento. Exige transacao.
     */
    Optional<WebhookEvento> travarProximoPendente(Instant agora);

    /** O par (payment.id, status) ja foi aplicado (idempotencia da F6-i)? */
    boolean statusJaAplicado(String mpPaymentId, String status);

    /** Registra o par (payment.id, status) na transacao do efeito. */
    void registrarStatusAplicado(String mpPaymentId, String status, UUID pagamentoId, Instant agora);
}
