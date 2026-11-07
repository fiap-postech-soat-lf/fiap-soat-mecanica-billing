package br.com.fiap.soat.mecanica.billing.adapters.in.messaging;

import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemHandler;
import br.com.fiap.soat.mecanica.billing.application.saga.ParticipanteSaga;
import br.com.fiap.soat.mecanica.billing.application.saga.dto.GerarOrcamentoPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/** Comando {@code GerarOrcamento} do OS (fila billing-comandos). */
@Component
public class GerarOrcamentoHandler implements MensagemHandler {

    private final ParticipanteSaga participante;
    private final ObjectMapper objectMapper;

    public GerarOrcamentoHandler(ParticipanteSaga participante, ObjectMapper objectMapper) {
        this.participante = participante;
        this.objectMapper = objectMapper;
    }

    @Override
    public String tipo() {
        return ParticipanteSaga.GERAR_ORCAMENTO;
    }

    @Override
    public void processar(MensagemEnvelope envelope) {
        participante.gerarOrcamento(envelope, PayloadsSaga.ler(objectMapper, envelope, GerarOrcamentoPayload.class));
    }
}
