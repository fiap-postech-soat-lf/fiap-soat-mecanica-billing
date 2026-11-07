package br.com.fiap.soat.mecanica.billing.application.pagamento;

import br.com.fiap.soat.mecanica.billing.application.pagamento.port.GatewayPagamentoPort;
import br.com.fiap.soat.mecanica.billing.application.pagamento.port.Preferencia;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.Pagamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.StatusPagamento;
import br.com.fiap.soat.mecanica.billing.support.Dados;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmissorDeCobrancaTest {

    static final Instant AGORA = Instant.parse("2026-11-07T12:00:00Z");

    GatewayPagamentoPort gateway = mock(GatewayPagamentoPort.class);
    Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);
    Orcamento orcamento = Dados.orcamento(UUID.fromString("7d1c2a4e-3b5f-4c6d-8e9f-0a1b2c3d4e5f"), UUID.randomUUID(),
            Duration.ofHours(1));

    @Test
    void cobraOValorDoOrcamentoComOTituloDaOsEExternalReferenceDoPagamento() {
        when(gateway.criarPreferencia(any(), any(), any())).thenReturn(new Preferencia("pref", "https://init", "https://sandbox"));

        Pagamento pagamento = new EmissorDeCobranca(gateway, clock, "init_point").emitir(orcamento);

        verify(gateway).criarPreferencia(eq(pagamento.getId()), eq("Ordem de serviço 7d1c2a4e"),
                eq(new BigDecimal("159.80")));
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(pagamento.getValor()).isEqualByComparingTo("159.80");
        assertThat(pagamento.getClienteId()).isEqualTo(orcamento.getClienteId());
        assertThat(pagamento.getOrcamentoId()).isEqualTo(orcamento.getId());
        assertThat(pagamento.getCriadoEm()).isEqualTo(AGORA);
        assertThat(pagamento.getMpPreferenceId()).isEqualTo("pref");
        assertThat(pagamento.getLink()).isEqualTo("https://init");
    }

    @Test
    void sandboxInitPointQuandoConfigurado() {
        when(gateway.criarPreferencia(any(), any(), any())).thenReturn(new Preferencia("pref", "https://init", "https://sandbox"));

        assertThat(new EmissorDeCobranca(gateway, clock, "sandbox_init_point").emitir(orcamento).getLink())
                .isEqualTo("https://sandbox");
    }

    @Test
    void semSandboxNaRespostaUsaOInitPoint() {
        when(gateway.criarPreferencia(any(), any(), any())).thenReturn(new Preferencia("pref", "https://init", null));

        assertThat(new EmissorDeCobranca(gateway, clock, "sandbox_init_point").emitir(orcamento).getLink())
                .isEqualTo("https://init");
    }

    @Test
    void falhaDoGatewayPropaga() {
        when(gateway.criarPreferencia(any(), any(), any())).thenThrow(new IllegalStateException("mp"));

        assertThatThrownBy(() -> new EmissorDeCobranca(gateway, clock, "init_point").emitir(orcamento))
                .isInstanceOf(IllegalStateException.class);
    }
}
