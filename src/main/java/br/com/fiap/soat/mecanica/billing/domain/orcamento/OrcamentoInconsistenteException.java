package br.com.fiap.soat.mecanica.billing.domain.orcamento;

/** Itens invalidos ou total do comando diferente do recalculado (gera OrcamentoFalhou, F6-d). */
public class OrcamentoInconsistenteException extends RuntimeException {
    public OrcamentoInconsistenteException(String message) {
        super(message);
    }
}
