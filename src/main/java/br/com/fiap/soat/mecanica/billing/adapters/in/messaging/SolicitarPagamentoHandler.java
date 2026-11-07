package br.com.fiap.soat.mecanica.billing.adapters.in.messaging;

import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemHandler;
import br.com.fiap.soat.mecanica.billing.application.saga.ParticipanteSaga;
import br.com.fiap.soat.mecanica.billing.application.saga.dto.SolicitarPagamentoPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Comando {@code SolicitarPagamento} do OS (fila billing-comandos). */
@Component
public class SolicitarPagamentoHandler implements MensagemHandler {

    private final ParticipanteSaga participante;
    private final ObjectMapper objectMapper;

    public SolicitarPagamentoHandler(ParticipanteSaga participante, ObjectMapper objectMapper) {
        this.participante = participante;
        this.objectMapper = objectMapper;
    }

    @Override
    public String tipo() {
        return ParticipanteSaga.SOLICITAR_PAGAMENTO;
    }

    @Override
    public void processar(MensagemEnvelope envelope) {
        participante.solicitarPagamento(envelope, PayloadsSaga.ler(objectMapper, envelope, SolicitarPagamentoPayload.class));
    }
}
