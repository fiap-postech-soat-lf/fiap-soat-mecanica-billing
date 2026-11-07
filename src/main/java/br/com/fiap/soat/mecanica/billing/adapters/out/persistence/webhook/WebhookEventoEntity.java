package br.com.fiap.soat.mecanica.billing.adapters.out.persistence.webhook;

import br.com.fiap.soat.mecanica.billing.domain.webhook.StatusWebhook;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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
@Table(name = "webhook_eventos")
public class WebhookEventoEntity {

    @Id
    private UUID id;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "mp_payment_id", nullable = false, length = 50)
    private String mpPaymentId;

    @Column(nullable = false, length = 50)
    private String tipo;

    @Column(length = 100)
    private String acao;

    @Column(name = "recebido_em", nullable = false)
    private Instant recebidoEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusWebhook status;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "proxima_tentativa_em")
    private Instant proximaTentativaEm;

    @Column(name = "status_mp", length = 30)
    private String statusMp;

    @Column(name = "ultimo_erro", columnDefinition = "text")
    private String ultimoErro;
}
