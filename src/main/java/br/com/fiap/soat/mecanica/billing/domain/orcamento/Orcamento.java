package br.com.fiap.soat.mecanica.billing.domain.orcamento;

import br.com.fiap.soat.mecanica.billing.domain.exception.RegraNegocioException;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Orcamento da OS (tabela 4.1 da F6). Transicoes recebem o instante, para o dominio nao ler o relogio. */
@Getter
public class Orcamento {

    private final UUID id;
    private final UUID osId;
    private final UUID sagaId;
    private final UUID clienteId;
    private final List<ItemOrcamento> itens;
    private final BigDecimal valorTotal;
    private final Instant expiraEm;
    private final Instant criadoEm;
    private StatusOrcamento status;
    private Instant decididoEm;
    private UUID decididoPor;
    private String motivo;
    /** Versao do bloqueio otimista (nula antes do primeiro save). */
    private final Long versao;

    @SuppressWarnings("java:S107")
    public Orcamento(UUID id, UUID osId, UUID sagaId, UUID clienteId, List<ItemOrcamento> itens,
                     BigDecimal valorTotal, Instant expiraEm, Instant criadoEm, StatusOrcamento status,
                     Instant decididoEm, UUID decididoPor, String motivo, Long versao) {
        this.id = id;
        this.osId = osId;
        this.sagaId = sagaId;
        this.clienteId = clienteId;
        this.itens = List.copyOf(itens);
        this.valorTotal = valorTotal;
        this.expiraEm = expiraEm;
        this.criadoEm = criadoEm;
        this.status = status;
        this.decididoEm = decididoEm;
        this.decididoPor = decididoPor;
        this.motivo = motivo;
        this.versao = versao;
    }

    /**
     * Cria um orcamento GERADO recalculando o total a partir dos itens.
     *
     * @throws OrcamentoInconsistenteException item sem valor, quantidade invalida ou total diferente do informado
     */
    public static Orcamento gerar(UUID osId, UUID sagaId, UUID clienteId, List<ItemOrcamento> itens,
                                  BigDecimal valorTotalInformado, Duration ttl, Instant agora) {
        if (itens == null || itens.isEmpty()) {
            throw new OrcamentoInconsistenteException("Orcamento sem itens");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ItemOrcamento item : itens) {
            if (item.valorUnitario() == null || item.valorUnitario().signum() < 0) {
                throw new OrcamentoInconsistenteException("Item sem valor valido: " + item.descricao());
            }
            if (item.quantidade() < 1) {
                throw new OrcamentoInconsistenteException("Item com quantidade invalida: " + item.descricao());
            }
            total = total.add(item.valorTotal());
        }
        total = total.setScale(2, RoundingMode.HALF_EVEN);
        if (valorTotalInformado == null || total.compareTo(valorTotalInformado) != 0) {
            throw new OrcamentoInconsistenteException(
                    "Total divergente: calculado " + total + ", informado " + valorTotalInformado);
        }
        return new Orcamento(UUID.randomUUID(), osId, sagaId, clienteId, itens, total, agora.plus(ttl), agora,
                StatusOrcamento.GERADO, null, null, null, null);
    }

    public boolean isDono(UUID sub) {
        return clienteId.equals(sub);
    }

    public boolean isVencido(Instant agora) {
        return !expiraEm.isAfter(agora);
    }

    public void aprovar(UUID sub, Instant agora) {
        exigirDecidivel(agora);
        status = StatusOrcamento.APROVADO;
        decididoPor = sub;
        decididoEm = agora;
    }

    public void recusar(UUID sub, String motivoRecusa, Instant agora) {
        exigirDecidivel(agora);
        status = StatusOrcamento.RECUSADO;
        decididoPor = sub;
        decididoEm = agora;
        motivo = motivoRecusa;
    }

    /** So um GERADO ja vencido expira (o scheduler nao pode expirar antes da hora). */
    public void expirar(Instant agora) {
        if (status != StatusOrcamento.GERADO) {
            throw new RegraNegocioException("Somente orcamento GERADO pode expirar (status atual: " + status + ")");
        }
        if (!isVencido(agora)) {
            throw new RegraNegocioException("Orcamento ainda dentro do prazo");
        }
        status = StatusOrcamento.EXPIRADO;
        decididoEm = agora;
    }

    /**
     * Cancela um GERADO ou APROVADO. Em qualquer outro estado nao faz nada e devolve false
     * (a regra "APROVADO sem pagamento" e do chamador, que conhece os pagamentos).
     */
    public boolean cancelar(String motivoCancelamento) {
        if (status != StatusOrcamento.GERADO && status != StatusOrcamento.APROVADO) {
            return false;
        }
        status = StatusOrcamento.CANCELADO;
        motivo = motivoCancelamento;
        return true;
    }

    private void exigirDecidivel(Instant agora) {
        if (status != StatusOrcamento.GERADO) {
            throw new RegraNegocioException("Orcamento nao esta aguardando decisao (status atual: " + status + ")");
        }
        if (isVencido(agora)) {
            throw new RegraNegocioException("Orcamento expirado");
        }
    }
}
