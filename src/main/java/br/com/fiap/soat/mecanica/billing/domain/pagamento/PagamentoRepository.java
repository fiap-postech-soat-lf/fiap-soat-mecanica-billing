package br.com.fiap.soat.mecanica.billing.domain.pagamento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PagamentoRepository {

    Pagamento salvar(Pagamento pagamento);

    Optional<Pagamento> buscarPorId(UUID id);

    /** O pagamento da OS que nao foi substituido (no maximo um, pelo indice unico parcial). */
    Optional<Pagamento> buscarVigentePorOs(UUID osId);

    /** Todas as cobrancas da OS, da mais recente para a mais antiga. */
    List<Pagamento> listarPorOs(UUID osId);
}
