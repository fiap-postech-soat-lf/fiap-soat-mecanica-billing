package br.com.fiap.soat.mecanica.billing.domain.orcamento;

import br.com.fiap.soat.mecanica.billing.domain.exception.RegraNegocioException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrcamentoTest {

    static final Instant AGORA = Instant.parse("2026-11-07T12:00:00Z");
    static final Duration TTL = Duration.ofHours(24);
    static final UUID OS = UUID.randomUUID();
    static final UUID SAGA = UUID.randomUUID();
    static final UUID CLIENTE = UUID.randomUUID();

    static List<ItemOrcamento> itens() {
        return List.of(
                ItemOrcamento.servico(UUID.randomUUID(), "Troca de oleo", new BigDecimal("80.00")),
                ItemOrcamento.peca(UUID.randomUUID(), "Filtro de oleo", 2, new BigDecimal("39.90")));
    }

    static Orcamento gerado() {
        return Orcamento.gerar(OS, SAGA, CLIENTE, itens(), new BigDecimal("159.80"), TTL, AGORA);
    }

    @Test
    void gerarRecalculaOTotalEDefineAExpiracao() {
        Orcamento o = gerado();

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.GERADO);
        assertThat(o.getValorTotal()).isEqualByComparingTo("159.80");
        assertThat(o.getExpiraEm()).isEqualTo(AGORA.plus(TTL));
        assertThat(o.getCriadoEm()).isEqualTo(AGORA);
        assertThat(o.getItens()).hasSize(2);
        assertThat(o.getItens().get(1).valorTotal()).isEqualByComparingTo("79.80");
        assertThat(o.getVersao()).isNull();
    }

    @Test
    void gerarComTotalDivergenteFalha() {
        assertThatThrownBy(() -> Orcamento.gerar(OS, SAGA, CLIENTE, itens(), new BigDecimal("159.79"), TTL, AGORA))
                .isInstanceOf(OrcamentoInconsistenteException.class).hasMessageContaining("divergente");
    }

    @Test
    void gerarSemTotalInformadoFalha() {
        assertThatThrownBy(() -> Orcamento.gerar(OS, SAGA, CLIENTE, itens(), null, TTL, AGORA))
                .isInstanceOf(OrcamentoInconsistenteException.class);
    }

    @Test
    void gerarComItemSemValorFalha() {
        List<ItemOrcamento> semValor = List.of(ItemOrcamento.servico(UUID.randomUUID(), "Alinhamento", null));
        assertThatThrownBy(() -> Orcamento.gerar(OS, SAGA, CLIENTE, semValor, BigDecimal.ZERO, TTL, AGORA))
                .isInstanceOf(OrcamentoInconsistenteException.class).hasMessageContaining("Alinhamento");
    }

    @Test
    void gerarComItemDeValorNegativoOuQuantidadeInvalidaFalha() {
        List<ItemOrcamento> negativo = List.of(ItemOrcamento.servico(UUID.randomUUID(), "A", new BigDecimal("-1")));
        List<ItemOrcamento> semQuantidade = List.of(ItemOrcamento.peca(UUID.randomUUID(), "P", 0, BigDecimal.TEN));

        assertThatThrownBy(() -> Orcamento.gerar(OS, SAGA, CLIENTE, negativo, BigDecimal.ZERO, TTL, AGORA))
                .isInstanceOf(OrcamentoInconsistenteException.class);
        assertThatThrownBy(() -> Orcamento.gerar(OS, SAGA, CLIENTE, semQuantidade, BigDecimal.ZERO, TTL, AGORA))
                .isInstanceOf(OrcamentoInconsistenteException.class);
    }

    @Test
    void gerarSemItensFalha() {
        assertThatThrownBy(() -> Orcamento.gerar(OS, SAGA, CLIENTE, List.of(), BigDecimal.ZERO, TTL, AGORA))
                .isInstanceOf(OrcamentoInconsistenteException.class);
        assertThatThrownBy(() -> Orcamento.gerar(OS, SAGA, CLIENTE, null, BigDecimal.ZERO, TTL, AGORA))
                .isInstanceOf(OrcamentoInconsistenteException.class);
    }

    @Test
    void totalUsaDuasCasasComHalfEven() {
        List<ItemOrcamento> itens = List.of(ItemOrcamento.peca(UUID.randomUUID(), "P", 3, new BigDecimal("0.835")));
        // 3 x 0.835 = 2.505 -> HALF_EVEN = 2.50
        Orcamento o = Orcamento.gerar(OS, SAGA, CLIENTE, itens, new BigDecimal("2.50"), TTL, AGORA);
        assertThat(o.getValorTotal()).isEqualByComparingTo("2.50");
    }

    @Test
    void aprovarMudaParaAprovadoERegistraQuem() {
        Orcamento o = gerado();
        UUID sub = UUID.randomUUID();

        o.aprovar(sub, AGORA.plusSeconds(60));

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.APROVADO);
        assertThat(o.getDecididoPor()).isEqualTo(sub);
        assertThat(o.getDecididoEm()).isEqualTo(AGORA.plusSeconds(60));
    }

    @Test
    void aprovarForaDeGeradoOuVencidoFalha() {
        Orcamento aprovado = gerado();
        aprovado.aprovar(CLIENTE, AGORA);
        Orcamento vencido = gerado();

        assertThatThrownBy(() -> aprovado.aprovar(CLIENTE, AGORA)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> vencido.aprovar(CLIENTE, AGORA.plus(TTL)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("expirado");
        assertThat(vencido.getStatus()).isEqualTo(StatusOrcamento.GERADO);
    }

    @Test
    void recusarGuardaOMotivo() {
        Orcamento o = gerado();

        o.recusar(CLIENTE, "Muito caro", AGORA);

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.RECUSADO);
        assertThat(o.getMotivo()).isEqualTo("Muito caro");
        assertThat(o.getDecididoPor()).isEqualTo(CLIENTE);
    }

    @Test
    void recusarForaDeGeradoOuVencidoFalha() {
        Orcamento recusado = gerado();
        recusado.recusar(CLIENTE, null, AGORA);

        assertThatThrownBy(() -> recusado.recusar(CLIENTE, null, AGORA)).isInstanceOf(RegraNegocioException.class);
        assertThatThrownBy(() -> gerado().recusar(CLIENTE, null, AGORA.plus(TTL.plusSeconds(1))))
                .isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void expirarSoDepoisDoPrazo() {
        Orcamento o = gerado();

        assertThatThrownBy(() -> o.expirar(AGORA.plus(TTL).minusSeconds(1)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("dentro do prazo");
        o.expirar(AGORA.plus(TTL));

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.EXPIRADO);
        assertThat(o.getDecididoEm()).isEqualTo(AGORA.plus(TTL));
    }

    @Test
    void expirarForaDeGeradoFalha() {
        Orcamento o = gerado();
        o.aprovar(CLIENTE, AGORA);

        assertThatThrownBy(() -> o.expirar(AGORA.plus(TTL))).isInstanceOf(RegraNegocioException.class);
    }

    @Test
    void cancelarGeradoEAprovado() {
        Orcamento gerado = gerado();
        Orcamento aprovado = gerado();
        aprovado.aprovar(CLIENTE, AGORA);

        assertThat(gerado.cancelar("SUBSTITUIDO")).isTrue();
        assertThat(aprovado.cancelar("Cancelado pelo atendente")).isTrue();

        assertThat(gerado.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(gerado.getMotivo()).isEqualTo("SUBSTITUIDO");
        assertThat(aprovado.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
    }

    @Test
    void cancelarEmOutroEstadoNaoFazNada() {
        Orcamento o = gerado();
        o.recusar(CLIENTE, null, AGORA);

        assertThat(o.cancelar("tarde demais")).isFalse();
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.RECUSADO);
        assertThat(o.getMotivo()).isNull();
    }

    @Test
    void donoEVencimento() {
        Orcamento o = gerado();

        assertThat(o.isDono(CLIENTE)).isTrue();
        assertThat(o.isDono(UUID.randomUUID())).isFalse();
        assertThat(o.isVencido(AGORA)).isFalse();
        assertThat(o.isVencido(AGORA.plus(TTL))).isTrue();
    }

    @Test
    void vivoSoParaGeradoEAprovado() {
        assertThat(StatusOrcamento.GERADO.isVivo()).isTrue();
        assertThat(StatusOrcamento.APROVADO.isVivo()).isTrue();
        assertThat(StatusOrcamento.RECUSADO.isVivo()).isFalse();
        assertThat(StatusOrcamento.EXPIRADO.isVivo()).isFalse();
        assertThat(StatusOrcamento.CANCELADO.isVivo()).isFalse();
    }
}
