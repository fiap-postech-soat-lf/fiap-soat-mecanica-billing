package br.com.fiap.soat.mecanica.billing.application.security;

import br.com.fiap.soat.mecanica.billing.domain.exception.AcessoARecursoDeOutroClienteException;

import java.util.UUID;

/** Regra de dono (F6-e, F6-k): CLIENTE so acessa o que e dele; ATENDENTE acessa em nome do cliente. */
public final class DonoDoRecurso {

    private DonoDoRecurso() {
    }

    public static void exigir(UsuarioAutenticadoPort usuario, UUID clienteId, String mensagem) {
        if (usuario.isCliente() && !usuario.sub().equals(clienteId)) {
            throw new AcessoARecursoDeOutroClienteException(mensagem);
        }
    }
}
