package br.com.fiap.soat.mecanica.billing.domain.pagamento;

import br.com.fiap.soat.mecanica.billing.domain.exception.RegraNegocioException;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Cobranca de uma OS no Mercado Pago (tabela 4.1 da F6). Um so pagamento e vigente por OS (F6-h). */
@Getter
public class Pagamento {

    private final UUID id;
    private final UUID osId;
    private final UUID orcamentoId;
    private final UUID clienteId;
    private final BigDecimal valor;
    private final Instant criadoEm;
    private StatusPagamento status;
    private String mpPreferenceId;
    private String link;
    private String mpPaymentId;
    private String motivo;
    private Instant atualizadoEm;
    private final Long versao;

    @SuppressWarnings("java:S107")
    public Pagamento(UUID id, UUID osId, UUID orcamentoId, UUID clienteId, BigDecimal valor,
                     StatusPagamento status, String mpPreferenceId, String link, String mpPaymentId,
                     String motivo, Instant criadoEm, Instant atualizadoEm, Long versao) {
        this.id = id;
        this.osId = osId;
        this.orcamentoId = orcamentoId;
        this.clienteId = clienteId;
        this.valor = valor;
        this.status = status;
        this.mpPreferenceId = mpPreferenceId;
        this.link = link;
        this.mpPaymentId = mpPaymentId;
        this.motivo = motivo;
        this.criadoEm = criadoEm;
        this.atualizadoEm = atualizadoEm;
        this.versao = versao;
    }

    public static Pagamento criar(UUID osId, UUID orcamentoId, UUID clienteId, BigDecimal valor, Instant agora) {
        return new Pagamento(UUID.randomUUID(), osId, orcamentoId, clienteId, valor, StatusPagamento.PENDENTE,
                null, null, null, null, agora, agora, null);
    }

    public boolean isDono(UUID sub) {
        return clienteId.equals(sub);
    }

    /** Vigente = todo pagamento que nao foi substituido por uma nova cobranca. */
    public boolean isVigente() {
        return status != StatusPagamento.SUBSTITUIDO;
    }

    /** So PENDENTE e RECUSADO (o cliente tentou de novo no mesmo link) aceitam um resultado do Mercado Pago. */
    public boolean aceitaResultado() {
        return status == StatusPagamento.PENDENTE || status == StatusPagamento.RECUSADO;
    }

    public void registrarPreferencia(String preferenceId, String linkDePagamento) {
        if (status != StatusPagamento.PENDENTE) {
            throw new RegraNegocioException("Preferencia so pode ser registrada em pagamento PENDENTE");
        }
        this.mpPreferenceId = preferenceId;
        this.link = linkDePagamento;
    }

    public void aprovar(String paymentId, Instant agora) {
        exigirAceitaResultado();
        status = StatusPagamento.APROVADO;
        mpPaymentId = paymentId;
        motivo = null;
        atualizadoEm = agora;
    }

    public void recusar(String paymentId, String statusDetail, Instant agora) {
        exigirAceitaResultado();
        status = StatusPagamento.RECUSADO;
        mpPaymentId = paymentId;
        motivo = statusDetail;
        atualizadoEm = agora;
    }

    /** Nova cobranca: so o vigente RECUSADO e substituido. */
    public void substituir(Instant agora) {
        if (status != StatusPagamento.RECUSADO) {
            throw new RegraNegocioException("Nova cobranca exige pagamento vigente RECUSADO (status atual: "
                    + status + ")");
        }
        status = StatusPagamento.SUBSTITUIDO;
        atualizadoEm = agora;
    }

    private void exigirAceitaResultado() {
        if (!aceitaResultado()) {
            throw new RegraNegocioException("Pagamento nao aceita resultado (status atual: " + status + ")");
        }
    }
}
