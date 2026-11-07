package br.com.fiap.soat.mecanica.billing.application.saga;

import br.com.fiap.soat.mecanica.billing.application.saga.RespostasSaga.Resposta;

import java.util.Optional;
import java.util.UUID;

/** Porta da tabela comando_atendido (PK os_id + tipo). Deve ser chamada dentro da transacao do handler. */
public interface ComandosAtendidos {

    Optional<ComandoAtendido> buscar(UUID osId, String tipo);

    /** Nova rodada: substitui a linha por uma cadeia que comeca em {@code messageId}, com a resposta dada. */
    void registrar(UUID osId, String tipo, UUID messageId, Resposta resposta);

    /** Reenvio: acrescenta o id a cadeia, sem mudar a resposta guardada. */
    void acrescentar(UUID osId, String tipo, UUID messageId);
}
