package br.com.fiap.soat.mecanica.billing.adapters.out.persistence.webhook;

import java.io.Serializable;

/** Chave composta de mp_status_aplicado. */
public record MpStatusAplicadoId(String mpPaymentId, String status) implements Serializable {
}
