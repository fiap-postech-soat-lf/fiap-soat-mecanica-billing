package br.com.fiap.soat.mecanica.billing.application.pagamento.usecase;

import br.com.fiap.soat.mecanica.billing.domain.webhook.WebhookEvento;
import br.com.fiap.soat.mecanica.billing.domain.webhook.WebhookEventoRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;

/** Grava a notificacao como PENDENTE e volta na hora (F6-i); o processamento e do scheduler. */
@Service
public class RegistrarNotificacaoUseCase {

    private final WebhookEventoRepository eventos;
    private final Clock clock;

    public RegistrarNotificacaoUseCase(WebhookEventoRepository eventos, Clock clock) {
        this.eventos = eventos;
        this.clock = clock;
    }

    public WebhookEvento executar(String requestId, String mpPaymentId, String tipo, String acao) {
        return eventos.salvar(WebhookEvento.receber(requestId, mpPaymentId, tipo, acao, clock.instant()));
    }
}
