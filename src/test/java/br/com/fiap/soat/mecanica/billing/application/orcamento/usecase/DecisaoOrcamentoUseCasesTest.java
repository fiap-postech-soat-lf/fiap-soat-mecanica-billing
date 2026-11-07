package br.com.fiap.soat.mecanica.billing.application.orcamento.usecase;

import br.com.fiap.soat.mecanica.billing.application.orcamento.EventosOrcamento;
import br.com.fiap.soat.mecanica.billing.application.security.TipoPrincipal;
import br.com.fiap.soat.mecanica.billing.application.security.UsuarioAutenticadoPort;
import br.com.fiap.soat.mecanica.billing.domain.exception.AcessoARecursoDeOutroClienteException;
import br.com.fiap.soat.mecanica.billing.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.billing.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.ItemOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.StatusOrcamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Aprovar e recusar: os 4 casos do dono (CLIENTE dono, CLIENTE de outro, ATENDENTE, MECANICO) e as regras de estado. */
class DecisaoOrcamentoUseCasesTest {

    static final Instant AGORA = Instant.parse("2026-11-07T12:00:00Z");
    static final UUID CLIENTE = UUID.randomUUID();

    OrcamentoRepository repositorio = mock(OrcamentoRepository.class);
    UsuarioAutenticadoPort usuario = mock(UsuarioAutenticadoPort.class);
    EventosOrcamento eventos = mock(EventosOrcamento.class);
    Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);
    AprovarOrcamentoUseCase aprovar = new AprovarOrcamentoUseCase(repositorio, usuario, eventos, clock);
    RecusarOrcamentoUseCase recusar = new RecusarOrcamentoUseCase(repositorio, usuario, eventos, clock);
    Orcamento orcamento;

    @BeforeEach
    void preparar() {
        orcamento = Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), CLIENTE,
                List.of(ItemOrcamento.servico(UUID.randomUUID(), "Revisao", new BigDecimal("100.00"))),
                new BigDecimal("100.00"), Duration.ofHours(24), AGORA);
        when(repositorio.buscarPorId(orcamento.getId())).thenReturn(Optional.of(orcamento));
        when(repositorio.salvar(any())).thenAnswer(i -> i.getArgument(0));
    }

    private void comoCliente(UUID sub) {
        when(usuario.sub()).thenReturn(sub);
        when(usuario.tipo()).thenReturn(TipoPrincipal.CLIENTE);
        when(usuario.isCliente()).thenReturn(true);
    }

    private void comoFuncionario(UUID sub) {
        when(usuario.sub()).thenReturn(sub);
        when(usuario.tipo()).thenReturn(TipoPrincipal.FUNCIONARIO);
        when(usuario.isCliente()).thenReturn(false);
    }

    @Test
    void clienteDonoAprova() {
        comoCliente(CLIENTE);

        Orcamento salvo = aprovar.executar(orcamento.getId());

        assertThat(salvo.getStatus()).isEqualTo(StatusOrcamento.APROVADO);
        assertThat(salvo.getDecididoPor()).isEqualTo(CLIENTE);
        verify(eventos).aprovado(salvo, CLIENTE);
    }

    @Test
    void clienteDeOutroNaoAprovaENadaEGravado() {
        comoCliente(UUID.randomUUID());

        assertThatThrownBy(() -> aprovar.executar(orcamento.getId()))
                .isInstanceOf(AcessoARecursoDeOutroClienteException.class);

        assertThat(orcamento.getStatus()).isEqualTo(StatusOrcamento.GERADO);
        verify(repositorio, never()).salvar(any());
        verifyNoInteractions(eventos);
    }

    @Test
    void atendenteAprovaEmNomeDoCliente() {
        UUID atendente = UUID.randomUUID();
        comoFuncionario(atendente);

        Orcamento salvo = aprovar.executar(orcamento.getId());

        assertThat(salvo.getStatus()).isEqualTo(StatusOrcamento.APROVADO);
        assertThat(salvo.getDecididoPor()).isEqualTo(atendente);
        verify(eventos).aprovado(salvo, atendente);
    }

    @Test
    void mecanicoSemPermissaoNaoChegaAoCasoDeUsoMasOCasoDeUsoNaoDependeDoCargo() {
        // MECANICO e barrado pelo @PreAuthorize do controller (403); aqui o caso de uso so aplica a regra de dono
        comoFuncionario(UUID.randomUUID());

        assertThat(aprovar.executar(orcamento.getId()).getStatus()).isEqualTo(StatusOrcamento.APROVADO);
    }

    @Test
    void aprovarInexistenteDa404() {
        comoCliente(CLIENTE);
        UUID inexistente = UUID.randomUUID();
        when(repositorio.buscarPorId(inexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aprovar.executar(inexistente)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void aprovarDepoisDeExpiraEmFalhaMesmoAntesDoScheduler() {
        comoCliente(CLIENTE);
        AprovarOrcamentoUseCase tardio = new AprovarOrcamentoUseCase(repositorio, usuario, eventos,
                Clock.fixed(AGORA.plus(Duration.ofHours(25)), ZoneOffset.UTC));

        assertThatThrownBy(() -> tardio.executar(orcamento.getId())).isInstanceOf(RegraNegocioException.class);

        verifyNoInteractions(eventos);
    }

    @Test
    void clienteDonoRecusaComMotivo() {
        comoCliente(CLIENTE);

        Orcamento salvo = recusar.executar(orcamento.getId(), "Caro");

        assertThat(salvo.getStatus()).isEqualTo(StatusOrcamento.RECUSADO);
        assertThat(salvo.getMotivo()).isEqualTo("Caro");
        verify(eventos).recusado(salvo, CLIENTE);
    }

    @Test
    void clienteDeOutroNaoRecusa() {
        comoCliente(UUID.randomUUID());

        assertThatThrownBy(() -> recusar.executar(orcamento.getId(), null))
                .isInstanceOf(AcessoARecursoDeOutroClienteException.class);

        verifyNoInteractions(eventos);
        verify(repositorio, never()).salvar(any());
    }

    @Test
    void atendenteRecusaEmNomeDoClienteSemMotivo() {
        UUID atendente = UUID.randomUUID();
        comoFuncionario(atendente);

        Orcamento salvo = recusar.executar(orcamento.getId(), null);

        assertThat(salvo.getDecididoPor()).isEqualTo(atendente);
        verify(eventos).recusado(salvo, atendente);
    }

    @Test
    void recusarJaDecididoFalha() {
        comoCliente(CLIENTE);
        recusar.executar(orcamento.getId(), null);

        assertThatThrownBy(() -> recusar.executar(orcamento.getId(), null)).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void recusarInexistenteDa404() {
        comoCliente(CLIENTE);
        UUID inexistente = UUID.randomUUID();
        when(repositorio.buscarPorId(inexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recusar.executar(inexistente, null)).isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
