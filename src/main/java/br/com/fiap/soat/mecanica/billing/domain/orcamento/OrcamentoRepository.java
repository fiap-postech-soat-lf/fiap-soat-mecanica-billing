package br.com.fiap.soat.mecanica.billing.domain.orcamento;

import java.util.Optional;
import java.util.UUID;

public interface OrcamentoRepository {

    /** Grava e sincroniza na hora; uma versao desatualizada lanca ConflitoConcorrenciaException. */
    Orcamento salvar(Orcamento orcamento);

    Optional<Orcamento> buscarPorId(UUID id);

    /** O orcamento GERADO ou APROVADO da OS (no maximo um, pelo indice unico parcial). */
    Optional<Orcamento> buscarVivoPorOs(UUID osId);

    Optional<Orcamento> buscarMaisRecentePorOs(UUID osId);
}
