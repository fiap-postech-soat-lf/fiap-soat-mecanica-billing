package br.com.fiap.soat.mecanica.billing.application.pagamento;

import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemFactory;
import br.com.fiap.soat.mecanica.billing.application.mensageria.OutboxPort;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.Pagamento;
import br.com.fiap.soat.mecanica.billing.support.Dados;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EventosPagamentoTest {

    OutboxPort outbox = mock(OutboxPort.class);
    EventosPagamento eventos = new EventosPagamento(new MensagemFactory(new ObjectMapper()), outbox);
    Orcamento orcamento = Dados.orcamento(UUID.randomUUID(), UUID.randomUUID(), Duration.ofHours(1));

    MensagemEnvelope capturado() {
        ArgumentCaptor<MensagemEnvelope> captor = ArgumentCaptor.forClass(MensagemEnvelope.class);
        verify(outbox).registrar(eq("billing-eventos"), captor.capture());
        return captor.getValue();
    }

    @Test
    void confirmadoCarregaIdsEStatusAprovado() {
        Pagamento p = Dados.pagamento(orcamento);
        p.aprovar("131234567891", Instant.now());

        eventos.confirmado(p, orcamento.getSagaId());

        MensagemEnvelope e = capturado();
        assertThat(e.type()).isEqualTo("PagamentoConfirmado");
        assertThat(e.sagaId()).isEqualTo(orcamento.getSagaId());
        assertThat(e.osId()).isEqualTo(orcamento.getOsId());
        assertThat(e.payload().path("mpPaymentId").asText()).isEqualTo("131234567891");
        assertThat(e.payload().path("status").asText()).isEqualTo("APROVADO");
        assertThat(e.payload().path("pagamentoId").asText()).isEqualTo(p.getId().toString());
    }

    @Test
    void recusadoCarregaOMotivoCortadoEm500() {
        Pagamento p = Dados.pagamento(orcamento);
        p.recusar("42", "m".repeat(700), Instant.now());

        eventos.recusado(p, orcamento.getSagaId());

        MensagemEnvelope e = capturado();
        assertThat(e.type()).isEqualTo("PagamentoRecusado");
        assertThat(e.payload().path("status").asText()).isEqualTo("RECUSADO");
        assertThat(e.payload().path("motivo").asText()).hasSize(500);
    }

    @Test
    void recusadoSemMotivoUsaUmTextoPadrao() {
        Pagamento p = Dados.pagamento(orcamento);
        p.recusar("42", null, Instant.now());

        eventos.recusado(p, orcamento.getSagaId());

        assertThat(capturado().payload().path("motivo").asText()).isEqualTo("recusado");
    }
}
