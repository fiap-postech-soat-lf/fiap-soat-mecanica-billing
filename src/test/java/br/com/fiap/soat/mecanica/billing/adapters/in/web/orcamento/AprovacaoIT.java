package br.com.fiap.soat.mecanica.billing.adapters.in.web.orcamento;

import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.StatusOrcamento;
import br.com.fiap.soat.mecanica.billing.support.ContratosDeTeste;
import br.com.fiap.soat.mecanica.billing.support.Dados;
import br.com.fiap.soat.mecanica.billing.support.IntegracaoTest;
import br.com.fiap.soat.mecanica.billing.support.JwtTestTokens;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.sqs.model.Message;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Aprovar e recusar de ponta a ponta: REST, banco, outbox e tópico, com o envelope validado contra o schema do Billing. */
class AprovacaoIT extends IntegracaoTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    OrcamentoRepository orcamentos;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper objectMapper;

    private Orcamento novo(UUID cliente, Duration ttl) {
        return orcamentos.salvar(Dados.orcamento(UUID.randomUUID(), cliente, ttl));
    }

    private static String cliente(UUID sub) {
        return "Bearer " + JwtTestTokens.cliente(sub);
    }

    private int naOutbox(UUID osId) {
        return jdbc.queryForObject("select count(*) from outbox where envelope->>'osId' = ?", Integer.class,
                osId.toString());
    }

    /** Espera o OutboxPublisher entregar a mensagem da OS no topico (a fila do assinante e compartilhada pelos ITs). */
    private MensagemEnvelope esperarNoTopico(UUID osId, String tipo) throws Exception {
        List<Message> recebidas = new ArrayList<>();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            recebidas.addAll(receber(MENSAGERIA.filaAssinante()));
            assertThat(recebidas).anySatisfy(m -> assertThat(m.body())
                    .contains(osId.toString()).contains("\"" + tipo + "\""));
        });
        String corpo = recebidas.stream().map(Message::body)
                .filter(b -> b.contains(osId.toString()) && b.contains("\"" + tipo + "\"")).findFirst().orElseThrow();
        assertThat(ContratosDeTeste.validar(corpo)).isEmpty();
        return objectMapper.readValue(corpo, MensagemEnvelope.class);
    }

    @Test
    void clienteDonoAprovaEOrcamentoAprovadoChegaAoTopico() throws Exception {
        UUID cliente = UUID.randomUUID();
        Orcamento o = novo(cliente, Duration.ofHours(1));

        mockMvc.perform(patch("/orcamentos/" + o.getId() + "/aprovar").header("Authorization", cliente(cliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADO"))
                .andExpect(jsonPath("$.decididoPor").value(cliente.toString()));

        assertThat(orcamentos.buscarPorId(o.getId()).orElseThrow().getStatus()).isEqualTo(StatusOrcamento.APROVADO);
        MensagemEnvelope e = esperarNoTopico(o.getOsId(), "OrcamentoAprovado");
        assertThat(e.sagaId()).isEqualTo(o.getSagaId());
        JsonNode payload = e.payload();
        assertThat(payload.path("orcamentoId").asText()).isEqualTo(o.getId().toString());
        assertThat(payload.path("aprovadoPor").asText()).isEqualTo(cliente.toString());
    }

    @Test
    void outroClienteRecebe403ENadaVaiParaAOutbox() throws Exception {
        Orcamento o = novo(UUID.randomUUID(), Duration.ofHours(1));

        mockMvc.perform(patch("/orcamentos/" + o.getId() + "/aprovar")
                        .header("Authorization", cliente(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("ORCAMENTO_DE_OUTRO_CLIENTE"));
        mockMvc.perform(patch("/orcamentos/" + o.getId() + "/recusar")
                        .header("Authorization", cliente(UUID.randomUUID())))
                .andExpect(status().isForbidden());

        assertThat(naOutbox(o.getOsId())).isZero();
        assertThat(orcamentos.buscarPorId(o.getId()).orElseThrow().getStatus()).isEqualTo(StatusOrcamento.GERADO);
    }

    @Test
    void outroClienteTambemNaoLe() throws Exception {
        Orcamento o = novo(UUID.randomUUID(), Duration.ofHours(1));

        mockMvc.perform(get("/orcamentos/" + o.getId()).header("Authorization", cliente(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/orcamentos/por-os/" + o.getOsId()).header("Authorization", cliente(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void atendenteAprovaEmNomeDoClienteEAprovadoPorEOAtendente() throws Exception {
        Orcamento o = novo(UUID.randomUUID(), Duration.ofHours(1));
        UUID atendente = UUID.randomUUID();

        mockMvc.perform(patch("/orcamentos/" + o.getId() + "/aprovar")
                        .header("Authorization", "Bearer " + JwtTestTokens.funcionario(atendente, "ATENDENTE")))
                .andExpect(status().isOk());

        MensagemEnvelope e = esperarNoTopico(o.getOsId(), "OrcamentoAprovado");
        assertThat(e.payload().path("aprovadoPor").asText()).isEqualTo(atendente.toString());
    }

    @Test
    void recusarPublicaOrcamentoRecusadoComMotivoNoBanco() throws Exception {
        UUID cliente = UUID.randomUUID();
        Orcamento o = novo(cliente, Duration.ofHours(1));

        mockMvc.perform(patch("/orcamentos/" + o.getId() + "/recusar").header("Authorization", cliente(cliente))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Muito caro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECUSADO"));

        assertThat(orcamentos.buscarPorId(o.getId()).orElseThrow().getMotivo()).isEqualTo("Muito caro");
        MensagemEnvelope e = esperarNoTopico(o.getOsId(), "OrcamentoRecusado");
        assertThat(e.payload().path("recusadoPor").asText()).isEqualTo(cliente.toString());
    }

    @Test
    void aprovarDepoisDeExpiraEmDa422MesmoAntesDoScheduler() throws Exception {
        UUID cliente = UUID.randomUUID();
        Orcamento o = novo(cliente, Duration.ofMillis(1));
        Thread.sleep(50);

        mockMvc.perform(patch("/orcamentos/" + o.getId() + "/aprovar").header("Authorization", cliente(cliente)))
                .andExpect(status().isUnprocessableEntity());

        // O scheduler de expiracao (1 s nos testes) pode vencer a corrida: o que nao pode haver e aprovacao
        assertThat(jdbc.queryForObject("select count(*) from outbox where tipo = 'OrcamentoAprovado' "
                + "and envelope->>'osId' = ?", Integer.class, o.getOsId().toString())).isZero();
        assertThat(orcamentos.buscarPorId(o.getId()).orElseThrow().getStatus())
                .isIn(StatusOrcamento.GERADO, StatusOrcamento.EXPIRADO);
    }

    @Test
    void aprovarDuasVezesDa422ENaoDuplicaOEvento() throws Exception {
        UUID cliente = UUID.randomUUID();
        Orcamento o = novo(cliente, Duration.ofHours(1));

        mockMvc.perform(patch("/orcamentos/" + o.getId() + "/aprovar").header("Authorization", cliente(cliente)))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/orcamentos/" + o.getId() + "/aprovar").header("Authorization", cliente(cliente)))
                .andExpect(status().isUnprocessableEntity());

        assertThat(naOutbox(o.getOsId())).isEqualTo(1);
    }

    @Test
    void getPorOsDevolveOVivoComItens() throws Exception {
        UUID cliente = UUID.randomUUID();
        Orcamento o = novo(cliente, Duration.ofHours(1));

        mockMvc.perform(get("/orcamentos/por-os/" + o.getOsId()).header("Authorization", cliente(cliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(o.getId().toString()))
                .andExpect(jsonPath("$.valorTotal").value(159.80))
                .andExpect(jsonPath("$.itens.length()").value(2));
    }
}
