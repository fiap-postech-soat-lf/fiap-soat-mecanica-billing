package br.com.fiap.soat.mecanica.billing.adapters.out.rest.mercadopago.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/** Corpo de {@code POST /checkout/preferences}: um item so e sem {@code notification_url} (F6-g). */
public record PreferenceRequest(List<Item> items, @JsonProperty("external_reference") String externalReference) {

    public record Item(String title, int quantity, @JsonProperty("currency_id") String currencyId,
                       @JsonProperty("unit_price") BigDecimal unitPrice) {
    }

    public static PreferenceRequest de(String titulo, BigDecimal valor, String externalReference) {
        return new PreferenceRequest(List.of(new Item(titulo, 1, "BRL", valor)), externalReference);
    }
}
