package br.com.fiap.soat.mecanica.billing.application.pagamento;

import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.Pagamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.PagamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.StatusPagamento;
import br.com.fiap.soat.mecanica.billing.support.AssinaturasDeTeste;
import br.com.fiap.soat.mecanica.billing.support.Dados;
import br.com.fiap.soat.mecanica.billing.support.JwtTestTokens;
import br.com.fiap.soat.mecanica.billing.support.SagaIntegracaoTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static br.com.fiap.soat.mecanica.billing.support.MercadoPagoStubs.pagamentoConsultado;
import static br.com.fiap.soat.mecanica.billing.support.MercadoPagoStubs.preferenciaCriada;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O ciclo inteiro da cobranca (F6-h): SolicitarPagamento -> rejected -> nova-cobranca -> approved no antigo
 * (sem evento) -> approved no novo (PagamentoConfirmado).
 */
class PagamentoFluxoIT extends SagaIntegracaoTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    OrcamentoRepository orcamentos;
    @Autowired
    PagamentoRepository pagamentos;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MeterRegistry metricas;

    private void notificar(String mpId) throws Exception {
        String requestId = UUID.randomUUID().toString();
        mockMvc.perform(post("/webhooks/mercadopago?data.id=" + mpId + "&type=payment")
                        .header("x-request-id", requestId)
                        .header("x-signature", AssinaturasDeTeste.assinar(mpId, requestId, "1700000000")))
                .andExpect(status().isOk());
    }

    private static String novoMpPaymentId() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000_000L, 999_999_999_999L));
    }

    private void esperarProcessado(String mpId) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "select count(*) from webhook_eventos where mp_payment_id = ? and status = 'PROCESSADO'",
                Integer.class, mpId)).isEqualTo(1));
    }

    @Test
    void cobrancaRecusadaNovaCobrancaEAprovacaoSoNoVigente() throws Exception {
        UUID osId = UUID.randomUUID();
        UUID saga = UUID.randomUUID();
        Orcamento o = Dados.orcamento(osId, UUID.randomUUID(), Duration.ofHours(1));
        o = new Orcamento(o.getId(), osId, saga, o.getClienteId(), o.getItens(), o.getValorTotal(), o.getExpiraEm(),
                o.getCriadoEm(), o.getStatus(), null, null, null, null);
        o.aprovar(UUID.randomUUID(), Instant.now());
        o = orcamentos.salvar(o);

        // SolicitarPagamento -> CobrancaCriada
        preferenciaCriada(MERCADOPAGO, osId, "pref-1-" + osId);
        enviar("SolicitarPagamento", saga, osId, null,
                Map.of("osId", osId, "orcamentoId", o.getId(), "valor", o.getValorTotal()));
        assertThat(esperar(osId, "CobrancaCriada", 1)).hasSize(1);
        Pagamento primeiro = pagamentos.buscarVigentePorOs(osId).orElseThrow();

        // webhook rejected -> PagamentoRecusado
        String mpRecusado = novoMpPaymentId();
        pagamentoConsultado(MERCADOPAGO, mpRecusado, "rejected", "cc_rejected_other_reason", primeiro.getId());
        notificar(mpRecusado);
        MensagemEnvelope recusado = esperar(osId, "PagamentoRecusado", 1).get(0);
        assertThat(recusado.sagaId()).isEqualTo(saga);
        assertThat(recusado.payload().path("pagamentoId").asText()).isEqualTo(primeiro.getId().toString());

        // nova-cobranca -> novo CobrancaCriada
        preferenciaCriada(MERCADOPAGO, osId, "pref-2-" + osId);
        mockMvc.perform(post("/pagamentos/" + osId + "/nova-cobranca").header("Authorization",
                        "Bearer " + JwtTestTokens.funcionario(UUID.randomUUID(), "ATENDENTE")))
                .andExpect(status().isCreated());
        assertThat(esperar(osId, "CobrancaCriada", 2)).hasSize(2);
        Pagamento segundo = pagamentos.buscarVigentePorOs(osId).orElseThrow();
        assertThat(segundo.getId()).isNotEqualTo(primeiro.getId());

        // approved no antigo (SUBSTITUIDO) -> sem evento e com a metrica
        double antes = metricas.counter("pagamento.substituido_aprovado").count();
        String mpAntigo = novoMpPaymentId();
        pagamentoConsultado(MERCADOPAGO, mpAntigo, "approved", "accredited", primeiro.getId());
        notificar(mpAntigo);
        esperarProcessado(mpAntigo);
        assertThat(metricas.counter("pagamento.substituido_aprovado").count()).isEqualTo(antes + 1);
        assertThat(pagamentos.buscarPorId(primeiro.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusPagamento.SUBSTITUIDO);
        esperar(osId, "PagamentoConfirmado", 0);
        assertThat(doTopico(osId, "PagamentoConfirmado")).isEmpty();

        // approved no novo -> PagamentoConfirmado
        String mpNovo = novoMpPaymentId();
        pagamentoConsultado(MERCADOPAGO, mpNovo, "approved", "accredited", segundo.getId());
        notificar(mpNovo);
        MensagemEnvelope confirmado = esperar(osId, "PagamentoConfirmado", 1).get(0);
        assertThat(confirmado.payload().path("pagamentoId").asText()).isEqualTo(segundo.getId().toString());
        assertThat(pagamentos.buscarPorId(segundo.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusPagamento.APROVADO);
        validarContratos(osId);
    }
}
