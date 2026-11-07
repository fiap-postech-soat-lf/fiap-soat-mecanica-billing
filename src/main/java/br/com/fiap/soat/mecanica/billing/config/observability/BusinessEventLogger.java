package br.com.fiap.soat.mecanica.billing.config.observability;

import br.com.fiap.soat.mecanica.billing.domain.orcamento.StatusOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.StatusPagamento;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/** Eventos de negocio em log estruturado. Nunca registra dados de cartao nem do pagador. */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BusinessEventLogger {

    /** {@code de} e nulo na criacao. */
    public static void orcamentoStatus(UUID orcamentoId, UUID osId, StatusOrcamento de, StatusOrcamento para,
                                       String motivo) {
        log.atInfo()
                .addKeyValue("event", "orcamento_status")
                .addKeyValue("orcamentoId", orcamentoId)
                .addKeyValue("orderServiceId", osId)
                .addKeyValue("de", de)
                .addKeyValue("para", para)
                .addKeyValue("motivo", motivo)
                .log("Orcamento status changed");
    }

    public static void pagamentoStatus(UUID pagamentoId, UUID osId, StatusPagamento de, StatusPagamento para,
                                       String motivo) {
        log.atInfo()
                .addKeyValue("event", "pagamento_status")
                .addKeyValue("pagamentoId", pagamentoId)
                .addKeyValue("orderServiceId", osId)
                .addKeyValue("de", de)
                .addKeyValue("para", para)
                .addKeyValue("motivo", motivo)
                .log("Pagamento status changed");
    }

    public static void cobrancaCriada(UUID pagamentoId, UUID osId) {
        log.atInfo()
                .addKeyValue("event", "cobranca_criada")
                .addKeyValue("pagamentoId", pagamentoId)
                .addKeyValue("orderServiceId", osId)
                .log("Cobranca created");
    }

    public static void orcamentoFalhou(UUID osId, String motivo) {
        log.atWarn()
                .addKeyValue("event", "orcamento_falhou")
                .addKeyValue("orderServiceId", osId)
                .addKeyValue("motivo", motivo)
                .log("Orcamento failed");
    }

    public static void cancelamentoSemEfeito(UUID osId, UUID orcamentoId, String motivo) {
        log.atInfo()
                .addKeyValue("event", "cancelamento_sem_efeito")
                .addKeyValue("orcamentoId", orcamentoId)
                .addKeyValue("orderServiceId", osId)
                .addKeyValue("motivo", motivo)
                .log("Cancelamento without effect");
    }

    public static void comandoReemitido(UUID osId, String tipoComando, String tipoResposta) {
        log.atInfo()
                .addKeyValue("event", "comando_reemitido")
                .addKeyValue("orderServiceId", osId)
                .addKeyValue("comando", tipoComando)
                .addKeyValue("resposta", tipoResposta)
                .log("Comando reenviado; resposta reemitida");
    }
}
