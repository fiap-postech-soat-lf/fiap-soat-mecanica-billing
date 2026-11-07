package br.com.fiap.soat.mecanica.billing.adapters.in.messaging;

import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemHandler;
import br.com.fiap.soat.mecanica.billing.application.saga.ParticipanteSaga;
import br.com.fiap.soat.mecanica.billing.application.saga.dto.CancelarOrcamentoPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Comando {@code CancelarOrcamento} do OS (fila billing-comandos). */
@Component
public class CancelarOrcamentoHandler implements MensagemHandler {

    private final ParticipanteSaga participante;
    private final ObjectMapper objectMapper;

    public CancelarOrcamentoHandler(ParticipanteSaga participante, ObjectMapper objectMapper) {
        this.participante = participante;
        this.objectMapper = objectMapper;
    }

    @Override
    public String tipo() {
        return ParticipanteSaga.CANCELAR_ORCAMENTO;
    }

    @Override
    public void processar(MensagemEnvelope envelope) {
        participante.cancelarOrcamento(envelope, PayloadsSaga.ler(objectMapper, envelope, CancelarOrcamentoPayload.class));
    }
}
