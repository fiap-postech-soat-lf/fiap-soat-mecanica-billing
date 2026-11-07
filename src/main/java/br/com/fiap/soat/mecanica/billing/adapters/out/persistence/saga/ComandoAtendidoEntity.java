package br.com.fiap.soat.mecanica.billing.adapters.out.persistence.saga;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "comando_atendido")
@IdClass(ComandoAtendidoId.class)
public class ComandoAtendidoEntity {

    @Id
    @Column(name = "os_id")
    private UUID osId;

    @Id
    @Column(length = 100)
    private String tipo;

    /** Cadeia de ids (messageId do original e dos reenvios), em JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String ids;

    /** Resposta guardada: {@code {"tipo": ..., "payload": {...}}}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String resposta;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
}
