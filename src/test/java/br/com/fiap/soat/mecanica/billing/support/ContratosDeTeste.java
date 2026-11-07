package br.com.fiap.soat.mecanica.billing.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Valida um envelope produzido pelo servico contra o schema do envelope e o do payload do proprio Billing
 * (regra de congelamento da F1): {@code contratos/<type>.v<version>.schema.json}.
 */
public final class ContratosDeTeste {

    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(
            SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemaRegistryConfig(SchemaRegistryConfig.builder().formatAssertionsEnabled(true).build()));
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ContratosDeTeste() {
    }

    /** Erros do envelope e do payload (lista vazia = valido). */
    public static List<String> validar(String envelopeJson) {
        List<String> erros = new ArrayList<>();
        try {
            JsonNode json = MAPPER.readTree(envelopeJson);
            REGISTRY.getSchema(ler("contratos/envelope.schema.json"), InputFormat.JSON)
                    .validate(envelopeJson, InputFormat.JSON).forEach(e -> erros.add("envelope: " + e));
            String schema = "contratos/" + json.path("type").asText() + ".v" + json.path("version").asInt()
                    + ".schema.json";
            REGISTRY.getSchema(ler(schema), InputFormat.JSON)
                    .validate(json.path("payload").toString(), InputFormat.JSON)
                    .forEach((Error e) -> erros.add("payload: " + e));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return erros;
    }

    private static String ler(String caminho) throws IOException {
        return new ClassPathResource(caminho).getContentAsString(StandardCharsets.UTF_8);
    }
}
