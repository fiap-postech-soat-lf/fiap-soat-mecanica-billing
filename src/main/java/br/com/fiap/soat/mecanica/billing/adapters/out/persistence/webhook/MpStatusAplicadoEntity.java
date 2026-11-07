package br.com.fiap.soat.mecanica.billing.adapters.out.persistence.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "mp_status_aplicado")
@IdClass(MpStatusAplicadoId.class)
public class MpStatusAplicadoEntity {

    @Id
    @Column(name = "mp_payment_id", length = 50)
    private String mpPaymentId;

    @Id
    @Column(length = 30)
    private String status;

    @Column(name = "pagamento_id")
    private UUID pagamentoId;

    @Column(name = "aplicado_em", nullable = false)
    private Instant aplicadoEm;
}
