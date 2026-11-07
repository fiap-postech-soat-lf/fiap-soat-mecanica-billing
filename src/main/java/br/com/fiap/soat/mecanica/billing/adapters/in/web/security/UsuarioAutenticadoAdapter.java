package br.com.fiap.soat.mecanica.billing.adapters.in.web.security;

import br.com.fiap.soat.mecanica.billing.application.security.TipoPrincipal;
import br.com.fiap.soat.mecanica.billing.application.security.UsuarioAutenticadoPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Implementa a porta a partir do {@link UsuarioAutenticado} da requisicao atual. */
@Component
public class UsuarioAutenticadoAdapter implements UsuarioAutenticadoPort {

    @Override
    public UUID sub() {
        return atual().sub();
    }

    @Override
    public TipoPrincipal tipo() {
        return TipoPrincipal.valueOf(atual().principalType().name());
    }

    @Override
    public Optional<String> cargo() {
        return Optional.ofNullable(atual().cargo());
    }

    private static UsuarioAutenticado atual() {
        return UsuarioAutenticado.atual()
                .orElseThrow(() -> new IllegalStateException("usuario_nao_autenticado"));
    }
}
