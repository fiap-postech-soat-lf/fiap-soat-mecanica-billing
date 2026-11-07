package br.com.fiap.soat.mecanica.billing.adapters.out.persistence.saga;

import br.com.fiap.soat.mecanica.billing.application.saga.ComandoAtendido;
import br.com.fiap.soat.mecanica.billing.application.saga.ComandosAtendidos;
import br.com.fiap.soat.mecanica.billing.application.saga.RespostasSaga.Resposta;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** comando_atendido em SQL (F6-c): cadeia de ids e resposta guardada, na transacao do handler. */
@Component
public class ComandoAtendidoJpaAdapter implements ComandosAtendidos {

    private final ComandoAtendidoJpaRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ComandoAtendidoJpaAdapter(ComandoAtendidoJpaRepository repository, ObjectMapper objectMapper,
                                     Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ComandoAtendido> buscar(UUID osId, String tipo) {
        return repository.findById(new ComandoAtendidoId(osId, tipo)).map(this::paraDominio);
    }

    @Override
    @Transactional
    public void registrar(UUID osId, String tipo, UUID messageId, Resposta resposta) {
        ComandoAtendidoEntity entity = repository.findById(new ComandoAtendidoId(osId, tipo))
                .orElseGet(() -> {
                    ComandoAtendidoEntity nova = new ComandoAtendidoEntity();
                    nova.setOsId(osId);
                    nova.setTipo(tipo);
                    return nova;
                });
        ObjectNode guardada = objectMapper.createObjectNode().put("tipo", resposta.tipo());
        guardada.set("payload", resposta.payload());
        entity.setIds(escrever(List.of(messageId)));
        entity.setResposta(guardada.toString());
        entity.setAtualizadoEm(clock.instant());
        repository.saveAndFlush(entity);
    }

    @Override
    @Transactional
    public void acrescentar(UUID osId, String tipo, UUID messageId) {
        ComandoAtendidoEntity entity = repository.findById(new ComandoAtendidoId(osId, tipo))
                .orElseThrow(() -> new IllegalStateException(
                        "comando_atendido inexistente para " + tipo + " da OS " + osId));
        ComandoAtendido atual = paraDominio(entity).comReenvio(messageId);
        entity.setIds(escrever(atual.ids()));
        entity.setAtualizadoEm(clock.instant());
        repository.saveAndFlush(entity);
    }

    private ComandoAtendido paraDominio(ComandoAtendidoEntity entity) {
        try {
            List<UUID> ids = new ArrayList<>();
            objectMapper.readTree(entity.getIds()).forEach(n -> ids.add(UUID.fromString(n.asText())));
            JsonNode resposta = entity.getResposta() == null ? null : objectMapper.readTree(entity.getResposta());
            return new ComandoAtendido(ids, resposta == null ? null : resposta.path("tipo").asText(),
                    resposta == null ? null : resposta.path("payload"));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("comando_atendido invalido: " + entity.getTipo(), e);
        }
    }

    private String escrever(List<UUID> ids) {
        return objectMapper.valueToTree(ids).toString();
    }
}
