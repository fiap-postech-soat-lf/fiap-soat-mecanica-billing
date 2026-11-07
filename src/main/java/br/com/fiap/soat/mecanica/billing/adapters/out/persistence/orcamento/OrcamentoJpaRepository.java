package br.com.fiap.soat.mecanica.billing.adapters.out.persistence.orcamento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrcamentoJpaRepository extends JpaRepository<OrcamentoEntity, UUID> {

    @Query("select o from OrcamentoEntity o where o.osId = :osId and o.status in "
            + "(br.com.fiap.soat.mecanica.billing.domain.orcamento.StatusOrcamento.GERADO, "
            + "br.com.fiap.soat.mecanica.billing.domain.orcamento.StatusOrcamento.APROVADO)")
    Optional<OrcamentoEntity> buscarVivo(UUID osId);

    Optional<OrcamentoEntity> findFirstByOsIdOrderByCriadoEmDesc(UUID osId);

    @Query(value = """
            SELECT * FROM orcamentos
            WHERE status = 'GERADO' AND expira_em <= :agora
            ORDER BY expira_em
            LIMIT :limite
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OrcamentoEntity> travarVencidos(@Param("agora") Instant agora, @Param("limite") int limite);
}
