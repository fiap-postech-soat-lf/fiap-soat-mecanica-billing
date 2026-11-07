package br.com.fiap.soat.mecanica.billing.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

/**
 * Stubs do Mercado Pago por OS: os ITs compartilham um WireMock, entao cada stub casa pelo titulo da
 * preferencia ("Ordem de serviço &lt;osId curto&gt;").
 */
public final class MercadoPagoStubs {

    private MercadoPagoStubs() {
    }

    public static String titulo(UUID osId) {
        return "Ordem de serviço " + osId.toString().substring(0, 8);
    }

    private static MappingBuilder preferenciaDaOs(UUID osId) {
        return post("/checkout/preferences").withRequestBody(matchingJsonPath("$.items[0].title", containing(osId.toString().substring(0, 8))));
    }

    public static StubMapping preferenciaCriada(WireMockServer mp, UUID osId, String prefId) {
        return mp.stubFor(preferenciaDaOs(osId).willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"" + prefId + "\",\"init_point\":\"https://www.mercadopago.com.br/checkout/v1/redirect?pref_id="
                        + prefId + "\",\"sandbox_init_point\":\"https://sandbox.mercadopago.com.br/checkout/v1/redirect?pref_id="
                        + prefId + "\"}")));
    }

    public static StubMapping preferenciaComErro(WireMockServer mp, UUID osId, int status) {
        return mp.stubFor(preferenciaDaOs(osId).willReturn(aResponse().withStatus(status)));
    }

    public static int chamadasDePreferencia(WireMockServer mp, UUID osId) {
        return mp.findAll(postRequestedFor(urlEqualTo("/checkout/preferences"))
                .withRequestBody(containing(osId.toString().substring(0, 8)))).size();
    }

    /** GET /v1/payments/{id}: o pagamento como o Mercado Pago o devolve (id numerico, external_reference = pagamentoId). */
    public static StubMapping pagamentoConsultado(WireMockServer mp, String mpPaymentId, String status,
                                                  String statusDetail, UUID externalReference) {
        return mp.stubFor(get(urlEqualTo("/v1/payments/" + mpPaymentId)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":" + mpPaymentId + ",\"status\":\"" + status + "\",\"status_detail\":\""
                        + statusDetail + "\",\"external_reference\":\"" + externalReference + "\"}")));
    }

    public static StubMapping pagamentoComErro(WireMockServer mp, String mpPaymentId, int status) {
        return mp.stubFor(get(urlEqualTo("/v1/payments/" + mpPaymentId)).willReturn(aResponse().withStatus(status)));
    }

    public static int consultasDePagamento(WireMockServer mp, String mpPaymentId) {
        return mp.findAll(getRequestedFor(urlEqualTo("/v1/payments/" + mpPaymentId))).size();
    }
}
