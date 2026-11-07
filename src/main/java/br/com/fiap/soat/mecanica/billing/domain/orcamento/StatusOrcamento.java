package br.com.fiap.soat.mecanica.billing.domain.orcamento;

public enum StatusOrcamento {
    GERADO, APROVADO, RECUSADO, EXPIRADO, CANCELADO;

    /** Vivo = ocupa o indice unico parcial por OS (F6-d). */
    public boolean isVivo() {
        return this == GERADO || this == APROVADO;
    }
}
