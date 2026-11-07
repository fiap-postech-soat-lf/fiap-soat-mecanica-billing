package br.com.fiap.soat.mecanica.billing.adapters.out.rest.mercadopago;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** {@code app.mercadopago.*} (secao 3.1 da F6). O token e o segredo vem do {@code app_secrets} e nunca de arquivo. */
@ConfigurationProperties("app.mercadopago")
public record MercadoPagoProperties(String baseUrl, String accessToken, Duration timeout, Retry retry) {

    public MercadoPagoProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank() ? "https://api.mercadopago.com" : baseUrl;
        accessToken = accessToken == null ? "" : accessToken;
        timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
        retry = retry == null ? new Retry(0, null, 0) : retry;
    }

    /** 3 tentativas, 500 ms x 2 (F6-b). */
    public record Retry(int maxTentativas, Duration esperaInicial, double multiplicador) {

        public Retry {
            maxTentativas = maxTentativas <= 0 ? 3 : maxTentativas;
            esperaInicial = esperaInicial == null ? Duration.ofMillis(500) : esperaInicial;
            multiplicador = multiplicador <= 0 ? 2.0 : multiplicador;
        }
    }
}
