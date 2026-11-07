package br.com.fiap.soat.mecanica.billing.application.pagamento.port;

/** Pagamento consultado no Mercado Pago. O status vem sempre daqui, nunca do corpo do webhook (F6-i). */
public record PagamentoMp(String id, String status, String statusDetail, String externalReference) {
}
