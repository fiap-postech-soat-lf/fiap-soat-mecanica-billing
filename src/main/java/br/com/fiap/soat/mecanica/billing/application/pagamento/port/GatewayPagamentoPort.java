package br.com.fiap.soat.mecanica.billing.application.pagamento.port;

import java.math.BigDecimal;
import java.util.UUID;

/** Porta do gateway de pagamento (Mercado Pago, F6-b). */
public interface GatewayPagamentoPort {

    /**
     * Cria a preferencia do Checkout Pro com {@code external_reference = pagamentoId}. A chamada e idempotente
     * pelo {@code pagamentoId}.
     *
     * @throws br.com.fiap.soat.mecanica.billing.application.pagamento.exception.MercadoPagoIndisponivelException I/O, timeout ou 5xx depois dos retries
     * @throws br.com.fiap.soat.mecanica.billing.application.pagamento.exception.MercadoPagoTokenAusenteException sem access token configurado
     */
    Preferencia criarPreferencia(UUID pagamentoId, String titulo, BigDecimal valor);

    /** Consulta o pagamento no Mercado Pago (usado pelo processamento do webhook, T06). */
    PagamentoMp consultarPagamento(String mpPaymentId);
}
