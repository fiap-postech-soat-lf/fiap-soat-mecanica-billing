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

    /** SolicitarPagamento sem orcamento APROVADO, ou com pagamento vigente: sem resposta (tabela 4.1 da F6). */
    public static void comandoForaDeEstado(UUID osId, String tipoComando, String motivo) {
        log.atWarn()
                .addKeyValue("event", "comando_fora_de_estado")
                .addKeyValue("orderServiceId", osId)
                .addKeyValue("comando", tipoComando)
                .addKeyValue("motivo", motivo)
                .log("Comando fora de estado; sem resposta");
    }

    /** O valor do comando difere do orcamento aprovado: cobra-se o do orcamento (F6-g). */
    public static void valorDivergente(UUID osId, java.math.BigDecimal valorComando, java.math.BigDecimal valorOrcamento) {
        log.atWarn()
                .addKeyValue("event", "valor_divergente")
                .addKeyValue("orderServiceId", osId)
                .addKeyValue("valorComando", valorComando)
                .addKeyValue("valorOrcamento", valorOrcamento)
                .log("Valor do comando diverge do orcamento aprovado");
    }

    public static void mercadoPagoTokenAusente() {
        log.atError()
                .addKeyValue("event", "mercadopago_token_ausente")
                .log("Mercado Pago access token is not configured");
    }

    public static void mercadoPagoIndisponivel(String operacao, String causa) {
        log.atWarn()
                .addKeyValue("event", "mercadopago_indisponivel")
                .addKeyValue("operacao", operacao)
                .addKeyValue("causa", causa)
                .log("Mercado Pago unavailable after retries");
    }
}
