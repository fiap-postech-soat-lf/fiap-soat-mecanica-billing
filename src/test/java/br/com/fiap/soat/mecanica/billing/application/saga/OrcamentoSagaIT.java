package br.com.fiap.soat.mecanica.billing.application.saga;

import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.StatusOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.PagamentoRepository;
import br.com.fiap.soat.mecanica.billing.support.ContratosDeTeste;
import br.com.fiap.soat.mecanica.billing.support.Dados;
import br.com.fiap.soat.mecanica.billing.support.IntegracaoTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.services.sqs.model.Message;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Comandos do OS pela fila real (LocalStack): banco, comando_atendido, outbox e topico, com o schema do Billing. */
class OrcamentoSagaIT extends IntegracaoTest {

    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    OrcamentoRepository orcamentos;
    @Autowired
    PagamentoRepository pagamentos;
    @Autowired
    JdbcTemplate jdbc;

    private final List<String> corpos = new ArrayList<>();

    private static Map<String, Object> payloadGerar(UUID osId, UUID clienteId, String total) {
        return Map.of("osId", osId, "clienteId", clienteId, "valorTotal", new BigDecimal(total), "itens", List.of(
                Map.of("servicoId", UUID.randomUUID(), "descricao", "Troca de oleo",
                        "precoMaoDeObra", new BigDecimal("80.00"), "pecas", List.of(
                                Map.of("pecaId", UUID.randomUUID(), "descricao", "Filtro de oleo", "quantidade", 2,
                                        "valorUnitario", new BigDecimal("39.90"))))));
    }

    private UUID enviar(String tipo, UUID sagaId, UUID osId, UUID causationId, Object payload) throws Exception {
        UUID messageId = UUID.randomUUID();
        MensagemEnvelope envelope = new MensagemEnvelope(messageId, tipo, 1, sagaId, osId, UUID.randomUUID().toString(),
                causationId, Instant.now(), null, objectMapper.valueToTree(payload));
        SQS.sendMessage(r -> r.queueUrl(MENSAGERIA.filaEntrada()).messageBody(writeValue(envelope)));
        return messageId;
    }

    private String writeValue(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Espera o topico entregar {@code minimo} mensagens do tipo para a OS (a fila do assinante e compartilhada). */
    private List<MensagemEnvelope> esperar(UUID osId, String tipo, int minimo) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            receber(MENSAGERIA.filaAssinante()).stream().map(Message::body).forEach(corpos::add);
            assertThat(doTopico(osId, tipo)).hasSizeGreaterThanOrEqualTo(minimo);
        });
        return doTopico(osId, tipo);
    }

    private List<MensagemEnvelope> doTopico(UUID osId, String tipo) {
        return corpos.stream().filter(b -> b.contains(osId.toString()) && b.contains("\"" + tipo + "\""))
                .map(b -> {
                    try {
                        return objectMapper.readValue(b, MensagemEnvelope.class);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }).toList();
    }

    private void validarContratos(UUID osId) {
        corpos.stream().filter(b -> b.contains(osId.toString()))
                .forEach(b -> assertThat(ContratosDeTeste.validar(b)).as(b).isEmpty());
    }

    private int orcamentosDaOs(UUID osId) {
        return jdbc.queryForObject("select count(*) from orcamentos where os_id = ?", Integer.class, osId);
    }

    private String cadeia(UUID osId, String tipo) {
        return jdbc.queryForObject("select ids::text from comando_atendido where os_id = ? and tipo = ?",
                String.class, osId, tipo);
    }

    @Test
    void gerarOrcamentoCriaOGeradoComExpiraEmDoTtlERespondeNoTopico() throws Exception {
        UUID osId = UUID.randomUUID();
        UUID saga = UUID.randomUUID();
        UUID cliente = UUID.randomUUID();
        Instant antes = Instant.now();

        UUID messageId = enviar("GerarOrcamento", saga, osId, null, payloadGerar(osId, cliente, "159.80"));

        MensagemEnvelope gerado = esperar(osId, "OrcamentoGerado", 1).get(0);
        Orcamento o = orcamentos.buscarVivoPorOs(osId).or(() -> orcamentos.buscarMaisRecentePorOs(osId)).orElseThrow();
        assertThat(gerado.sagaId()).isEqualTo(saga);
        assertThat(gerado.causationId()).isEqualTo(messageId);
        assertThat(gerado.payload().path("orcamentoId").asText()).isEqualTo(o.getId().toString());
        assertThat(gerado.payload().path("valorTotal").decimalValue()).isEqualByComparingTo("159.80");
        Instant expiraEm = Instant.parse(gerado.payload().path("expiraEm").asText());
        assertThat(expiraEm).isBetween(antes.plusSeconds(2), antes.plusSeconds(2 + 20));
        assertThat(o.getClienteId()).isEqualTo(cliente);
        assertThat(o.getSagaId()).isEqualTo(saga);
        assertThat(o.getItens()).hasSize(2);
        validarContratos(osId);
    }

    @Test
    void reenvioComCausationIdDoOriginalReemiteOMesmoOrcamentoGeradoSemCriarOutro() throws Exception {
        UUID osId = UUID.randomUUID();
        UUID saga = UUID.randomUUID();
        UUID cliente = UUID.randomUUID();
        UUID original = enviar("GerarOrcamento", saga, osId, null, payloadGerar(osId, cliente, "159.80"));
        MensagemEnvelope primeiro = esperar(osId, "OrcamentoGerado", 1).get(0);

        UUID reenvio = enviar("GerarOrcamento", saga, osId, original, payloadGerar(osId, cliente, "159.80"));

        List<MensagemEnvelope> geradas = esperar(osId, "OrcamentoGerado", 2);
        assertThat(geradas).hasSize(2);
        MensagemEnvelope reemitido = geradas.stream().filter(e -> reenvio.equals(e.causationId())).findFirst()
                .orElseThrow();
        assertThat(reemitido.messageId()).isNotEqualTo(primeiro.messageId());
        assertThat(reemitido.payload()).isEqualTo(primeiro.payload());
        assertThat(orcamentosDaOs(osId)).isEqualTo(1);
        assertThat(cadeia(osId, "GerarOrcamento")).contains(original.toString()).contains(reenvio.toString());
        validarContratos(osId);
    }

    @Test
    void novaRodadaForaDaCadeiaSubstituiACadeiaECriaOutroOrcamento() throws Exception {
        UUID osId = UUID.randomUUID();
        UUID saga = UUID.randomUUID();
        UUID cliente = UUID.randomUUID();
        UUID primeira = enviar("GerarOrcamento", saga, osId, null, payloadGerar(osId, cliente, "159.80"));
        esperar(osId, "OrcamentoGerado", 1);

        UUID segunda = enviar("GerarOrcamento", saga, osId, UUID.randomUUID(), payloadGerar(osId, cliente, "159.80"));

        List<MensagemEnvelope> geradas = esperar(osId, "OrcamentoGerado", 2);
        assertThat(geradas.stream().map(e -> e.payload().path("orcamentoId").asText()).distinct()).hasSize(2);
        assertThat(orcamentosDaOs(osId)).isEqualTo(2);
        assertThat(cadeia(osId, "GerarOrcamento")).contains(segunda.toString()).doesNotContain(primeira.toString());
    }

    @Test
    void totalDivergenteRespondeOrcamentoFalhouENaoCriaOrcamento() throws Exception {
        UUID osId = UUID.randomUUID();
        UUID saga = UUID.randomUUID();
        UUID original = enviar("GerarOrcamento", saga, osId, null, payloadGerar(osId, UUID.randomUUID(), "100.00"));

        MensagemEnvelope falhou = esperar(osId, "OrcamentoFalhou", 1).get(0);

        assertThat(falhou.causationId()).isEqualTo(original);
        assertThat(falhou.payload().path("motivo").asText()).contains("Total divergente");
        assertThat(orcamentosDaOs(osId)).isZero();
        // o reenvio reemite a mesma falha
        UUID reenvio = enviar("GerarOrcamento", saga, osId, original, payloadGerar(osId, UUID.randomUUID(), "100.00"));
        List<MensagemEnvelope> falhas = esperar(osId, "OrcamentoFalhou", 2);
        assertThat(falhas).anySatisfy(e -> assertThat(e.causationId()).isEqualTo(reenvio));
        assertThat(orcamentosDaOs(osId)).isZero();
        validarContratos(osId);
    }

    @Test
    void novaRodadaComGeradoAnteriorCancelaOAnteriorEUmCancelarAtrasadoSoAtingeOAntigo() throws Exception {
        UUID osId = UUID.randomUUID();
        UUID saga = UUID.randomUUID();
        UUID cliente = UUID.randomUUID();
        Orcamento anterior = orcamentos.salvar(Dados.orcamento(osId, cliente, Duration.ofHours(1)));

        enviar("GerarOrcamento", saga, osId, null, payloadGerar(osId, cliente, "159.80"));
        MensagemEnvelope gerado = esperar(osId, "OrcamentoGerado", 1).get(0);
        UUID novoId = UUID.fromString(gerado.payload().path("orcamentoId").asText());

        // O CancelarOrcamento da revisao chega depois da nova rodada (filas padrao nao garantem ordem)
        enviar("CancelarOrcamento", saga, osId, null,
                Map.of("osId", osId, "orcamentoId", anterior.getId(), "motivo", "Revisao do diagnostico"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(orcamentos.buscarPorId(anterior.getId()).orElseThrow().getStatus())
                        .isEqualTo(StatusOrcamento.CANCELADO));
        assertThat(orcamentos.buscarPorId(anterior.getId()).orElseThrow().getMotivo()).isIn("SUBSTITUIDO",
                "Revisao do diagnostico");
        // o novo nao e atingido (GERADO ou, passados 2 s, EXPIRADO; nunca CANCELADO)
        assertThat(orcamentos.buscarPorId(novoId).orElseThrow().getStatus())
                .isIn(StatusOrcamento.GERADO, StatusOrcamento.EXPIRADO);
        validarContratos(osId);
    }

    @Test
    void cancelarOrcamentoGeradoCancelaESemResposta() throws Exception {
        UUID osId = UUID.randomUUID();
        Orcamento o = orcamentos.salvar(Dados.orcamento(osId, UUID.randomUUID(), Duration.ofHours(1)));

        enviar("CancelarOrcamento", UUID.randomUUID(), osId, null,
                Map.of("osId", osId, "orcamentoId", o.getId(), "motivo", "Cliente desistiu"));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(orcamentos.buscarPorId(o.getId()).orElseThrow().getStatus())
                        .isEqualTo(StatusOrcamento.CANCELADO));
        assertThat(orcamentos.buscarPorId(o.getId()).orElseThrow().getMotivo()).isEqualTo("Cliente desistiu");
        assertThat(jdbc.queryForObject("select count(*) from outbox where envelope->>'osId' = ?", Integer.class,
                osId.toString())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from comando_atendido where os_id = ?", Integer.class,
                osId)).isZero();
    }

    @Test
    void cancelarOrcamentoAprovadoComPagamentoNaoFazNada() throws Exception {
        UUID osId = UUID.randomUUID();
        Orcamento o = Dados.orcamento(osId, UUID.randomUUID(), Duration.ofHours(1));
        o.aprovar(o.getClienteId(), Instant.now());
        o = orcamentos.salvar(o);
        pagamentos.salvar(Dados.pagamento(o));

        UUID cancelamento = enviar("CancelarOrcamento", UUID.randomUUID(), osId, null,
                Map.of("osId", osId, "orcamentoId", o.getId(), "motivo", "tarde demais"));
        // outro comando depois, na mesma fila: quando ele for atendido, o cancelamento ja foi tratado
        UUID marcador = UUID.randomUUID();
        enviar("GerarOrcamento", UUID.randomUUID(), marcador, null, payloadGerar(marcador, UUID.randomUUID(), "1.00"));
        esperar(marcador, "OrcamentoFalhou", 1);

        assertThat(cancelamento).isNotNull();
        assertThat(orcamentos.buscarPorId(o.getId()).orElseThrow().getStatus()).isEqualTo(StatusOrcamento.APROVADO);
    }
}
