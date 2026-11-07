package br.com.fiap.soat.mecanica.billing.domain.pagamento;

import br.com.fiap.soat.mecanica.billing.domain.exception.RegraNegocioException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PagamentoTest {

    static final Instant AGORA = Instant.parse("2026-11-07T12:00:00Z");
    static final UUID CLIENTE = UUID.randomUUID();

    static Pagamento pendente() {
        return Pagamento.criar(UUID.randomUUID(), UUID.randomUUID(), CLIENTE, new BigDecimal("159.80"), AGORA);
    }

    static Pagamento recusado() {
        Pagamento p = pendente();
        p.recusar("1001", "cc_rejected_other_reason", AGORA);
        return p;
    }

    @Test
    void criarNasceVigentePendente() {
        Pagamento p = pendente();

        assertThat(p.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(p.isVigente()).isTrue();
        assertThat(p.aceitaResultado()).isTrue();
        assertThat(p.getVersao()).isNull();
        assertThat(p.isDono(CLIENTE)).isTrue();
        assertThat(p.isDono(UUID.randomUUID())).isFalse();
    }

    @Test
    void registrarPreferenciaGuardaIdELink() {
        Pagamento p = pendente();

        p.registrarPreferencia("pref-1", "https://mp/checkout");

        assertThat(p.getMpPreferenceId()).isEqualTo("pref-1");
        assertThat(p.getLink()).isEqualTo("https://mp/checkout");
    }

    @Test
    void registrarPreferenciaForaDePendenteFalha() {
        Pagamento p = recusado();
        assertThatThrownBy(() -> p.registrarPreferencia("x", "y")).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void aprovarDePendenteERegistraOPagamentoDoMercadoPago() {
        Pagamento p = pendente();

        p.aprovar("1001", AGORA.plusSeconds(5));

        assertThat(p.getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(p.getMpPaymentId()).isEqualTo("1001");
        assertThat(p.getAtualizadoEm()).isEqualTo(AGORA.plusSeconds(5));
        assertThat(p.isVigente()).isTrue();
    }

    @Test
    void aprovarDeRecusadoLimpaOMotivo() {
        Pagamento p = recusado();

        p.aprovar("1002", AGORA);

        assertThat(p.getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(p.getMotivo()).isNull();
        assertThat(p.getMpPaymentId()).isEqualTo("1002");
    }

    @Test
    void aprovarForaDePendenteOuRecusadoFalha() {
        Pagamento aprovado = pendente();
        aprovado.aprovar("1", AGORA);
        Pagamento substituido = recusado();
        substituido.substituir(AGORA);

        assertThatThrownBy(() -> aprovado.aprovar("2", AGORA)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> substituido.aprovar("2", AGORA)).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void recusarGuardaOStatusDetail() {
        Pagamento p = pendente();

        p.recusar("1001", "cc_rejected_other_reason", AGORA);

        assertThat(p.getStatus()).isEqualTo(StatusPagamento.RECUSADO);
        assertThat(p.getMotivo()).isEqualTo("cc_rejected_other_reason");
        assertThat(p.getMpPaymentId()).isEqualTo("1001");
        assertThat(p.aceitaResultado()).isTrue();
    }

    @Test
    void recusarForaDePendenteOuRecusadoFalha() {
        Pagamento aprovado = pendente();
        aprovado.aprovar("1", AGORA);

        assertThatThrownBy(() -> aprovado.recusar("2", "x", AGORA)).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void substituirSoORecusado() {
        Pagamento p = recusado();

        p.substituir(AGORA.plusSeconds(1));

        assertThat(p.getStatus()).isEqualTo(StatusPagamento.SUBSTITUIDO);
        assertThat(p.isVigente()).isFalse();
        assertThat(p.aceitaResultado()).isFalse();
    }

    @Test
    void substituirForaDeRecusadoFalha() {
        Pagamento pendente = pendente();
        assertThatThrownBy(() -> pendente.substituir(AGORA)).isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("RECUSADO");
    }
}
