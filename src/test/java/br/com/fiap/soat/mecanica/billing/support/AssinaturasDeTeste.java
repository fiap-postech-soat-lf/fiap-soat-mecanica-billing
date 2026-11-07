package br.com.fiap.soat.mecanica.billing.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/** Gera o x-signature do Mercado Pago com o segredo de teste (application-test.properties), por conta propria. */
public final class AssinaturasDeTeste {

    public static final String SEGREDO = "segredo-webhook-de-teste";

    private AssinaturasDeTeste() {
    }

    public static String assinar(String dataId, String requestId, String ts) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SEGREDO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String manifesto = "id:" + dataId.toLowerCase() + ";request-id:" + requestId + ";ts:" + ts + ";";
            return "ts=" + ts + ",v1=" + HexFormat.of().formatHex(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
