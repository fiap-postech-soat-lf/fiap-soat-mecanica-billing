package br.com.fiap.soat.mecanica.billing.adapters.out.persistence.saga;

import java.io.Serializable;
import java.util.UUID;

/** Chave composta de comando_atendido. */
public record ComandoAtendidoId(UUID osId, String tipo) implements Serializable {
}
