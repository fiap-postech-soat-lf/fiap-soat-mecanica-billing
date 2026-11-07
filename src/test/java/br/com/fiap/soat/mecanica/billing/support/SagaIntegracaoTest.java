package br.com.fiap.soat.mecanica.billing.support;

import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.sqs.model.Message;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Apoio dos ITs que mandam comandos pela fila real e leem o topico de eventos: a fila do assinante e
 * compartilhada, entao cada teste filtra as mensagens pela propria OS.
 */
public abstract class SagaIntegracaoTest extends IntegracaoTest {

    @Autowired
    protected ObjectMapper objectMapper;

    protected final List<String> corpos = new ArrayList<>();

    protected UUID enviar(String tipo, UUID sagaId, UUID osId, UUID causationId, Object payload) throws Exception {
        UUID messageId = UUID.randomUUID();
        MensagemEnvelope envelope = new MensagemEnvelope(messageId, tipo, 1, sagaId, osId, UUID.randomUUID().toString(),
                causationId, Instant.now(), null, objectMapper.valueToTree(payload));
        String corpo = objectMapper.writeValueAsString(envelope);
        SQS.sendMessage(r -> r.queueUrl(MENSAGERIA.filaEntrada()).messageBody(corpo));
        return messageId;
    }

    /** Espera o topico entregar {@code minimo} mensagens do tipo para a OS. */
    protected List<MensagemEnvelope> esperar(UUID osId, String tipo, int minimo) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            receber(MENSAGERIA.filaAssinante()).stream().map(Message::body).forEach(corpos::add);
            assertThat(doTopico(osId, tipo)).hasSizeGreaterThanOrEqualTo(minimo);
        });
        return doTopico(osId, tipo);
    }

    protected List<MensagemEnvelope> doTopico(UUID osId, String tipo) {
        return corpos.stream().filter(b -> b.contains(osId.toString()) && b.contains("\"" + tipo + "\""))
                .map(b -> {
                    try {
                        return objectMapper.readValue(b, MensagemEnvelope.class);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }).toList();
    }

    /** Cada envelope produzido para a OS vale contra o schema do envelope e o do payload do Billing. */
    protected void validarContratos(UUID osId) {
        corpos.stream().filter(b -> b.contains(osId.toString()))
                .forEach(b -> assertThat(ContratosDeTeste.validar(b)).as(b).isEmpty());
    }
}
