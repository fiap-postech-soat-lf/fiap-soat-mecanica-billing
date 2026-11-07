package br.com.fiap.soat.mecanica.billing.adapters.in.scheduler;

import br.com.fiap.soat.mecanica.billing.application.orcamento.usecase.ExpirarOrcamentosUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Dispara a expiracao dos orcamentos vencidos (F6-f); o intervalo vem de app.orcamento.expiracao.intervalo. */
@Slf4j
@Component
public class ExpiracaoOrcamentoScheduler {

    private final ExpirarOrcamentosUseCase expirar;

    public ExpiracaoOrcamentoScheduler(ExpirarOrcamentosUseCase expirar) {
        this.expirar = expirar;
    }

    @Scheduled(fixedDelayString = "${app.orcamento.expiracao.intervalo:PT30S}")
    public void agendado() {
        try {
            int expirados = expirar.executar();
            if (expirados > 0) {
                log.atInfo().addKeyValue("event", "orcamentos_expirados").addKeyValue("quantidade", expirados)
                        .log("Orcamentos vencidos expirados");
            }
        } catch (RuntimeException e) {
            log.atWarn().addKeyValue("event", "expiracao_falhou").setCause(e)
                    .log("Falha ao expirar orcamentos; nova tentativa na proxima rodada");
        }
    }
}
