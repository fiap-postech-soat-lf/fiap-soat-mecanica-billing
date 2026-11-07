package br.com.fiap.soat.mecanica.billing.adapters.in.web.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Corpo do webhook (so os campos usados). O status do pagamento NAO vem daqui (F6-i). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificacaoMercadoPago(String type, String action, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String id) {
    }

    public String dataId() {
        return data == null ? null : data.id();
    }
}
