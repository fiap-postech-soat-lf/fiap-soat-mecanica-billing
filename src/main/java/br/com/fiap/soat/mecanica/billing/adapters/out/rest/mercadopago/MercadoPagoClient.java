package br.com.fiap.soat.mecanica.billing.adapters.out.rest.mercadopago;

import br.com.fiap.soat.mecanica.billing.adapters.out.rest.mercadopago.dto.PaymentResponse;
import br.com.fiap.soat.mecanica.billing.adapters.out.rest.mercadopago.dto.PreferenceRequest;
import br.com.fiap.soat.mecanica.billing.adapters.out.rest.mercadopago.dto.PreferenceResponse;
import br.com.fiap.soat.mecanica.billing.application.pagamento.exception.MercadoPagoIndisponivelException;
import br.com.fiap.soat.mecanica.billing.application.pagamento.exception.MercadoPagoTokenAusenteException;
import br.com.fiap.soat.mecanica.billing.application.pagamento.port.GatewayPagamentoPort;
import br.com.fiap.soat.mecanica.billing.application.pagamento.port.PagamentoMp;
import br.com.fiap.soat.mecanica.billing.application.pagamento.port.Preferencia;
import br.com.fiap.soat.mecanica.billing.config.observability.BusinessEventLogger;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cliente do Mercado Pago (F6-b): duas chamadas, {@code POST /checkout/preferences} e
 * {@code GET /v1/payments/{id}}. Retry de 3 tentativas (500 ms x 2) so para I/O, timeout e 5xx; 4xx nao e repetido.
 * Metrica {@code mercadopago.erros} (tag {@code operacao}) a cada falha final.
 */
@Component
public class MercadoPagoClient implements GatewayPagamentoPort {

    static final String METRICA_ERROS = "mercadopago.erros";
    static final String OPERACAO_PREFERENCIA = "preferencia";
    static final String OPERACAO_PAGAMENTO = "pagamento";
    static final int TAMANHO_MAXIMO_CORPO_NO_ERRO = 200;

    private final RestClient restClient;
    private final MercadoPagoProperties propriedades;
    private final MeterRegistry metricas;
    private final Retry retry;

    public MercadoPagoClient(RestClient mercadoPagoRestClient, MercadoPagoProperties propriedades,
                             MeterRegistry metricas) {
        this.restClient = mercadoPagoRestClient;
        this.propriedades = propriedades;
        this.metricas = metricas;
        MercadoPagoProperties.Retry config = propriedades.retry();
        this.retry = Retry.of("mercadopago", RetryConfig.custom()
                .maxAttempts(config.maxTentativas())
                .intervalFunction(IntervalFunction.ofExponentialBackoff(config.esperaInicial(), config.multiplicador()))
                .retryOnException(MercadoPagoClient::repetivel)
                .build());
    }

    @Override
    public Preferencia criarPreferencia(UUID pagamentoId, String titulo, BigDecimal valor) {
        String token = token(OPERACAO_PREFERENCIA);
        PreferenceRequest corpo = PreferenceRequest.de(titulo, valor, pagamentoId.toString());
        PreferenceResponse resposta = executar(OPERACAO_PREFERENCIA, () -> restClient.post()
                .uri("/checkout/preferences")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Idempotency-Key", pagamentoId.toString())
                .body(corpo)
                .retrieve()
                .body(PreferenceResponse.class));
        if (resposta == null || resposta.id() == null) {
            throw erroFinal(OPERACAO_PREFERENCIA, new IllegalStateException("Mercado Pago devolveu preferencia sem id"));
        }
        return new Preferencia(resposta.id(), resposta.initPoint(), resposta.sandboxInitPoint());
    }

    @Override
    public PagamentoMp consultarPagamento(String mpPaymentId) {
        String token = token(OPERACAO_PAGAMENTO);
        PaymentResponse resposta = executar(OPERACAO_PAGAMENTO, () -> restClient.get()
                .uri("/v1/payments/{id}", mpPaymentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(PaymentResponse.class));
        if (resposta == null) {
            throw erroFinal(OPERACAO_PAGAMENTO, new IllegalStateException("Mercado Pago devolveu pagamento vazio"));
        }
        return new PagamentoMp(resposta.id(), resposta.status(), resposta.statusDetail(),
                resposta.externalReference());
    }

    private String token(String operacao) {
        String token = propriedades.accessToken();
        if (token.isBlank()) {
            BusinessEventLogger.mercadoPagoTokenAusente();
            metricas.counter(METRICA_ERROS, "operacao", operacao).increment();
            throw new MercadoPagoTokenAusenteException();
        }
        return token;
    }

    private <T> T executar(String operacao, Supplier<T> chamada) {
        try {
            return Retry.decorateSupplier(retry, chamada).get();
        } catch (HttpServerErrorException | ResourceAccessException e) {
            BusinessEventLogger.mercadoPagoIndisponivel(operacao, e.getClass().getSimpleName());
            throw erroFinal(operacao, new MercadoPagoIndisponivelException(
                    "Mercado Pago indisponivel (" + operacao + ")", e));
        } catch (HttpClientErrorException e) {
            throw erroFinal(operacao, new IllegalStateException("Mercado Pago recusou a chamada (" + operacao
                    + ", HTTP " + e.getStatusCode().value() + "): " + resumo(e.getResponseBodyAsString()), e));
        }
    }

    private <E extends RuntimeException> E erroFinal(String operacao, E erro) {
        metricas.counter(METRICA_ERROS, "operacao", operacao).increment();
        return erro;
    }

    /** O corpo da resposta de erro nao carrega o token, mas e cortado para nao poluir o log. */
    private static String resumo(String corpo) {
        if (corpo == null) {
            return "";
        }
        return corpo.length() <= TAMANHO_MAXIMO_CORPO_NO_ERRO ? corpo : corpo.substring(0, TAMANHO_MAXIMO_CORPO_NO_ERRO);
    }

    private static boolean repetivel(Throwable erro) {
        return erro instanceof ResourceAccessException || erro instanceof HttpServerErrorException;
    }
}
