package br.com.fiap.soat.mecanica.billing.adapters.in.scheduler;

import br.com.fiap.soat.mecanica.billing.application.pagamento.usecase.ProcessarNotificacaoPagamentoUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Processa as notificacoes do Mercado Pago recebidas (F6-i); o intervalo vem de app.webhook.processador.intervalo. */
@Slf4j
@Component
public class ProcessadorWebhookScheduler {

    private final ProcessarNotificacaoPagamentoUseCase processar;

    public ProcessadorWebhookScheduler(ProcessarNotificacaoPagamentoUseCase processar) {
        this.processar = processar;
    }

    @Scheduled(fixedDelayString = "${app.webhook.processador.intervalo:PT2S}")
    public void agendado() {
        try {
            int tratados = processar.executar();
            if (tratados > 0) {
                log.atInfo().addKeyValue("event", "webhooks_processados").addKeyValue("quantidade", tratados)
                        .log("Notificacoes do Mercado Pago processadas");
            }
        } catch (RuntimeException e) {
            log.atWarn().addKeyValue("event", "processador_webhook_falhou").setCause(e)
                    .log("Falha ao processar notificacoes; nova tentativa na proxima rodada");
        }
    }
}
