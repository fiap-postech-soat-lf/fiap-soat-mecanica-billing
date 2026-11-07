package br.com.fiap.soat.mecanica.billing.adapters.in.web.security;

import br.com.fiap.soat.mecanica.billing.application.security.TipoPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UsuarioAutenticadoAdapterTest {

    final UsuarioAutenticadoAdapter adapter = new UsuarioAutenticadoAdapter();

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComo(UsuarioAutenticado usuario) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario, null, List.of()));
    }

    @Test
    void clienteNaoTemCargo() {
        UUID sub = UUID.randomUUID();
        autenticarComo(new UsuarioAutenticado(sub, UsuarioAutenticado.TipoPrincipal.CLIENTE, null));

        assertThat(adapter.sub()).isEqualTo(sub);
        assertThat(adapter.tipo()).isEqualTo(TipoPrincipal.CLIENTE);
        assertThat(adapter.isCliente()).isTrue();
        assertThat(adapter.cargo()).isEmpty();
    }

    @Test
    void funcionarioTemCargo() {
        autenticarComo(new UsuarioAutenticado(UUID.randomUUID(), UsuarioAutenticado.TipoPrincipal.FUNCIONARIO, "ATENDENTE"));

        assertThat(adapter.tipo()).isEqualTo(TipoPrincipal.FUNCIONARIO);
        assertThat(adapter.isCliente()).isFalse();
        assertThat(adapter.cargo()).contains("ATENDENTE");
    }

    @Test
    void semUsuarioAutenticadoFalha() {
        assertThatThrownBy(adapter::sub).isInstanceOf(IllegalStateException.class);
    }
}
