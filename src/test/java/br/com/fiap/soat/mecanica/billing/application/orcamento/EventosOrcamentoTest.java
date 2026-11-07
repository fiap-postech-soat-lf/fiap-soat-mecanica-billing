package br.com.fiap.soat.mecanica.billing.application.orcamento;

import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemFactory;
import br.com.fiap.soat.mecanica.billing.application.mensageria.OutboxPort;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.ItemOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EventosOrcamentoTest {

    OutboxPort outbox = mock(OutboxPort.class);
    EventosOrcamento eventos = new EventosOrcamento(new MensagemFactory(new ObjectMapper()), outbox);
    Orcamento orcamento = Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            List.of(ItemOrcamento.servico(UUID.randomUUID(), "Revisao", new BigDecimal("10.00"))),
            new BigDecimal("10.00"), Duration.ofHours(1), Instant.parse("2026-11-07T12:00:00Z"));

    private MensagemEnvelope publicado() {
        ArgumentCaptor<MensagemEnvelope> captor = ArgumentCaptor.forClass(MensagemEnvelope.class);
        verify(outbox).registrar(eq("billing-eventos"), captor.capture());
        return captor.getValue();
    }

    @Test
    void aprovadoUsaOSagaIdEOOsIdDoOrcamento() {
        UUID quem = UUID.randomUUID();

        eventos.aprovado(orcamento, quem);

        MensagemEnvelope e = publicado();
        assertThat(e.type()).isEqualTo("OrcamentoAprovado");
        assertThat(e.sagaId()).isEqualTo(orcamento.getSagaId());
        assertThat(e.osId()).isEqualTo(orcamento.getOsId());
        assertThat(e.payload().path("aprovadoPor").asText()).isEqualTo(quem.toString());
        assertThat(e.payload().path("orcamentoId").asText()).isEqualTo(orcamento.getId().toString());
    }

    @Test
    void recusadoEExpiradoTemOsCamposDoContrato() {
        UUID quem = UUID.randomUUID();
        eventos.recusado(orcamento, quem);
        eventos.expirado(orcamento);

        ArgumentCaptor<MensagemEnvelope> captor = ArgumentCaptor.forClass(MensagemEnvelope.class);
        verify(outbox, org.mockito.Mockito.times(2)).registrar(eq("billing-eventos"), captor.capture());
        assertThat(captor.getAllValues()).extracting(MensagemEnvelope::type)
                .containsExactly("OrcamentoRecusado", "OrcamentoExpirado");
        assertThat(captor.getAllValues().get(0).payload().path("recusadoPor").asText()).isEqualTo(quem.toString());
        assertThat(captor.getAllValues().get(1).payload().path("osId").asText())
                .isEqualTo(orcamento.getOsId().toString());
    }
}
