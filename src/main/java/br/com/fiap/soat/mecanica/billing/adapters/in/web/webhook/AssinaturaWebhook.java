package br.com.fiap.soat.mecanica.billing.adapters.in.web.webhook;

import br.com.fiap.soat.mecanica.billing.config.observability.BusinessEventLogger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Valida o {@code x-signature} do Mercado Pago (F6-i): {@code ts=<ts>,v1=<hex>}, onde v1 e o HMAC-SHA256, com o
 * segredo do webhook, do manifesto {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;}. Uma parte ausente
 * (data.id ou x-request-id) sai do manifesto. A comparacao e em tempo constante. Nem o header nem o segredo vao
 * para o log.
 */
@Component
public class AssinaturaWebhook {

    private final String segredo;

    public AssinaturaWebhook(@Value("${app.mercadopago.webhook-secret:}") String segredo) {
        this.segredo = segredo == null ? "" : segredo;
    }

    /**
     * @param dataId o {@code data.id} da notificacao (query param ou corpo); normalizado para minusculas
     * @return true so se a assinatura bate; sem segredo configurado, sempre false
     */
    public boolean valida(String xSignature, String xRequestId, String dataId) {
        if (segredo.isBlank()) {
            BusinessEventLogger.webhookSegredoAusente();
            return false;
        }
        String ts = null;
        String v1 = null;
        if (xSignature != null) {
            for (String parte : xSignature.split(",")) {
                int igual = parte.indexOf('=');
                if (igual < 0) {
                    continue;
                }
                String chave = parte.substring(0, igual).trim();
                String valor = parte.substring(igual + 1).trim();
                if ("ts".equals(chave)) {
                    ts = valor;
                } else if ("v1".equals(chave)) {
                    v1 = valor;
                }
            }
        }
        if (ts == null || ts.isEmpty() || v1 == null || v1.isEmpty()) {
            return false;
        }
        byte[] esperado = calcular(segredo, manifesto(dataId, xRequestId, ts));
        byte[] recebido = v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(HexFormat.of().formatHex(esperado).getBytes(StandardCharsets.UTF_8), recebido);
    }

    /** {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;} sem as partes ausentes. */
    static String manifesto(String dataId, String requestId, String ts) {
        StringBuilder manifesto = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            manifesto.append("id:").append(dataId.toLowerCase(Locale.ROOT)).append(';');
        }
        if (requestId != null && !requestId.isBlank()) {
            manifesto.append("request-id:").append(requestId).append(';');
        }
        return manifesto.append("ts:").append(ts).append(';').toString();
    }

    static byte[] calcular(String segredo, String manifesto) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponivel", e);
        }
    }
}
