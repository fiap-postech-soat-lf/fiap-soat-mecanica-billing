package br.com.fiap.soat.mecanica.billing.adapters.in.messaging;

import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemHandler;
import br.com.fiap.soat.mecanica.billing.application.saga.ParticipanteSaga;
import br.com.fiap.soat.mecanica.billing.application.saga.dto.CancelarOrcamentoPayload;
import br.com.fiap.soat.mecanica.billing.application.saga.dto.GerarOrcamentoPayload;
import br.com.fiap.soat.mecanica.billing.application.saga.dto.GerarOrcamentoPayload.ItemServico;
import br.com.fiap.soat.mecanica.billing.application.saga.dto.GerarOrcamentoPayload.PecaOrcada;
import br.com.fiap.soat.mecanica.billing.application.saga.dto.SolicitarPagamentoPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Contratos consumidos (copiados do -os, padrao da F4-m): cada exemplo valida contra o schema do OS e
 * desserializa no record do handler que o consome, que o entrega ao {@link ParticipanteSaga}.
 */
class ContratosConsumidosTest {

    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemaRegistryConfig(SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build()));

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final ParticipanteSaga participante = Mockito.mock(ParticipanteSaga.class);

    @Test
    void gerarOrcamento() {
        MensagemEnvelope envelope = exemplo("GerarOrcamento");

        new GerarOrcamentoHandler(participante, objectMapper).processar(envelope);

        verify(participante).gerarOrcamento(eq(envelope), eq(new GerarOrcamentoPayload(
                UUID.fromString("7d1c2a4e-3b5f-4c6d-8e9f-0a1b2c3d4e5f"),
                UUID.fromString("5a4b3c2d-1e0f-4a9b-8c7d-6e5f4a3b2c1d"), List.of(
                new ItemServico(UUID.fromString("11111111-aaaa-4bbb-8ccc-222222222222"), "Troca de oleo e filtro",
                        new BigDecimal("80.0"), List.of(
                        new PecaOrcada(UUID.fromString("55555555-1111-4222-8333-666666666666"),
                                "Oleo 5W30 sintetico 1L", 4, new BigDecimal("42.9")),
                        new PecaOrcada(UUID.fromString("77777777-4444-4555-8666-888888888888"),
                                "Filtro de oleo", 1, new BigDecimal("35.5")))),
                new ItemServico(UUID.fromString("33333333-dddd-4eee-8fff-444444444444"),
                        "Alinhamento e balanceamento", new BigDecimal("120.0"), List.of())),
                new BigDecimal("407.1"))));
    }

    @Test
    void cancelarOrcamento() {
        MensagemEnvelope envelope = exemplo("CancelarOrcamento");

        new CancelarOrcamentoHandler(participante, objectMapper).processar(envelope);

        verify(participante).cancelarOrcamento(eq(envelope), eq(new CancelarOrcamentoPayload(
                UUID.fromString("7d1c2a4e-3b5f-4c6d-8e9f-0a1b2c3d4e5f"),
                UUID.fromString("e1f2a3b4-c5d6-4e7f-8a9b-0c1d2e3f4a5b"),
                "Revisao do diagnostico pedida pelo cliente")));
    }

    @Test
    void solicitarPagamentoDesserializaNoRecordDoT04() throws Exception {
        MensagemEnvelope envelope = exemplo("SolicitarPagamento");

        SolicitarPagamentoPayload payload = PayloadsSaga.ler(objectMapper, envelope, SolicitarPagamentoPayload.class);

        assertThat(payload).isEqualTo(new SolicitarPagamentoPayload(
                UUID.fromString("7d1c2a4e-3b5f-4c6d-8e9f-0a1b2c3d4e5f"),
                UUID.fromString("e1f2a3b4-c5d6-4e7f-8a9b-0c1d2e3f4a5b"), new BigDecimal("407.1")));
    }

    @Test
    void cadaHandlerTrataOTipoDoSeuContrato() {
        List<MensagemHandler> handlers = List.of(new GerarOrcamentoHandler(participante, objectMapper),
                new CancelarOrcamentoHandler(participante, objectMapper));

        assertThat(handlers).extracting(MensagemHandler::tipo).containsExactly("GerarOrcamento", "CancelarOrcamento");
    }

    @Test
    void payloadAusenteOuInvalidoEErro() {
        MensagemEnvelope semPayload = new MensagemEnvelope(UUID.randomUUID(), "GerarOrcamento", 1, null, null, "c",
                null, Instant.now(), null, null);
        MensagemEnvelope invalido = new MensagemEnvelope(UUID.randomUUID(), "CancelarOrcamento", 1, null, null, "c",
                null, Instant.now(), null, JsonNodeFactory.instance.objectNode().put("osId", "nao-e-uuid"));

        assertThatThrownBy(() -> new GerarOrcamentoHandler(participante, objectMapper).processar(semPayload))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CancelarOrcamentoHandler(participante, objectMapper).processar(invalido))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("CancelarOrcamento");
        Mockito.verifyNoInteractions(participante);
    }

    private MensagemEnvelope exemplo(String tipo) {
        String json = ler("contratos-consumidos/exemplos/" + tipo + ".v1.json");
        List<Error> erroEnvelope = schema("contratos/envelope.schema.json").validate(json, InputFormat.JSON);
        assertThat(erroEnvelope).as("envelope de " + tipo).isEmpty();
        try {
            MensagemEnvelope envelope = objectMapper.readValue(json, MensagemEnvelope.class);
            List<Error> erroPayload = schema("contratos-consumidos/" + tipo + ".v1.schema.json")
                    .validate(envelope.payload().toString(), InputFormat.JSON);
            assertThat(erroPayload).as("payload de " + tipo).isEmpty();
            assertThat(envelope.type()).isEqualTo(tipo);
            return envelope;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Schema schema(String caminho) {
        return REGISTRY.getSchema(ler(caminho), InputFormat.JSON);
    }

    private static String ler(String caminho) {
        try {
            return new ClassPathResource(caminho).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
