package br.com.fiap.soat.mecanica.billing.adapters.out.rest.mercadopago.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentResponse(String id, String status, @JsonProperty("status_detail") String statusDetail,
                              @JsonProperty("external_reference") String externalReference) {
}
