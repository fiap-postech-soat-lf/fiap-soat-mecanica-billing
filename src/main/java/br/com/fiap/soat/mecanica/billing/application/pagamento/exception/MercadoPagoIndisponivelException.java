package br.com.fiap.soat.mecanica.billing.application.pagamento.exception;

/** Mercado Pago fora do ar, lento ou com 5xx, depois de esgotados os retries. */
public class MercadoPagoIndisponivelException extends RuntimeException {

    public MercadoPagoIndisponivelException(String message, Throwable cause) {
        super(message, cause);
    }
}
