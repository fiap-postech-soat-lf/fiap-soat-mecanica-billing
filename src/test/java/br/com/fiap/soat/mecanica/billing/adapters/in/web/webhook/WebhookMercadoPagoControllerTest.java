package br.com.fiap.soat.mecanica.billing.adapters.in.web.webhook;

import br.com.fiap.soat.mecanica.billing.application.pagamento.usecase.RegistrarNotificacaoUseCase;
import br.com.fiap.soat.mecanica.billing.support.WebMvcSeguro;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcSeguro
@org.springframework.test.context.TestPropertySource(properties = "app.mercadopago.webhook-secret=segredo-webhook-de-teste")
@WebMvcTest(value = WebhookMercadoPagoController.class, excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({AssinaturaWebhook.class, WebhookMercadoPagoControllerTest.Config.class})
class WebhookMercadoPagoControllerTest {

    @TestConfiguration
    static class Config {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    MeterRegistry metricas;
    @MockitoBean
    RegistrarNotificacaoUseCase registrar;

    static String assinar(String dataId, String requestId, String ts) {
        String v1 = java.util.HexFormat.of().formatHex(AssinaturaWebhook.calcular("segredo-webhook-de-teste",
                AssinaturaWebhook.manifesto(dataId, requestId, ts)));
        return "ts=" + ts + ",v1=" + v1;
    }

    @Test
    void semAssinaturaResponde401SemCorpoENaoGrava() throws Exception {
        mockMvc.perform(post("/webhooks/mercadopago?data.id=1&type=payment"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
        verifyNoInteractions(registrar);
    }

    @Test
    void assinaturaInvalidaResponde401() throws Exception {
        mockMvc.perform(post("/webhooks/mercadopago?data.id=1&type=payment")
                        .header("x-signature", "ts=1,v1=" + "0".repeat(64)).header("x-request-id", "r"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(registrar);
    }

    @Test
    void assinaturaValidaGravaEResponde200() throws Exception {
        mockMvc.perform(post("/webhooks/mercadopago?data.id=555&type=payment")
                        .header("x-signature", assinar("555", "req-1", "10")).header("x-request-id", "req-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"payment\",\"action\":\"payment.created\",\"data\":{\"id\":\"555\"}}"))
                .andExpect(status().isOk());
        verify(registrar).executar("req-1", "555", "payment", "payment.created");
    }

    @Test
    void dataIdSoNoCorpoServeDeAlternativa() throws Exception {
        mockMvc.perform(post("/webhooks/mercadopago")
                        .header("x-signature", assinar("777", "req-2", "10")).header("x-request-id", "req-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"payment\",\"action\":\"payment.updated\",\"data\":{\"id\":777}}"))
                .andExpect(status().isOk());
        verify(registrar).executar("req-2", "777", "payment", "payment.updated");
    }

    @Test
    void tipoDiferenteDePaymentResponde200SemEfeito() throws Exception {
        mockMvc.perform(post("/webhooks/mercadopago?data.id=9&type=merchant_order")
                        .header("x-signature", assinar("9", "req-3", "10")).header("x-request-id", "req-3"))
                .andExpect(status().isOk());
        verifyNoInteractions(registrar);
    }

    @Test
    void assinaturaValidaSemDataIdResponde200SemEfeito() throws Exception {
        mockMvc.perform(post("/webhooks/mercadopago?type=payment")
                        .header("x-signature", assinar(null, "req-4", "10")).header("x-request-id", "req-4"))
                .andExpect(status().isOk());
        verify(registrar, org.mockito.Mockito.never()).executar(any(), any(), any(), any());
    }

    @Test
    void corpoIlegivelNaoDerrubaAValidacao() throws Exception {
        mockMvc.perform(post("/webhooks/mercadopago?data.id=1&type=payment")
                        .contentType(MediaType.APPLICATION_JSON).content("{nao e json"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(registrar);
    }
}
