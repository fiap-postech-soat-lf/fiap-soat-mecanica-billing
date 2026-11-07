package br.com.fiap.soat.mecanica.billing.application.security;

import java.util.Optional;
import java.util.UUID;

/** Quem esta chamando, sem consultar o banco: os dados vem do JWT ja validado. */
public interface UsuarioAutenticadoPort {

    /** UUID do cliente ou do funcionario (claim sub). */
    UUID sub();

    TipoPrincipal tipo();

    /** Cargo do funcionario; vazio para cliente. */
    Optional<String> cargo();

    default boolean isCliente() {
        return tipo() == TipoPrincipal.CLIENTE;
    }
}
