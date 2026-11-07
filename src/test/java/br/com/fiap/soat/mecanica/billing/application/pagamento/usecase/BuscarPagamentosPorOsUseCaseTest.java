package br.com.fiap.soat.mecanica.billing.application.pagamento.usecase;

import br.com.fiap.soat.mecanica.billing.application.security.TipoPrincipal;
import br.com.fiap.soat.mecanica.billing.application.security.UsuarioAutenticadoPort;
import br.com.fiap.soat.mecanica.billing.domain.exception.AcessoARecursoDeOutroClienteException;
import br.com.fiap.soat.mecanica.billing.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.Pagamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.PagamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.StatusPagamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuscarPagamentosPorOsUseCaseTest {

    static final Instant AGORA = Instant.parse("2026-11-07T12:00:00Z");
    static final UUID CLIENTE = UUID.randomUUID();
    static final UUID OS = UUID.randomUUID();

    PagamentoRepository repositorio = mock(PagamentoRepository.class);
    UsuarioAutenticadoPort usuario = mock(UsuarioAutenticadoPort.class);
    BuscarPagamentosPorOsUseCase useCase = new BuscarPagamentosPorOsUseCase(repositorio, usuario);
    Pagamento antigo;
    Pagamento vigente;

    @BeforeEach
    void preparar() {
        antigo = Pagamento.criar(OS, UUID.randomUUID(), CLIENTE, new BigDecimal("50.00"), AGORA);
        antigo.recusar("1", "cc_rejected_other_reason", AGORA);
        antigo.substituir(AGORA);
        vigente = Pagamento.criar(OS, UUID.randomUUID(), CLIENTE, new BigDecimal("50.00"), AGORA.plusSeconds(1));
        when(repositorio.listarPorOs(OS)).thenReturn(List.of(vigente, antigo));
    }

    private void autenticar(TipoPrincipal tipo, UUID sub) {
        when(usuario.sub()).thenReturn(sub);
        when(usuario.tipo()).thenReturn(tipo);
        when(usuario.isCliente()).thenReturn(tipo == TipoPrincipal.CLIENTE);
    }

    @Test
    void atendenteEClienteDonoVeemOVigenteEOHistorico() {
        autenticar(TipoPrincipal.FUNCIONARIO, UUID.randomUUID());
        var comoAtendente = useCase.executar(OS);
        autenticar(TipoPrincipal.CLIENTE, CLIENTE);
        var comoCliente = useCase.executar(OS);

        assertThat(comoAtendente.vigente()).isSameAs(vigente);
        assertThat(comoCliente.historico()).containsExactly(vigente, antigo);
        assertThat(antigo.getStatus()).isEqualTo(StatusPagamento.SUBSTITUIDO);
    }

    @Test
    void clienteDeOutroRecebe403() {
        autenticar(TipoPrincipal.CLIENTE, UUID.randomUUID());

        assertThatThrownBy(() -> useCase.executar(OS)).isInstanceOf(AcessoARecursoDeOutroClienteException.class);
    }

    @Test
    void semPagamentoVigenteDa404() {
        autenticar(TipoPrincipal.FUNCIONARIO, UUID.randomUUID());
        UUID semPagamento = UUID.randomUUID();
        when(repositorio.listarPorOs(semPagamento)).thenReturn(List.of());
        UUID soSubstituidos = UUID.randomUUID();
        when(repositorio.listarPorOs(soSubstituidos)).thenReturn(List.of(antigo));

        assertThatThrownBy(() -> useCase.executar(semPagamento)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThatThrownBy(() -> useCase.executar(soSubstituidos)).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
