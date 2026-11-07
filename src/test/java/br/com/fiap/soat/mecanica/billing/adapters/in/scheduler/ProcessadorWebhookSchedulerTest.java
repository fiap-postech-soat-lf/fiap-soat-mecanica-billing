package br.com.fiap.soat.mecanica.billing.adapters.in.scheduler;

import br.com.fiap.soat.mecanica.billing.application.pagamento.usecase.ProcessarNotificacaoPagamentoUseCase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessadorWebhookSchedulerTest {

    final ProcessarNotificacaoPagamentoUseCase processar = mock(ProcessarNotificacaoPagamentoUseCase.class);
    final ProcessadorWebhookScheduler scheduler = new ProcessadorWebhookScheduler(processar);

    @Test
    void disparaOProcessamento() {
        when(processar.executar()).thenReturn(2);
        scheduler.agendado();
        verify(processar).executar();
    }

    @Test
    void falhaNaoDerrubaOScheduler() {
        when(processar.executar()).thenThrow(new IllegalStateException("banco fora"));
        assertThatCode(scheduler::agendado).doesNotThrowAnyException();
    }
}
