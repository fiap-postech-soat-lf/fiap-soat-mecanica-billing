package br.com.fiap.soat.mecanica.billing.domain.exception;

/** CLIENTE tentou ler ou decidir orcamento ou pagamento de outro cliente: responde 403 (F6-e, F6-k). */
public class AcessoARecursoDeOutroClienteException extends RuntimeException {
    public AcessoARecursoDeOutroClienteException(String message) {
        super(message);
    }
}
