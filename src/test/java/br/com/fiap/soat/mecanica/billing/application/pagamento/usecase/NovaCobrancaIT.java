package br.com.fiap.soat.mecanica.billing.application.pagamento.usecase;

import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.Pagamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.PagamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.StatusPagamento;
import br.com.fiap.soat.mecanica.billing.support.Dados;
import br.com.fiap.soat.mecanica.billing.support.JwtTestTokens;
import br.com.fiap.soat.mecanica.billing.support.SagaIntegracaoTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static br.com.fiap.soat.mecanica.billing.support.MercadoPagoStubs.chamadasDePreferencia;
import static br.com.fiap.soat.mecanica.billing.support.MercadoPagoStubs.preferenciaComErro;
import static br.com.fiap.soat.mecanica.billing.support.MercadoPagoStubs.preferenciaCriada;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /pagamentos/{osId}/nova-cobranca contra o Postgres, a outbox e o Mercado Pago simulado. */
class NovaCobrancaIT extends SagaIntegracaoTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    OrcamentoRepository orcamentos;
    @Autowired
    PagamentoRepository pagamentos;
    @Autowired
    JdbcTemplate jdbc;

    private Orcamento orcamentoAprovado(UUID osId) {
        Orcamento o = Dados.orcamento(osId, UUID.randomUUID(), Duration.ofHours(1));
        o.aprovar(UUID.randomUUID(), Instant.now());
        return orcamentos.salvar(o);
    }

    private Pagamento pagamentoVigente(Orcamento o, boolean recusado) {
        Pagamento p = Dados.pagamento(o);
        p.registrarPreferencia("pref-antiga", "https://mp/antigo");
        if (recusado) {
            p.recusar("777", "cc_rejected_other_reason", Instant.now());
        }
        return pagamentos.salvar(p);
    }

    private String atendente() {
        return "Bearer " + JwtTestTokens.funcionario(UUID.randomUUID(), "ATENDENTE");
    }

    private int pagamentosDaOs(UUID osId) {
        return jdbc.queryForObject("select count(*) from pagamentos where os_id = ?", Integer.class, osId);
    }

    @Test
    void vigenteRecusadoViraSubstituidoENasceUmPendenteComCobrancaCriada() throws Exception {
        UUID osId = UUID.randomUUID();
        Orcamento o = orcamentoAprovado(osId);
        Pagamento antigo = pagamentoVigente(o, true);
        preferenciaCriada(MERCADOPAGO, osId, "pref-nova-" + osId);

        mockMvc.perform(post("/pagamentos/" + osId + "/nova-cobranca").header("Authorization", atendente()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.link").value(
                        "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=pref-nova-" + osId));

        assertThat(pagamentos.buscarPorId(antigo.getId()).orElseThrow().getStatus())
                .isEqualTo(StatusPagamento.SUBSTITUIDO);
        Pagamento vigente = pagamentos.buscarVigentePorOs(osId).orElseThrow();
        assertThat(vigente.getId()).isNotEqualTo(antigo.getId());
        assertThat(vigente.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        MensagemEnvelope criada = esperar(osId, "CobrancaCriada", 1).get(0);
        assertThat(criada.sagaId()).isEqualTo(o.getSagaId());
        assertThat(criada.payload().path("pagamentoId").asText()).isEqualTo(vigente.getId().toString());
        validarContratos(osId);
    }

    @Test
    void vigentePendenteDa422SemChamarOMercadoPago() throws Exception {
        UUID osId = UUID.randomUUID();
        Orcamento o = orcamentoAprovado(osId);
        pagamentoVigente(o, false);
        preferenciaCriada(MERCADOPAGO, osId, "pref-nao-deve-ser-chamada");

        mockMvc.perform(post("/pagamentos/" + osId + "/nova-cobranca").header("Authorization", atendente()))
                .andExpect(status().isUnprocessableEntity());

        assertThat(chamadasDePreferencia(MERCADOPAGO, osId)).isZero();
        assertThat(pagamentosDaOs(osId)).isEqualTo(1);
    }

    @Test
    void mercadoPagoForaDa502ENadaEGravado() throws Exception {
        UUID osId = UUID.randomUUID();
        Orcamento o = orcamentoAprovado(osId);
        Pagamento antigo = pagamentoVigente(o, true);
        preferenciaComErro(MERCADOPAGO, osId, 503);

        mockMvc.perform(post("/pagamentos/" + osId + "/nova-cobranca").header("Authorization", atendente()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.erro").value("MERCADOPAGO_INDISPONIVEL"));

        assertThat(chamadasDePreferencia(MERCADOPAGO, osId)).isEqualTo(3);
        assertThat(pagamentos.buscarPorId(antigo.getId()).orElseThrow().getStatus()).isEqualTo(StatusPagamento.RECUSADO);
        assertThat(pagamentosDaOs(osId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox where envelope::text like ?", Integer.class,
                "%" + osId + "%")).isZero();
    }
}
