package br.com.fiap.soat.mecanica.billing.adapters.in.web.webhook;

import br.com.fiap.soat.mecanica.billing.application.pagamento.usecase.RegistrarNotificacaoUseCase;
import br.com.fiap.soat.mecanica.billing.config.observability.BusinessEventLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recebe as notificacoes do Mercado Pago (F6-i). Rota publica: a autenticacao e a assinatura HMAC. Assinatura
 * ausente ou invalida -> 401 sem gravar nada (o Mercado Pago repete). {@code type} diferente de {@code payment}
 * -> 200 sem efeito. Senao, grava PENDENTE e responde 200 na hora; o processamento e do scheduler.
 */
@Hidden
@RestController
@RequestMapping("/webhooks/mercadopago")
public class WebhookMercadoPagoController {

    static final String METRICA = "webhook.recebido";
    static final String TIPO_PAGAMENTO = "payment";

    private final AssinaturaWebhook assinatura;
    private final RegistrarNotificacaoUseCase registrar;
    private final ObjectMapper objectMapper;
    private final MeterRegistry metricas;

    public WebhookMercadoPagoController(AssinaturaWebhook assinatura, RegistrarNotificacaoUseCase registrar,
                                        ObjectMapper objectMapper, MeterRegistry metricas) {
        this.assinatura = assinatura;
        this.registrar = registrar;
        this.objectMapper = objectMapper;
        this.metricas = metricas;
    }

    @PostMapping
    public ResponseEntity<Void> receber(
            @RequestHeader(name = "x-signature", required = false) String xSignature,
            @RequestHeader(name = "x-request-id", required = false) String xRequestId,
            @RequestParam(name = "data.id", required = false) String dataIdQuery,
            @RequestParam(name = "type", required = false) String tipoQuery,
            @RequestBody(required = false) String corpoBruto) {
        NotificacaoMercadoPago corpo = ler(corpoBruto);
        String dataId = primeiroPreenchido(dataIdQuery, corpo.dataId());
        if (!assinatura.valida(xSignature, xRequestId, dataId)) {
            BusinessEventLogger.webhookAssinaturaInvalida();
            metricas.counter(METRICA, "resultado", "assinatura_invalida").increment();
            return ResponseEntity.status(401).build();
        }
        String tipo = primeiroPreenchido(corpo.type(), tipoQuery);
        if (!TIPO_PAGAMENTO.equals(tipo) || dataId == null) {
            return resposta(tipo, corpo.action(), dataId, "ignorado");
        }
        registrar.executar(xRequestId, dataId, tipo, corpo.action());
        return resposta(tipo, corpo.action(), dataId, "aceito");
    }

    private ResponseEntity<Void> resposta(String tipo, String acao, String dataId, String resultado) {
        BusinessEventLogger.webhookRecebido(tipo, acao, dataId, resultado);
        metricas.counter(METRICA, "resultado", resultado).increment();
        return ResponseEntity.ok().build();
    }

    /** Corpo ilegivel nao derruba a validacao: sem corpo, o data.id so pode vir da query. */
    private NotificacaoMercadoPago ler(String corpoBruto) {
        if (corpoBruto == null || corpoBruto.isBlank()) {
            return new NotificacaoMercadoPago(null, null, null);
        }
        try {
            return objectMapper.readValue(corpoBruto, NotificacaoMercadoPago.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return new NotificacaoMercadoPago(null, null, null);
        }
    }

    private static String primeiroPreenchido(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null && !b.isBlank() ? b : null;
    }
}
