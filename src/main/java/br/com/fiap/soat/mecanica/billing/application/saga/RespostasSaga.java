package br.com.fiap.soat.mecanica.billing.application.saga;

import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemFactory;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Monta as respostas do Billing aos comandos da Saga (contratos em docs/contratos). Cada resposta e o tipo e o
 * payload, que o {@link ParticipanteSaga} guarda em comando_atendido e registra na outbox.
 */
@Component
public class RespostasSaga {

    public static final String DESTINO = "billing-eventos";
    public static final String ORCAMENTO_GERADO = "OrcamentoGerado";
    public static final String ORCAMENTO_FALHOU = "OrcamentoFalhou";
    static final int TAMANHO_MAXIMO_MOTIVO = 500;

    private final MensagemFactory mensagens;
    private final ObjectMapper objectMapper;

    public RespostasSaga(MensagemFactory mensagens, ObjectMapper objectMapper) {
        this.mensagens = mensagens;
        this.objectMapper = objectMapper;
    }

    /** Tipo e payload de uma resposta, prontos para guardar e para virar envelope. */
    public record Resposta(String tipo, JsonNode payload) {
    }

    public Resposta orcamentoGerado(Orcamento o) {
        return resposta(ORCAMENTO_GERADO, Map.of("osId", o.getOsId(), "orcamentoId", o.getId(),
                "valorTotal", o.getValorTotal(), "expiraEm", o.getExpiraEm().toString()));
    }

    public Resposta orcamentoFalhou(UUID osId, String motivo) {
        String curto = motivo.length() <= TAMANHO_MAXIMO_MOTIVO ? motivo : motivo.substring(0, TAMANHO_MAXIMO_MOTIVO);
        return resposta(ORCAMENTO_FALHOU, Map.of("osId", osId, "motivo", curto));
    }

    /** Envelope novo (messageId novo) para a resposta, na primeira emissao e na reemissao. */
    public MensagemEnvelope envelope(Resposta resposta, UUID sagaId, UUID osId) {
        return mensagens.criar(resposta.tipo(), 1, sagaId, osId, resposta.payload());
    }

    private Resposta resposta(String tipo, Object payload) {
        return new Resposta(tipo, objectMapper.valueToTree(payload));
    }
}
