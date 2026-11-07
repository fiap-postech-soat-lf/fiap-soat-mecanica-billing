package br.com.fiap.soat.mecanica.billing.domain.webhook;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookEventoTest {

    static final Instant AGORA = Instant.parse("2026-11-08T15:00:00Z");

    WebhookEvento novo() {
        return WebhookEvento.receber("req", "123", "payment", "payment.updated", AGORA);
    }

    @Test
    void nascePendenteEElegivelNaHora() {
        WebhookEvento e = novo();
        assertThat(e.getStatus()).isEqualTo(StatusWebhook.PENDENTE);
        assertThat(e.getProximaTentativaEm()).isEqualTo(AGORA);
        assertThat(e.getTentativas()).isZero();
    }

    @Test
    void processadoEIgnoradoGuardamOStatusDoMpESaemDaFila() {
        WebhookEvento a = novo();
        a.processado("approved");
        assertThat(a.getStatus()).isEqualTo(StatusWebhook.PROCESSADO);
        assertThat(a.getStatusMp()).isEqualTo("approved");
        assertThat(a.getProximaTentativaEm()).isNull();

        WebhookEvento b = novo();
        b.ignorado("refunded");
        assertThat(b.getStatus()).isEqualTo(StatusWebhook.IGNORADO);
    }

    @Test
    void falhaAgendaBackoffDe2ElevadoATentativasSegundosEEsgotaNaQuinta() {
        WebhookEvento e = novo();
        long[] esperados = {2, 4, 8, 16};
        for (long espera : esperados) {
            assertThat(e.falhou("boom", AGORA, 5)).isFalse();
            assertThat(e.getStatus()).isEqualTo(StatusWebhook.PENDENTE);
            assertThat(e.getProximaTentativaEm()).isEqualTo(AGORA.plusSeconds(espera));
        }
        assertThat(e.falhou("boom", AGORA, 5)).isTrue();
        assertThat(e.getStatus()).isEqualTo(StatusWebhook.ERRO);
        assertThat(e.getTentativas()).isEqualTo(5);
        assertThat(e.getProximaTentativaEm()).isNull();
        assertThat(e.getUltimoErro()).isEqualTo("boom");
    }

    @Test
    void erroLongoECortado() {
        WebhookEvento e = novo();
        e.falhou("x".repeat(900), AGORA, 5);
        assertThat(e.getUltimoErro()).hasSize(500);
        e.falhou(null, AGORA, 5);
        assertThat(e.getUltimoErro()).isNull();
    }
}
