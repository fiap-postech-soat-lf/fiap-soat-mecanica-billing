package br.com.fiap.soat.mecanica.billing.application.pagamento.exception;

/** {@code MP_ACCESS_TOKEN} nao configurado: nenhuma chamada e feita (F6-m). */
public class MercadoPagoTokenAusenteException extends RuntimeException {

    public MercadoPagoTokenAusenteException() {
        super("Access token do Mercado Pago nao configurado");
    }
}
