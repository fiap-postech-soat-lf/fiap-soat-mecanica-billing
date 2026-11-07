package br.com.fiap.soat.mecanica.billing.application.saga;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Comando ja atendido para uma OS (F6-c): a cadeia de ids (o original e os reenvios) e a resposta guardada,
 * para reemitir num envelope novo.
 */
public record ComandoAtendido(List<UUID> ids, String tipoResposta, JsonNode payloadResposta) {

    public ComandoAtendido {
        ids = List.copyOf(ids);
    }

    /** O comando e um reenvio quando o messageId ou o causationId esta na cadeia. */
    public boolean contem(UUID messageId, UUID causationId) {
        return ids.contains(messageId) || (causationId != null && ids.contains(causationId));
    }

    public ComandoAtendido comReenvio(UUID messageId) {
        List<UUID> nova = new ArrayList<>(ids);
        if (!nova.contains(messageId)) {
            nova.add(messageId);
        }
        return new ComandoAtendido(nova, tipoResposta, payloadResposta);
    }
}
