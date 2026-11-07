package br.com.fiap.soat.mecanica.billing.adapters.in.web.webhook;

import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.Pagamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.PagamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.StatusPagamento;
import br.com.fiap.soat.mecanica.billing.support.AssinaturasDeTeste;
import br.com.fiap.soat.mecanica.billing.support.Dados;
import br.com.fiap.soat.mecanica.billing.support.SagaIntegracaoTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static br.com.fiap.soat.mecanica.billing.support.MercadoPagoStubs.consultasDePagamento;
import static br.com.fiap.soat.mecanica.billing.support.MercadoPagoStubs.pagamentoComErro;
import static br.com.fiap.soat.mecanica.billing.support.MercadoPagoStubs.pagamentoConsultado;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /webhooks/mercadopago + o processador, contra o Postgres, a outbox e o Mercado Pago simulado (WireMock). */
class WebhookIT extends SagaIntegracaoTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    OrcamentoRepository orcamentos;
    @Autowired
    PagamentoRepository pagamentos;
    @Autowired
    JdbcTemplate jdbc;

    private Pagamento pagamentoPendente(UUID osId) {
        Orcamento o = Dados.orcamento(osId, UUID.randomUUID(), Duration.ofHours(1));
        o.aprovar(UUID.randomUUID(), Instant.now());
        o = orcamentos.salvar(o);
        Pagamento p = Dados.pagamento(o);
        p.registrarPreferencia("pref-" + osId, "https://mp/" + osId);
        return pagamentos.salvar(p);
    }

    /** Id numerico longo, como o do Mercado Pago, unico por teste (o WireMock e o banco sao compartilhados). */
    private static String novoMpPaymentId() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(100_000_000_000L, 999_999_999_999L));
    }

    private ResultActions notificar(String mpPaymentId, String tipo, String acao, String assinatura) throws Exception {
        String requestId = UUID.randomUUID().toString();
        var req = post("/webhooks/mercadopago?data.id=" + mpPaymentId + "&type=" + tipo)
                .header("x-request-id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"" + acao + "\",\"type\":\"" + tipo + "\",\"data\":{\"id\":\"" + mpPaymentId + "\"}}");
        if (assinatura != null) {
            req.header("x-signature", assinatura);
        } else {
            req.header("x-signature", AssinaturasDeTeste.assinar(mpPaymentId, requestId, "1700000000"));
        }
        return mockMvc.perform(req);
    }

    private int eventosGravados(String mpPaymentId) {
        return jdbc.queryForObject("select count(*) from webhook_eventos where mp_payment_id = ?", Integer.class,
                mpPaymentId);
    }

    private int eventosComStatus(String mpPaymentId, String status) {
        return jdbc.queryForObject("select count(*) from webhook_eventos where mp_payment_id = ? and status = ?",
                Integer.class, mpPaymentId, status);
    }

    @Test
    void assinaturaValidaResponde200EGravaUmaLinhaPendente() throws Exception {
        String mpId = novoMpPaymentId();
        pagamentoComErro(MERCADOPAGO, mpId, 404);

        notificar(mpId, "payment", "payment.created", null).andExpect(status().isOk());

        assertThat(eventosGravados(mpId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select tipo || '/' || acao from webhook_eventos where mp_payment_id = ?",
                String.class, mpId)).isEqualTo("payment/payment.created");
    }

    @Test
    void assinaturaInvalidaResponde401SemCorpoENaoGravaNada() throws Exception {
        String mpId = novoMpPaymentId();

        notificar(mpId, "payment", "payment.created", "ts=1700000000,v1=" + "a".repeat(64))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
        mockMvc.perform(post("/webhooks/mercadopago?data.id=" + mpId + "&type=payment"))
                .andExpect(status().isUnauthorized());

        assertThat(eventosGravados(mpId)).isZero();
        assertThat(consultasDePagamento(MERCADOPAGO, mpId)).isZero();
    }

    @Test
    void tipoMerchantOrderResponde200SemLinha() throws Exception {
        String mpId = novoMpPaymentId();

        notificar(mpId, "merchant_order", "updated", null).andExpect(status().isOk());

        assertThat(eventosGravados(mpId)).isZero();
    }

    @Test
    void approvedGeraPagamentoConfirmadoValidadoContraOSchema() throws Exception {
        UUID osId = UUID.randomUUID();
        Pagamento p = pagamentoPendente(osId);
        String mpId = novoMpPaymentId();
        pagamentoConsultado(MERCADOPAGO, mpId, "approved", "accredited", p.getId());

        notificar(mpId, "payment", "payment.updated", null).andExpect(status().isOk());

        MensagemEnvelope confirmado = esperar(osId, "PagamentoConfirmado", 1).get(0);
        assertThat(confirmado.payload().path("pagamentoId").asText()).isEqualTo(p.getId().toString());
        assertThat(confirmado.payload().path("mpPaymentId").asText()).isEqualTo(mpId);
        assertThat(confirmado.payload().path("status").asText()).isEqualTo("APROVADO");
        assertThat(confirmado.sagaId()).isEqualTo(orcamentos.buscarPorId(p.getOrcamentoId()).orElseThrow().getSagaId());
        Pagamento depois = pagamentos.buscarPorId(p.getId()).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(depois.getMpPaymentId()).isEqualTo(mpId);
        validarContratos(osId);
    }

    @Test
    void mesmaNotificacaoTresVezesGeraUmEventoSo() throws Exception {
        UUID osId = UUID.randomUUID();
        Pagamento p = pagamentoPendente(osId);
        String mpId = novoMpPaymentId();
        pagamentoConsultado(MERCADOPAGO, mpId, "approved", "accredited", p.getId());

        notificar(mpId, "payment", "payment.created", null).andExpect(status().isOk());
        notificar(mpId, "payment", "payment.updated", null).andExpect(status().isOk());
        notificar(mpId, "payment", "payment.updated", null).andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(
                () -> assertThat(eventosComStatus(mpId, "PROCESSADO")).isEqualTo(3));
        esperar(osId, "PagamentoConfirmado", 1);
        assertThat(doTopico(osId, "PagamentoConfirmado")).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from mp_status_aplicado where mp_payment_id = ?",
                Integer.class, mpId)).isEqualTo(1);
    }

    @Test
    void rejectedGeraPagamentoRecusadoComOStatusDetail() throws Exception {
        UUID osId = UUID.randomUUID();
        Pagamento p = pagamentoPendente(osId);
        String mpId = novoMpPaymentId();
        pagamentoConsultado(MERCADOPAGO, mpId, "rejected", "cc_rejected_other_reason", p.getId());

        notificar(mpId, "payment", "payment.updated", null).andExpect(status().isOk());

        MensagemEnvelope recusado = esperar(osId, "PagamentoRecusado", 1).get(0);
        assertThat(recusado.payload().path("motivo").asText()).isEqualTo("cc_rejected_other_reason");
        assertThat(recusado.payload().path("status").asText()).isEqualTo("RECUSADO");
        assertThat(pagamentos.buscarPorId(p.getId()).orElseThrow().getStatus()).isEqualTo(StatusPagamento.RECUSADO);
        validarContratos(osId);
    }

    @Test
    void external_referenceDesconhecidaViraIgnorado() throws Exception {
        String mpId = novoMpPaymentId();
        pagamentoConsultado(MERCADOPAGO, mpId, "approved", "accredited", UUID.randomUUID());

        notificar(mpId, "payment", "payment.updated", null).andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(
                () -> assertThat(eventosComStatus(mpId, "IGNORADO")).isEqualTo(1));
    }

    @Test
    void mercadoPagoComErroTentaDeNovoEnaQuintaVezViraErro() throws Exception {
        String mpId = novoMpPaymentId();
        pagamentoComErro(MERCADOPAGO, mpId, 500);

        notificar(mpId, "payment", "payment.updated", null).andExpect(status().isOk());

        // O backoff real e 2^n s; aqui o proximo horario e antecipado para o teste nao levar 30 s
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            jdbc.update("update webhook_eventos set proxima_tentativa_em = now() where mp_payment_id = ? "
                    + "and status = 'PENDENTE'", mpId);
            assertThat(eventosComStatus(mpId, "ERRO")).isEqualTo(1);
        });
        assertThat(jdbc.queryForObject("select tentativas from webhook_eventos where mp_payment_id = ?",
                Integer.class, mpId)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select ultimo_erro from webhook_eventos where mp_payment_id = ?",
                String.class, mpId)).contains("MercadoPagoIndisponivelException");
    }
}
