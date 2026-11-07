package br.com.fiap.soat.mecanica.billing.application.saga;

import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.Pagamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.PagamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.StatusPagamento;
import br.com.fiap.soat.mecanica.billing.support.Dados;
import br.com.fiap.soat.mecanica.billing.support.SagaIntegracaoTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static br.com.fiap.soat.mecanica.billing.support.MercadoPagoStubs.chamadasDePreferencia;
import static br.com.fiap.soat.mecanica.billing.support.MercadoPagoStubs.preferenciaComErro;
import static br.com.fiap.soat.mecanica.billing.support.MercadoPagoStubs.preferenciaCriada;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** SolicitarPagamento pela fila real (LocalStack) e o Mercado Pago simulado (WireMock). */
class CobrancaSagaIT extends SagaIntegracaoTest {

    @Autowired
    OrcamentoRepository orcamentos;
    @Autowired
    PagamentoRepository pagamentos;
    @Autowired
    JdbcTemplate jdbc;

    private Orcamento orcamentoAprovado(UUID osId, UUID saga) {
        Orcamento o = Dados.orcamento(osId, UUID.randomUUID(), Duration.ofHours(1));
        o = new Orcamento(o.getId(), osId, saga, o.getClienteId(), o.getItens(), o.getValorTotal(), o.getExpiraEm(),
                o.getCriadoEm(), o.getStatus(), null, null, null, null);
        o.aprovar(UUID.randomUUID(), Instant.now());
        return orcamentos.salvar(o);
    }

    private static Map<String, Object> payload(Orcamento o) {
        return Map.of("osId", o.getOsId(), "orcamentoId", o.getId(), "valor", o.getValorTotal());
    }

    private int pagamentosDaOs(UUID osId) {
        return jdbc.queryForObject("select count(*) from pagamentos where os_id = ?", Integer.class, osId);
    }

    @Test
    void solicitarPagamentoCriaACobrancaERespondeCobrancaCriadaComOLinkDoMercadoPago() throws Exception {
        UUID osId = UUID.randomUUID();
        UUID saga = UUID.randomUUID();
        Orcamento o = orcamentoAprovado(osId, saga);
        preferenciaCriada(MERCADOPAGO, osId, "pref-" + osId);

        UUID messageId = enviar("SolicitarPagamento", saga, osId, null, payload(o));

        MensagemEnvelope criada = esperar(osId, "CobrancaCriada", 1).get(0);
        Pagamento p = pagamentos.buscarVigentePorOs(osId).orElseThrow();
        assertThat(criada.sagaId()).isEqualTo(saga);
        assertThat(criada.causationId()).isEqualTo(messageId);
        assertThat(criada.payload().path("pagamentoId").asText()).isEqualTo(p.getId().toString());
        assertThat(criada.payload().path("link").asText())
                .isEqualTo("https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=pref-" + osId);
        assertThat(criada.payload().path("status").asText()).isEqualTo("PENDENTE");
        assertThat(p.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(p.getValor()).isEqualByComparingTo("159.80");
        assertThat(p.getClienteId()).isEqualTo(o.getClienteId());
        assertThat(p.getMpPreferenceId()).isEqualTo("pref-" + osId);
        assertThat(chamadasDePreferencia(MERCADOPAGO, osId)).isEqualTo(1);
        validarContratos(osId);
    }

    @Test
    void mercadoPagoComErroNasTresTentativasNaoGravaNadaEACobrancaSaiNaProximaEntrega() throws Exception {
        UUID osId = UUID.randomUUID();
        UUID saga = UUID.randomUUID();
        Orcamento o = orcamentoAprovado(osId, saga);
        var stubComErro = preferenciaComErro(MERCADOPAGO, osId, 500);

        enviar("SolicitarPagamento", saga, osId, null, payload(o));

        await().atMost(Duration.ofSeconds(30)).until(() -> chamadasDePreferencia(MERCADOPAGO, osId) >= 3);
        assertThat(pagamentosDaOs(osId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from comando_atendido where os_id = ?", Integer.class, osId))
                .isZero();

        MERCADOPAGO.removeStub(stubComErro);
        preferenciaCriada(MERCADOPAGO, osId, "pref-depois-" + osId);

        MensagemEnvelope criada = esperar(osId, "CobrancaCriada", 1).get(0);
        assertThat(criada.payload().path("link").asText()).endsWith("pref-depois-" + osId);
        assertThat(pagamentosDaOs(osId)).isEqualTo(1);
        validarContratos(osId);
    }

    @Test
    void reenvioDoOsReemiteACobrancaCriadaComUmaChamadaSoAoMercadoPago() throws Exception {
        UUID osId = UUID.randomUUID();
        UUID saga = UUID.randomUUID();
        Orcamento o = orcamentoAprovado(osId, saga);
        preferenciaCriada(MERCADOPAGO, osId, "pref-" + osId);
        UUID original = enviar("SolicitarPagamento", saga, osId, null, payload(o));
        MensagemEnvelope primeira = esperar(osId, "CobrancaCriada", 1).get(0);

        UUID reenvio = enviar("SolicitarPagamento", saga, osId, original, payload(o));

        List<MensagemEnvelope> criadas = esperar(osId, "CobrancaCriada", 2);
        MensagemEnvelope reemitida = criadas.stream().filter(e -> reenvio.equals(e.causationId())).findFirst()
                .orElseThrow();
        assertThat(reemitida.messageId()).isNotEqualTo(primeira.messageId());
        assertThat(reemitida.payload()).isEqualTo(primeira.payload());
        assertThat(pagamentosDaOs(osId)).isEqualTo(1);
        assertThat(chamadasDePreferencia(MERCADOPAGO, osId)).isEqualTo(1);
        validarContratos(osId);
    }

    @Test
    void semOrcamentoAprovadoNaoHaPreferenciaNemResposta() throws Exception {
        UUID osId = UUID.randomUUID();
        UUID saga = UUID.randomUUID();
        Orcamento gerado = orcamentos.salvar(Dados.orcamento(osId, UUID.randomUUID(), Duration.ofHours(1)));
        preferenciaCriada(MERCADOPAGO, osId, "pref-" + osId);

        enviar("SolicitarPagamento", saga, osId, null,
                Map.of("osId", osId, "orcamentoId", gerado.getId(), "valor", new BigDecimal("159.80")));

        // a mensagem e consumida e descartada sem efeito: nada de pagamento, preferencia ou resposta
        await().during(Duration.ofSeconds(4)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            esperarNenhuma(osId);
            assertThat(pagamentosDaOs(osId)).isZero();
            assertThat(chamadasDePreferencia(MERCADOPAGO, osId)).isZero();
        });
    }

    private void esperarNenhuma(UUID osId) {
        receber(MENSAGERIA.filaAssinante()).forEach(m -> corpos.add(m.body()));
        assertThat(doTopico(osId, "CobrancaCriada")).isEmpty();
    }
}
