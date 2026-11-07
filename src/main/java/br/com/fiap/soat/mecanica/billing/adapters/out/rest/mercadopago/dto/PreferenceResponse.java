package br.com.fiap.soat.mecanica.billing.adapters.out.rest.mercadopago.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PreferenceResponse(String id, @JsonProperty("init_point") String initPoint,
                                 @JsonProperty("sandbox_init_point") String sandboxInitPoint) {
}
