package br.com.fiap.soat.mecanica.billing.application.orcamento.usecase;

import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.StatusOrcamento;
import br.com.fiap.soat.mecanica.billing.support.Dados;
import br.com.fiap.soat.mecanica.billing.support.IntegracaoTest;
import br.com.fiap.soat.mecanica.billing.support.JwtTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/** Expiracao pelo Billing (F6-f): scheduler a cada 1 s e TTL de 2 s nos testes. */
class ExpiracaoOrcamentoIT extends IntegracaoTest {

    @Autowired
    OrcamentoRepository orcamentos;
    @Autowired
    ExpirarOrcamentosUseCase expirar;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    MockMvc mockMvc;

    private int eventos(UUID osId, String tipo) {
        return jdbc.queryForObject("select count(*) from outbox where tipo = ? and envelope->>'osId' = ?",
                Integer.class, tipo, osId.toString());
    }

    @Test
    void orcamentoVencidoViraExpiradoEEmiteOrcamentoExpiradoUmaVezSo() throws Exception {
        UUID osId = UUID.randomUUID();
        Orcamento o = orcamentos.salvar(Dados.orcamento(osId, UUID.randomUUID(), Duration.ofSeconds(2)));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(orcamentos.buscarPorId(o.getId()).orElseThrow().getStatus())
                        .isEqualTo(StatusOrcamento.EXPIRADO));

        assertThat(eventos(osId, "OrcamentoExpirado")).isEqualTo(1);
        Thread.sleep(2500); // mais algumas rodadas do scheduler: nenhum evento a mais
        assertThat(eventos(osId, "OrcamentoExpirado")).isEqualTo(1);
        String envelope = jdbc.queryForObject("select envelope::text from outbox where tipo = 'OrcamentoExpirado' "
                + "and envelope->>'osId' = ?", String.class, osId.toString());
        assertThat(br.com.fiap.soat.mecanica.billing.support.ContratosDeTeste.validar(envelope)).isEmpty();
        assertThat(envelope).contains(o.getSagaId().toString());
    }

    @Test
    void doisCasosDeUsoEmParaleloGeramUmEventoSo() throws Exception {
        UUID osId = UUID.randomUUID();
        Orcamento o = orcamentos.salvar(Dados.orcamento(osId, UUID.randomUUID(), Duration.ofMillis(1)));
        Thread.sleep(20);
        CountDownLatch largada = new CountDownLatch(1);
        AtomicInteger total = new AtomicInteger();
        CompletableFuture<?>[] execucoes = new CompletableFuture<?>[2];
        for (int i = 0; i < execucoes.length; i++) {
            execucoes[i] = CompletableFuture.runAsync(() -> {
                try {
                    largada.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                total.addAndGet(expirar.executar());
            });
        }
        largada.countDown();
        CompletableFuture.allOf(execucoes).join();

        // alguma das duas (ou o scheduler) expirou; nenhuma expirou duas vezes
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(orcamentos.buscarPorId(o.getId()).orElseThrow().getStatus())
                        .isEqualTo(StatusOrcamento.EXPIRADO));
        assertThat(eventos(osId, "OrcamentoExpirado")).isEqualTo(1);
    }

    @Test
    void aprovarEExpirarAoMesmoTempoUmVenceEOOutroFicaSemEfeito() throws Exception {
        UUID cliente = UUID.randomUUID();
        Orcamento o = orcamentos.salvar(Dados.orcamento(UUID.randomUUID(), cliente, Duration.ofMillis(400)));
        UUID osId = o.getOsId();
        CompletableFuture<Integer> aprovacao = CompletableFuture.supplyAsync(() -> {
            try {
                long ate = o.getExpiraEm().toEpochMilli() - 30;
                while (System.currentTimeMillis() < ate) {
                    Thread.onSpinWait();
                }
                return mockMvc.perform(patch("/orcamentos/" + o.getId() + "/aprovar")
                                .header("Authorization", "Bearer " + JwtTestTokens.cliente(cliente)))
                        .andReturn().getResponse().getStatus();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        CompletableFuture<Void> expiracao = CompletableFuture.runAsync(() -> {
            long ate = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < ate
                    && orcamentos.buscarPorId(o.getId()).orElseThrow().getStatus() == StatusOrcamento.GERADO) {
                expirar.executar();
            }
        });

        int statusHttp = aprovacao.get();
        expiracao.get();

        StatusOrcamento finalStatus = orcamentos.buscarPorId(o.getId()).orElseThrow().getStatus();
        if (finalStatus == StatusOrcamento.APROVADO) {
            assertThat(statusHttp).isEqualTo(200);
            assertThat(eventos(osId, "OrcamentoAprovado")).isEqualTo(1);
            assertThat(eventos(osId, "OrcamentoExpirado")).isZero();
        } else {
            assertThat(finalStatus).isEqualTo(StatusOrcamento.EXPIRADO);
            assertThat(statusHttp).isIn(409, 422);
            assertThat(eventos(osId, "OrcamentoAprovado")).isZero();
            assertThat(eventos(osId, "OrcamentoExpirado")).isEqualTo(1);
        }
    }
}
