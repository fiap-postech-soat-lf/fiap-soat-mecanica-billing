package br.com.fiap.soat.mecanica.billing.domain.orcamento;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrcamentoRepository {

    /** Grava e sincroniza na hora; uma versao desatualizada lanca ConflitoConcorrenciaException. */
    Orcamento salvar(Orcamento orcamento);

    Optional<Orcamento> buscarPorId(UUID id);

    /** O orcamento GERADO ou APROVADO da OS (no maximo um, pelo indice unico parcial). */
    Optional<Orcamento> buscarVivoPorOs(UUID osId);

    Optional<Orcamento> buscarMaisRecentePorOs(UUID osId);

    /**
     * Ate {@code limite} orcamentos GERADO vencidos ({@code expiraEm <= agora}), travados ate o fim da transacao
     * (FOR UPDATE SKIP LOCKED: cada replica pega um lote diferente). Exige uma transacao ativa.
     */
    List<Orcamento> travarVencidos(Instant agora, int limite);
}
