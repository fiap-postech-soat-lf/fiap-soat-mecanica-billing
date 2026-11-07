package br.com.fiap.soat.mecanica.billing.adapters.in.scheduler;

import br.com.fiap.soat.mecanica.billing.application.orcamento.usecase.ExpirarOrcamentosUseCase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpiracaoOrcamentoSchedulerTest {

    ExpirarOrcamentosUseCase useCase = mock(ExpirarOrcamentosUseCase.class);
    ExpiracaoOrcamentoScheduler scheduler = new ExpiracaoOrcamentoScheduler(useCase);

    @Test
    void cadaRodadaChamaOCasoDeUso() {
        when(useCase.executar()).thenReturn(3);

        scheduler.agendado();

        verify(useCase).executar();
    }

    @Test
    void falhaNaoDerrubaOAgendamento() {
        when(useCase.executar()).thenThrow(new IllegalStateException("banco fora"));

        assertThatCode(scheduler::agendado).doesNotThrowAnyException();
    }
}
