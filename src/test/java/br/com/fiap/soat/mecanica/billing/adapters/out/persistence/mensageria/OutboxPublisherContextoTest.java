package br.com.fiap.soat.mecanica.billing.adapters.out.persistence.mensageria;

import br.com.fiap.soat.mecanica.billing.adapters.out.messaging.SnsSqsPublicador;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** O span do SDK precisa nascer como filho do trace gravado no envelope, e nao do {@code @Scheduled}. */
class OutboxPublisherContextoTest {

    private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";

    @Test
    void publicaComOTraceDoEnvelopeComoContextoAtualEDevolveOAnteriorDepois() {
        OutboxEntity mensagem = new OutboxEntity();
        mensagem.setDestino("teste-eventos");
        mensagem.setTipo("X");
        mensagem.setEnvelope("{\"traceparent\":\"00-" + TRACE_ID + "-b7ad6b7169203331-01\"}");

        OutboxJpaRepository repository = mock(OutboxJpaRepository.class);
        when(repository.travarPendentes(OutboxPublisher.LOTE)).thenReturn(List.of(mensagem));
        TransactionTemplate transacao = mock(TransactionTemplate.class);
        when(transacao.execute(any())).thenAnswer(i -> i.<TransactionCallback<?>>getArgument(0).doInTransaction(null));

        List<SpanContext> vistos = new ArrayList<>();
        SnsSqsPublicador publicador = new SnsSqsPublicador(null, null, null) {
            @Override
            public void publicar(String destino, String tipo, String envelopeJson) {
                vistos.add(Span.current().getSpanContext());
            }
        };

        int publicadas = new OutboxPublisher(repository, publicador, transacao).publicarPendentes();

        assertThat(publicadas).isEqualTo(1);
        assertThat(vistos).singleElement().satisfies(span -> assertThat(span.getTraceId()).isEqualTo(TRACE_ID));
        assertThat(Span.current().getSpanContext().isValid()).isFalse();
    }
}
