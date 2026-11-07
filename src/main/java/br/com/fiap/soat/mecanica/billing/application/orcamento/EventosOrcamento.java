package br.com.fiap.soat.mecanica.billing.application.orcamento;

import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemFactory;
import br.com.fiap.soat.mecanica.billing.application.mensageria.OutboxPort;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Eventos de orcamento nascidos fora de um comando (REST e scheduler): usam o sagaId guardado no
 * orcamento e vao para a outbox na transacao do chamador (tabela 8 da F6).
 */
@Component
public class EventosOrcamento {

    public static final String DESTINO = "billing-eventos";

    private final MensagemFactory mensagens;
    private final OutboxPort outbox;

    public EventosOrcamento(MensagemFactory mensagens, OutboxPort outbox) {
        this.mensagens = mensagens;
        this.outbox = outbox;
    }

    public void aprovado(Orcamento o, UUID aprovadoPor) {
        publicar("OrcamentoAprovado", o, Map.of("osId", o.getOsId(), "orcamentoId", o.getId(),
                "aprovadoPor", aprovadoPor));
    }

    public void recusado(Orcamento o, UUID recusadoPor) {
        publicar("OrcamentoRecusado", o, Map.of("osId", o.getOsId(), "orcamentoId", o.getId(),
                "recusadoPor", recusadoPor));
    }

    public void expirado(Orcamento o) {
        publicar("OrcamentoExpirado", o, Map.of("osId", o.getOsId(), "orcamentoId", o.getId()));
    }

    private void publicar(String tipo, Orcamento o, Map<String, Object> payload) {
        outbox.registrar(DESTINO, mensagens.criar(tipo, 1, o.getSagaId(), o.getOsId(), payload));
    }
}
