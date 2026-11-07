package br.com.fiap.soat.mecanica.billing.adapters.out.persistence.pagamento;

import br.com.fiap.soat.mecanica.billing.domain.pagamento.StatusPagamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PagamentoJpaRepository extends JpaRepository<PagamentoEntity, UUID> {

    Optional<PagamentoEntity> findByOsIdAndStatusNot(UUID osId, StatusPagamento status);

    List<PagamentoEntity> findByOsIdOrderByCriadoEmDesc(UUID osId);
}
