package br.com.fiap.soat.mecanica.billing.adapters.out.persistence.pagamento;

import br.com.fiap.soat.mecanica.billing.domain.pagamento.StatusPagamento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pagamentos")
public class PagamentoEntity {

    @Id
    private UUID id;

    @Column(name = "os_id", nullable = false)
    private UUID osId;

    @Column(name = "orcamento_id", nullable = false)
    private UUID orcamentoId;

    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusPagamento status;

    @Column(name = "mp_preference_id", length = 100)
    private String mpPreferenceId;

    @Column(columnDefinition = "text")
    private String link;

    @Column(name = "mp_payment_id", length = 50)
    private String mpPaymentId;

    @Column(columnDefinition = "text")
    private String motivo;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Version
    @Column(nullable = false)
    private Long versao;
}
