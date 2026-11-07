package br.com.fiap.soat.mecanica.billing.adapters.out.rest.mercadopago;

import br.com.fiap.soat.mecanica.billing.application.pagamento.exception.MercadoPagoIndisponivelException;
import br.com.fiap.soat.mecanica.billing.application.pagamento.exception.MercadoPagoTokenAusenteException;
import br.com.fiap.soat.mecanica.billing.application.pagamento.port.PagamentoMp;
import br.com.fiap.soat.mecanica.billing.application.pagamento.port.Preferencia;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O cliente contra um WireMock: corpo, cabecalhos, retry, timeout e erros (F6-b). */
class MercadoPagoClientTest {

    static final String TOKEN = "APP_USR-token-de-teste";
    static final String PREFERENCIA_OK = """
            {"id":"pref-1","init_point":"https://mp/init","sandbox_init_point":"https://mp/sandbox","extra":"ignorado"}""";

    static WireMockServer wireMock;
    SimpleMeterRegistry metricas;

    @BeforeAll
    static void iniciar() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void parar() {
        wireMock.stop();
    }

    @BeforeEach
    void preparar() {
        wireMock.resetAll();
        metricas = new SimpleMeterRegistry();
    }

    @AfterEach
    void limparContexto() {
        RequestContextHolder.resetRequestAttributes();
    }

    private MercadoPagoClient cliente(String token, Duration timeout) {
        MercadoPagoProperties props = new MercadoPagoProperties(wireMock.baseUrl(), token, timeout,
                new MercadoPagoProperties.Retry(3, Duration.ofMillis(10), 2));
        RestClient restClient = new MercadoPagoClientConfig().mercadoPagoRestClient(RestClient.builder(), props);
        return new MercadoPagoClient(restClient, props, metricas);
    }

    private double erros(String operacao) {
        var contador = metricas.find(MercadoPagoClient.METRICA_ERROS).tag("operacao", operacao).counter();
        return contador == null ? 0 : contador.count();
    }

    @Test
    void criaAPreferenciaComUmItemEmBrlSemNotificationUrlComBearerEChaveDeIdempotencia() {
        UUID pagamentoId = UUID.randomUUID();
        wireMock.stubFor(post("/checkout/preferences").willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json").withBody(PREFERENCIA_OK)));

        Preferencia preferencia = cliente(TOKEN, Duration.ofSeconds(5))
                .criarPreferencia(pagamentoId, "Ordem de serviço 7d1c2a4e", new BigDecimal("159.80"));

        assertThat(preferencia).isEqualTo(new Preferencia("pref-1", "https://mp/init", "https://mp/sandbox"));
        wireMock.verify(1, postRequestedFor(urlEqualTo("/checkout/preferences"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN))
                .withHeader("X-Idempotency-Key", equalTo(pagamentoId.toString()))
                .withRequestBody(equalToJson("""
                        {"items":[{"title":"Ordem de serviço 7d1c2a4e","quantity":1,"currency_id":"BRL",
                        "unit_price":159.80}],"external_reference":"%s"}""".formatted(pagamentoId), true, false)));
        assertThat(wireMock.getAllServeEvents().get(0).getRequest().getBodyAsString())
                .doesNotContain("notification_url");
    }

    @Test
    void dois500SeguidosDeUm201Funcionam() {
        wireMock.stubFor(post("/checkout/preferences").inScenario("retry").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(500)).willSetStateTo("segundo"));
        wireMock.stubFor(post("/checkout/preferences").inScenario("retry").whenScenarioStateIs("segundo")
                .willReturn(aResponse().withStatus(500)).willSetStateTo("terceiro"));
        wireMock.stubFor(post("/checkout/preferences").inScenario("retry").whenScenarioStateIs("terceiro")
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody(PREFERENCIA_OK)));

        Preferencia preferencia = cliente(TOKEN, Duration.ofSeconds(5))
                .criarPreferencia(UUID.randomUUID(), "t", BigDecimal.TEN);

        assertThat(preferencia.id()).isEqualTo("pref-1");
        wireMock.verify(3, postRequestedFor(urlEqualTo("/checkout/preferences")));
        assertThat(erros("preferencia")).isZero();
    }

    @Test
    void tresVezes503EsgotaOsRetriesEViraIndisponivel() {
        wireMock.stubFor(post("/checkout/preferences").willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> cliente(TOKEN, Duration.ofSeconds(5))
                .criarPreferencia(UUID.randomUUID(), "t", BigDecimal.TEN))
                .isInstanceOf(MercadoPagoIndisponivelException.class);

        wireMock.verify(3, postRequestedFor(urlEqualTo("/checkout/preferences")));
        assertThat(erros("preferencia")).isEqualTo(1);
    }

    @Test
    void respostaMaisLentaQueOTimeoutEReTentadaEDepoisIndisponivel() {
        wireMock.stubFor(post("/checkout/preferences")
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody(PREFERENCIA_OK).withFixedDelay(1500)));

        assertThatThrownBy(() -> cliente(TOKEN, Duration.ofMillis(300))
                .criarPreferencia(UUID.randomUUID(), "t", BigDecimal.TEN))
                .isInstanceOf(MercadoPagoIndisponivelException.class);

        wireMock.verify(3, postRequestedFor(urlEqualTo("/checkout/preferences")));
    }

    @Test
    void erro401NaoERepetidoENaoVazaOToken() {
        wireMock.stubFor(post("/checkout/preferences")
                .willReturn(aResponse().withStatus(401).withHeader("Content-Type", "application/json").withBody("{\"message\":\"invalid access token\"}")));

        assertThatThrownBy(() -> cliente(TOKEN, Duration.ofSeconds(5))
                .criarPreferencia(UUID.randomUUID(), "t", BigDecimal.TEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 401").hasMessageContaining("invalid access token")
                .hasMessageNotContaining(TOKEN);

        wireMock.verify(1, postRequestedFor(urlEqualTo("/checkout/preferences")));
        assertThat(erros("preferencia")).isEqualTo(1);
    }

    @Test
    void corpoDeErroGrandeEResumido() {
        wireMock.stubFor(post("/checkout/preferences")
                .willReturn(aResponse().withStatus(400).withHeader("Content-Type", "text/plain").withBody("x".repeat(1000))));

        assertThatThrownBy(() -> cliente(TOKEN, Duration.ofSeconds(5))
                .criarPreferencia(UUID.randomUUID(), "t", BigDecimal.TEN))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(e -> assertThat(e.getMessage().length()).isLessThan(400));
    }

    @Test
    void respostaSemIdEErro() {
        wireMock.stubFor(post("/checkout/preferences").willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody("{}")));

        assertThatThrownBy(() -> cliente(TOKEN, Duration.ofSeconds(5))
                .criarPreferencia(UUID.randomUUID(), "t", BigDecimal.TEN))
                .isInstanceOf(IllegalStateException.class);
        assertThat(erros("preferencia")).isEqualTo(1);
    }

    @Test
    void tokenVazioNaoFazNenhumaChamada() {
        assertThatThrownBy(() -> cliente("", Duration.ofSeconds(5))
                .criarPreferencia(UUID.randomUUID(), "t", BigDecimal.TEN))
                .isInstanceOf(MercadoPagoTokenAusenteException.class);
        assertThatThrownBy(() -> cliente("  ", Duration.ofSeconds(5)).consultarPagamento("1"))
                .isInstanceOf(MercadoPagoTokenAusenteException.class);

        assertThat(wireMock.getAllServeEvents()).isEmpty();
        assertThat(erros("preferencia")).isEqualTo(1);
        assertThat(erros("pagamento")).isEqualTo(1);
    }

    @Test
    void oJwtDoUsuarioNuncaChegaAoMercadoPago() {
        MockHttpServletRequest requisicao = new MockHttpServletRequest();
        requisicao.addHeader("Authorization", "Bearer jwt-do-usuario");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requisicao));
        wireMock.stubFor(post("/checkout/preferences").willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody(PREFERENCIA_OK)));

        cliente(TOKEN, Duration.ofSeconds(5)).criarPreferencia(UUID.randomUUID(), "t", BigDecimal.TEN);

        wireMock.verify(postRequestedFor(urlEqualTo("/checkout/preferences"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN)));
        assertThat(wireMock.getAllServeEvents().get(0).getRequest().getHeaders().getHeader("Authorization").values())
                .containsExactly("Bearer " + TOKEN);
        wireMock.verify(0, postRequestedFor(urlEqualTo("/checkout/preferences"))
                .withHeader("Authorization", containing("jwt-do-usuario")));
    }

    @Test
    void consultaPagamentoAprovado() {
        wireMock.stubFor(get("/v1/payments/123").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                {"id":123,"status":"approved","status_detail":"accredited","external_reference":"ext-1","outro":1}""")));

        PagamentoMp pagamento = cliente(TOKEN, Duration.ofSeconds(5)).consultarPagamento("123");

        assertThat(pagamento).isEqualTo(new PagamentoMp("123", "approved", "accredited", "ext-1"));
        wireMock.verify(getRequestedFor(urlEqualTo("/v1/payments/123"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN)));
    }

    @Test
    void consultaPagamentoRecusado() {
        wireMock.stubFor(get("/v1/payments/456").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                {"id":456,"status":"rejected","status_detail":"cc_rejected_other_reason","external_reference":"ext-2"}""")));

        PagamentoMp pagamento = cliente(TOKEN, Duration.ofSeconds(5)).consultarPagamento("456");

        assertThat(pagamento.status()).isEqualTo("rejected");
        assertThat(pagamento.statusDetail()).isEqualTo("cc_rejected_other_reason");
    }

    @Test
    void consultaComCorpoVazioEErroEMercadoPagoForaViraIndisponivel() {
        wireMock.stubFor(get("/v1/payments/1").willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")));
        wireMock.stubFor(get("/v1/payments/2").willReturn(aResponse().withStatus(502)));

        assertThatThrownBy(() -> cliente(TOKEN, Duration.ofSeconds(5)).consultarPagamento("1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cliente(TOKEN, Duration.ofSeconds(5)).consultarPagamento("2"))
                .isInstanceOf(MercadoPagoIndisponivelException.class);
        assertThat(erros("pagamento")).isEqualTo(2);
    }

    @Test
    void propriedadesTemOsPadroesDaF6() {
        MercadoPagoProperties vazio = new MercadoPagoProperties(null, null, null, null);

        assertThat(vazio.baseUrl()).isEqualTo("https://api.mercadopago.com");
        assertThat(vazio.accessToken()).isEmpty();
        assertThat(vazio.timeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(vazio.retry().maxTentativas()).isEqualTo(3);
        assertThat(vazio.retry().esperaInicial()).isEqualTo(Duration.ofMillis(500));
        assertThat(vazio.retry().multiplicador()).isEqualTo(2.0);
    }
}
