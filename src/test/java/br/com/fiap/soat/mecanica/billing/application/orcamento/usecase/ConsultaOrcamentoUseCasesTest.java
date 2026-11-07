package br.com.fiap.soat.mecanica.billing.application.orcamento.usecase;

import br.com.fiap.soat.mecanica.billing.application.security.TipoPrincipal;
import br.com.fiap.soat.mecanica.billing.application.security.UsuarioAutenticadoPort;
import br.com.fiap.soat.mecanica.billing.domain.exception.AcessoARecursoDeOutroClienteException;
import br.com.fiap.soat.mecanica.billing.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.ItemOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsultaOrcamentoUseCasesTest {

    static final UUID CLIENTE = UUID.randomUUID();

    OrcamentoRepository repositorio = mock(OrcamentoRepository.class);
    UsuarioAutenticadoPort usuario = mock(UsuarioAutenticadoPort.class);
    BuscarOrcamentoUseCase buscar = new BuscarOrcamentoUseCase(repositorio, usuario);
    BuscarOrcamentoPorOsUseCase buscarPorOs = new BuscarOrcamentoPorOsUseCase(repositorio, usuario);
    Orcamento orcamento;

    @BeforeEach
    void preparar() {
        orcamento = Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), CLIENTE,
                List.of(ItemOrcamento.servico(UUID.randomUUID(), "Revisao", new BigDecimal("100.00"))),
                new BigDecimal("100.00"), Duration.ofHours(24), Instant.parse("2026-11-07T12:00:00Z"));
    }

    private void autenticar(TipoPrincipal tipo, UUID sub) {
        when(usuario.sub()).thenReturn(sub);
        when(usuario.tipo()).thenReturn(tipo);
        when(usuario.isCliente()).thenReturn(tipo == TipoPrincipal.CLIENTE);
    }

    @Test
    void clienteDonoEAtendenteLeemPorId() {
        when(repositorio.buscarPorId(orcamento.getId())).thenReturn(Optional.of(orcamento));

        autenticar(TipoPrincipal.CLIENTE, CLIENTE);
        assertThat(buscar.executar(orcamento.getId())).isSameAs(orcamento);
        autenticar(TipoPrincipal.FUNCIONARIO, UUID.randomUUID());
        assertThat(buscar.executar(orcamento.getId())).isSameAs(orcamento);
    }

    @Test
    void clienteDeOutroNaoLePorId() {
        when(repositorio.buscarPorId(orcamento.getId())).thenReturn(Optional.of(orcamento));
        autenticar(TipoPrincipal.CLIENTE, UUID.randomUUID());

        assertThatThrownBy(() -> buscar.executar(orcamento.getId()))
                .isInstanceOf(AcessoARecursoDeOutroClienteException.class);
    }

    @Test
    void idInexistenteDa404() {
        UUID id = UUID.randomUUID();
        when(repositorio.buscarPorId(id)).thenReturn(Optional.empty());
        autenticar(TipoPrincipal.CLIENTE, CLIENTE);

        assertThatThrownBy(() -> buscar.executar(id)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void porOsPrefereOVivo() {
        when(repositorio.buscarVivoPorOs(orcamento.getOsId())).thenReturn(Optional.of(orcamento));
        autenticar(TipoPrincipal.CLIENTE, CLIENTE);

        assertThat(buscarPorOs.executar(orcamento.getOsId())).isSameAs(orcamento);
    }

    @Test
    void porOsSemVivoDevolveOMaisRecente() {
        when(repositorio.buscarVivoPorOs(orcamento.getOsId())).thenReturn(Optional.empty());
        when(repositorio.buscarMaisRecentePorOs(orcamento.getOsId())).thenReturn(Optional.of(orcamento));
        autenticar(TipoPrincipal.FUNCIONARIO, UUID.randomUUID());

        assertThat(buscarPorOs.executar(orcamento.getOsId())).isSameAs(orcamento);
    }

    @Test
    void porOsDeOutroClienteDa403ESemOrcamentoDa404() {
        when(repositorio.buscarVivoPorOs(orcamento.getOsId())).thenReturn(Optional.of(orcamento));
        autenticar(TipoPrincipal.CLIENTE, UUID.randomUUID());
        assertThatThrownBy(() -> buscarPorOs.executar(orcamento.getOsId()))
                .isInstanceOf(AcessoARecursoDeOutroClienteException.class);

        UUID semOrcamento = UUID.randomUUID();
        when(repositorio.buscarVivoPorOs(semOrcamento)).thenReturn(Optional.empty());
        when(repositorio.buscarMaisRecentePorOs(semOrcamento)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> buscarPorOs.executar(semOrcamento)).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
