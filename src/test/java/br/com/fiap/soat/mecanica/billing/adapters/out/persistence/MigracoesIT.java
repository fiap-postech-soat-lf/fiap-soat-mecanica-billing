package br.com.fiap.soat.mecanica.billing.adapters.out.persistence;

import br.com.fiap.soat.mecanica.billing.domain.exception.ConflitoConcorrenciaException;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.StatusOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.Pagamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.PagamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.StatusPagamento;
import br.com.fiap.soat.mecanica.billing.support.Dados;
import br.com.fiap.soat.mecanica.billing.support.IntegracaoTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O Flyway aplica V1-V5, os indices parciais valem e os adapters gravam e leem o dominio por inteiro. */
class MigracoesIT extends IntegracaoTest {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    OrcamentoRepository orcamentos;
    @Autowired
    PagamentoRepository pagamentos;

    @Test
    void flywayAplicouV1AteV5() {
        List<String> versoes = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);
        assertThat(versoes).containsExactly("1", "2", "3", "4", "5");
        assertThat(jdbc.queryForList("select table_name from information_schema.tables where table_schema = 'public'",
                String.class)).contains("orcamentos", "itens_orcamento", "pagamentos", "webhook_eventos",
                "mp_status_aplicado", "comando_atendido", "outbox", "mensagem_processada");
    }

    @Test
    void segundoOrcamentoVivoDaMesmaOsEhRecusado() {
        UUID os = UUID.randomUUID();
        orcamentos.salvar(Dados.orcamento(os, UUID.randomUUID(), Duration.ofHours(1)));

        assertThatThrownBy(() -> orcamentos.salvar(Dados.orcamento(os, UUID.randomUUID(), Duration.ofHours(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void orcamentoNaoVivoLiberaAOsParaOutro() {
        UUID os = UUID.randomUUID();
        Orcamento primeiro = Dados.orcamento(os, UUID.randomUUID(), Duration.ofHours(1));
        primeiro.cancelar("SUBSTITUIDO");
        orcamentos.salvar(primeiro);

        Orcamento segundo = orcamentos.salvar(Dados.orcamento(os, UUID.randomUUID(), Duration.ofHours(1)));

        assertThat(orcamentos.buscarVivoPorOs(os)).get().extracting(Orcamento::getId).isEqualTo(segundo.getId());
        assertThat(orcamentos.buscarMaisRecentePorOs(os)).get().extracting(Orcamento::getId)
                .isEqualTo(segundo.getId());
    }

    @Test
    void semVivoOMaisRecenteEhDevolvido() {
        UUID os = UUID.randomUUID();
        Orcamento o = Dados.orcamento(os, UUID.randomUUID(), Duration.ofHours(1));
        o.recusar(o.getClienteId(), "Caro", Instant.now());
        orcamentos.salvar(o);

        assertThat(orcamentos.buscarVivoPorOs(os)).isEmpty();
        assertThat(orcamentos.buscarMaisRecentePorOs(os)).get().extracting(Orcamento::getStatus)
                .isEqualTo(StatusOrcamento.RECUSADO);
    }

    @Test
    void orcamentoVoltaDoBancoComItensEmOrdemEValores() {
        Orcamento original = Dados.orcamento(UUID.randomUUID(), UUID.randomUUID(), Duration.ofHours(1));

        Orcamento salvo = orcamentos.salvar(original);
        Orcamento lido = orcamentos.buscarPorId(original.getId()).orElseThrow();

        assertThat(lido.getVersao()).isEqualTo(salvo.getVersao()).isNotNull();
        assertThat(lido.getItens()).containsExactlyElementsOf(original.getItens());
        assertThat(lido.getValorTotal()).isEqualByComparingTo("159.80");
        assertThat(lido.getClienteId()).isEqualTo(original.getClienteId());
        assertThat(lido.getSagaId()).isEqualTo(original.getSagaId());
        assertThat(lido.getStatus()).isEqualTo(StatusOrcamento.GERADO);
    }

    @Test
    void versaoDesatualizadaViraConflito() {
        Orcamento criado = orcamentos.salvar(Dados.orcamento(UUID.randomUUID(), UUID.randomUUID(), Duration.ofHours(1)));
        Orcamento copiaA = orcamentos.buscarPorId(criado.getId()).orElseThrow();
        Orcamento copiaB = orcamentos.buscarPorId(criado.getId()).orElseThrow();
        copiaA.aprovar(criado.getClienteId(), Instant.now());
        copiaB.recusar(criado.getClienteId(), null, Instant.now());

        orcamentos.salvar(copiaA);

        assertThatThrownBy(() -> orcamentos.salvar(copiaB)).isInstanceOf(ConflitoConcorrenciaException.class);
        assertThat(orcamentos.buscarPorId(criado.getId()).orElseThrow().getStatus()).isEqualTo(StatusOrcamento.APROVADO);
    }

    @Test
    void segundoPagamentoVigenteDaMesmaOsEhRecusado() {
        Orcamento o = orcamentos.salvar(Dados.orcamento(UUID.randomUUID(), UUID.randomUUID(), Duration.ofHours(1)));
        pagamentos.salvar(Dados.pagamento(o));

        assertThatThrownBy(() -> pagamentos.salvar(Dados.pagamento(o)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void pagamentoSubstituidoLiberaOVigente() {
        Orcamento o = orcamentos.salvar(Dados.orcamento(UUID.randomUUID(), UUID.randomUUID(), Duration.ofHours(1)));
        Pagamento primeiro = Dados.pagamento(o);
        primeiro.registrarPreferencia("pref-1", "https://mp/1");
        primeiro.recusar("1001", "cc_rejected_other_reason", Instant.now());
        Pagamento recusado = pagamentos.salvar(primeiro);
        recusado.substituir(Instant.now());
        Pagamento substituido = pagamentos.salvar(recusado);
        Pagamento segundo = pagamentos.salvar(Dados.pagamento(o));

        assertThat(substituido.getStatus()).isEqualTo(StatusPagamento.SUBSTITUIDO);
        assertThat(pagamentos.buscarVigentePorOs(o.getOsId())).get().extracting(Pagamento::getId)
                .isEqualTo(segundo.getId());
        assertThat(pagamentos.listarPorOs(o.getOsId())).extracting(Pagamento::getId)
                .containsExactly(segundo.getId(), primeiro.getId());
        assertThat(pagamentos.buscarPorId(primeiro.getId())).get().satisfies(p -> {
            assertThat(p.getLink()).isEqualTo("https://mp/1");
            assertThat(p.getMotivo()).isEqualTo("cc_rejected_other_reason");
            assertThat(p.getMpPaymentId()).isEqualTo("1001");
        });
    }

    @Test
    void pagamentoDesatualizadoViraConflito() {
        Orcamento o = orcamentos.salvar(Dados.orcamento(UUID.randomUUID(), UUID.randomUUID(), Duration.ofHours(1)));
        Pagamento criado = pagamentos.salvar(Dados.pagamento(o));
        Pagamento a = pagamentos.buscarPorId(criado.getId()).orElseThrow();
        Pagamento b = pagamentos.buscarPorId(criado.getId()).orElseThrow();
        a.aprovar("1", Instant.now());
        b.recusar("2", "x", Instant.now());

        pagamentos.salvar(a);

        assertThatThrownBy(() -> pagamentos.salvar(b)).isInstanceOf(ConflitoConcorrenciaException.class);
    }

    @Test
    void checkDeStatusRecusaValorInvalido() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into orcamentos (id, os_id, saga_id, cliente_id, status, valor_total, expira_em, criado_em)
                values (?, ?, ?, ?, 'INVALIDO', 1, now(), now())""",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void chavesDeIdempotenciaDoWebhookEDoComandoSaoUnicas() {
        jdbc.update("insert into mp_status_aplicado values ('9001', 'approved', null, now())");
        UUID os = UUID.randomUUID();
        jdbc.update("insert into comando_atendido values (?, 'GerarOrcamento', '[]'::jsonb, null, now())", os);

        assertThatThrownBy(() -> jdbc.update("insert into mp_status_aplicado values ('9001', 'approved', null, now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into comando_atendido values (?, 'GerarOrcamento', '[]'::jsonb, null, now())", os))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("insert into mp_status_aplicado values ('9001', 'rejected', null, now())");
    }
}
