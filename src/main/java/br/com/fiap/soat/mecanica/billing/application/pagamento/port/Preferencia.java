package br.com.fiap.soat.mecanica.billing.application.pagamento.port;

/** Preferencia do Checkout Pro criada no Mercado Pago: id e os dois links de pagamento. */
public record Preferencia(String id, String initPoint, String sandboxInitPoint) {
}
