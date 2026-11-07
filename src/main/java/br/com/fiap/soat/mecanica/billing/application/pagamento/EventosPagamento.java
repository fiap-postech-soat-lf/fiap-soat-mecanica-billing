package br.com.fiap.soat.mecanica.billing.application.pagamento;

import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemFactory;
import br.com.fiap.soat.mecanica.billing.application.mensageria.OutboxPort;
import br.com.fiap.soat.mecanica.billing.application.saga.RespostasSaga;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.Pagamento;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Eventos de pagamento nascidos no webhook (tabela 8 da F6): usam o sagaId guardado no orcamento e vao para a
 * outbox na transacao do chamador.
 */
@Component
public class EventosPagamento {

    static final int TAMANHO_MAXIMO_MOTIVO = 500;

    private final MensagemFactory mensagens;
    private final OutboxPort outbox;

    public EventosPagamento(MensagemFactory mensagens, OutboxPort outbox) {
        this.mensagens = mensagens;
        this.outbox = outbox;
    }

    public void confirmado(Pagamento p, UUID sagaId) {
        outbox.registrar(RespostasSaga.DESTINO, mensagens.criar("PagamentoConfirmado", 1, sagaId, p.getOsId(),
                Map.of("osId", p.getOsId(), "pagamentoId", p.getId(), "mpPaymentId", p.getMpPaymentId(),
                        "status", "APROVADO")));
    }

    public void recusado(Pagamento p, UUID sagaId) {
        String motivo = p.getMotivo() == null || p.getMotivo().isBlank() ? "recusado" : p.getMotivo();
        String curto = motivo.substring(0, Math.min(motivo.length(), TAMANHO_MAXIMO_MOTIVO));
        outbox.registrar(RespostasSaga.DESTINO, mensagens.criar("PagamentoRecusado", 1, sagaId, p.getOsId(),
                Map.of("osId", p.getOsId(), "pagamentoId", p.getId(), "mpPaymentId", p.getMpPaymentId(),
                        "status", "RECUSADO", "motivo", curto)));
    }
}
