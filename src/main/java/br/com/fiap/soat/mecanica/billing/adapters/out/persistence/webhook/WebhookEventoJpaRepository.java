package br.com.fiap.soat.mecanica.billing.adapters.out.persistence.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface WebhookEventoJpaRepository extends JpaRepository<WebhookEventoEntity, UUID> {

    @Query(value = """
            SELECT * FROM webhook_eventos
            WHERE status = 'PENDENTE' AND proxima_tentativa_em <= :agora
            ORDER BY proxima_tentativa_em, recebido_em
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<WebhookEventoEntity> travarProximoPendente(@Param("agora") Instant agora);
}
