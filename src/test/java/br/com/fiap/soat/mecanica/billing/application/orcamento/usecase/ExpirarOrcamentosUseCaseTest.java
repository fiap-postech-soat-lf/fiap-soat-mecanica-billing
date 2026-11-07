package br.com.fiap.soat.mecanica.billing.application.orcamento.usecase;

import br.com.fiap.soat.mecanica.billing.application.orcamento.EventosOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.ItemOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.StatusOrcamento;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpirarOrcamentosUseCaseTest {

    static final Instant AGORA = Instant.parse("2026-11-07T12:00:00Z");

    OrcamentoRepository orcamentos = mock(OrcamentoRepository.class);
    EventosOrcamento eventos = mock(EventosOrcamento.class);
    PlatformTransactionManager tm = mock(PlatformTransactionManager.class);
    ExpirarOrcamentosUseCase useCase = new ExpirarOrcamentosUseCase(orcamentos, eventos,
            new TransactionTemplate(tm), Clock.fixed(AGORA, ZoneOffset.UTC));

    private static Orcamento vencido() {
        return Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(
                ItemOrcamento.servico(UUID.randomUUID(), "Servico", new BigDecimal("50.00"))),
                new BigDecimal("50.00"), Duration.ofHours(1), AGORA.minus(Duration.ofHours(2)));
    }

    @Test
    void expiraCadaVencidoEEmiteUmEventoPorOrcamento() {
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        Orcamento a = vencido();
        Orcamento b = vencido();
        when(orcamentos.travarVencidos(AGORA, 20)).thenReturn(List.of(a, b));
        when(orcamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));

        int expirados = useCase.executar();

        assertThat(expirados).isEqualTo(2);
        assertThat(a.getStatus()).isEqualTo(StatusOrcamento.EXPIRADO);
        assertThat(b.getStatus()).isEqualTo(StatusOrcamento.EXPIRADO);
        verify(eventos).expirado(a);
        verify(eventos).expirado(b);
        verify(tm).commit(any());
    }

    @Test
    void semVencidosNaoFazNada() {
        when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(orcamentos.travarVencidos(AGORA, 20)).thenReturn(List.of());

        assertThat(useCase.executar()).isZero();

        verify(orcamentos, never()).salvar(any());
    }
}
