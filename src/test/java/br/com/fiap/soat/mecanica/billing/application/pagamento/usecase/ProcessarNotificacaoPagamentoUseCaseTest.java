package br.com.fiap.soat.mecanica.billing.application.pagamento.usecase;

import br.com.fiap.soat.mecanica.billing.application.pagamento.EventosPagamento;
import br.com.fiap.soat.mecanica.billing.application.pagamento.exception.MercadoPagoIndisponivelException;
import br.com.fiap.soat.mecanica.billing.application.pagamento.port.GatewayPagamentoPort;
import br.com.fiap.soat.mecanica.billing.application.pagamento.port.PagamentoMp;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.Pagamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.PagamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.StatusPagamento;
import br.com.fiap.soat.mecanica.billing.domain.webhook.StatusWebhook;
import br.com.fiap.soat.mecanica.billing.domain.webhook.WebhookEvento;
import br.com.fiap.soat.mecanica.billing.domain.webhook.WebhookEventoRepository;
import br.com.fiap.soat.mecanica.billing.support.Dados;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProcessarNotificacaoPagamentoUseCaseTest {

    static final Instant AGORA = Instant.parse("2026-11-08T15:00:00Z");

    WebhookEventoRepository eventos = mock(WebhookEventoRepository.class);
    PagamentoRepository pagamentos = mock(PagamentoRepository.class);
    OrcamentoRepository orcamentos = mock(OrcamentoRepository.class);
    GatewayPagamentoPort gateway = mock(GatewayPagamentoPort.class);
    EventosPagamento eventosPagamento = mock(EventosPagamento.class);
    TransactionTemplate transacao = mock(TransactionTemplate.class);
    SimpleMeterRegistry metricas = new SimpleMeterRegistry();
    ProcessarNotificacaoPagamentoUseCase useCase;

    Orcamento orcamento;
    Pagamento pagamento;
    WebhookEvento evento;

    @BeforeEach
    void preparar() {
        useCase = new ProcessarNotificacaoPagamentoUseCase(eventos, pagamentos, orcamentos, gateway, eventosPagamento,
                transacao, metricas, Clock.fixed(AGORA, ZoneOffset.UTC), 5);
        orcamento = Dados.orcamento(UUID.randomUUID(), UUID.randomUUID(), Duration.ofHours(1));
        pagamento = Dados.pagamento(orcamento);
        evento = WebhookEvento.receber("req", "9001", "payment", "payment.updated", AGORA);
        when(pagamentos.buscarPorId(pagamento.getId())).thenReturn(Optional.of(pagamento));
        when(pagamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));
        when(orcamentos.buscarPorId(orcamento.getId())).thenReturn(Optional.of(orcamento));
    }

    void mpResponde(String status, String detalhe) {
        when(gateway.consultarPagamento("9001"))
                .thenReturn(new PagamentoMp("9001", status, detalhe, pagamento.getId().toString()));
    }

    @Test
    void approvedNoPendenteConfirmaERegistraOParAplicado() {
        mpResponde("approved", "accredited");

        useCase.processar(evento, AGORA);

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(pagamento.getMpPaymentId()).isEqualTo("9001");
        verify(eventosPagamento).confirmado(pagamento, orcamento.getSagaId());
        verify(eventos).registrarStatusAplicado("9001", "approved", pagamento.getId(), AGORA);
        assertThat(evento.getStatus()).isEqualTo(StatusWebhook.PROCESSADO);
        assertThat(evento.getStatusMp()).isEqualTo("approved");
    }

    @Test
    void rejectedNoPendenteRecusaComOStatusDetail() {
        mpResponde("rejected", "cc_rejected_other_reason");

        useCase.processar(evento, AGORA);

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.RECUSADO);
        assertThat(pagamento.getMotivo()).isEqualTo("cc_rejected_other_reason");
        verify(eventosPagamento).recusado(pagamento, orcamento.getSagaId());
        verify(eventos).registrarStatusAplicado("9001", "rejected", pagamento.getId(), AGORA);
    }

    @Test
    void cancelledTambemRecusaEUsaOStatusQuandoNaoHaDetalhe() {
        mpResponde("cancelled", null);

        useCase.processar(evento, AGORA);

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.RECUSADO);
        assertThat(pagamento.getMotivo()).isEqualTo("cancelled");
    }

    @Test
    void vigenteRecusadoAprovadoDepoisEAceito() {
        pagamento.recusar("8000", "cc_rejected_other_reason", AGORA);
        mpResponde("approved", "accredited");

        useCase.processar(evento, AGORA);

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(pagamento.getMpPaymentId()).isEqualTo("9001");
        verify(eventosPagamento).confirmado(pagamento, orcamento.getSagaId());
    }

    @Test
    void substituidoAprovadoGeraMetricaESemEvento() {
        pagamento.recusar("8000", "x", AGORA);
        pagamento.substituir(AGORA);
        mpResponde("approved", "accredited");

        useCase.processar(evento, AGORA);

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.SUBSTITUIDO);
        verifyNoInteractions(eventosPagamento);
        verify(pagamentos, never()).salvar(any());
        assertThat(metricas.counter("pagamento.substituido_aprovado").count()).isEqualTo(1.0);
        verify(eventos).registrarStatusAplicado("9001", "approved", pagamento.getId(), AGORA);
        assertThat(evento.getStatus()).isEqualTo(StatusWebhook.PROCESSADO);
    }

    @Test
    void substituidoRecusadoNaoGeraMetrica() {
        pagamento.recusar("8000", "x", AGORA);
        pagamento.substituir(AGORA);
        mpResponde("rejected", "cc_rejected_other_reason");

        useCase.processar(evento, AGORA);

        verifyNoInteractions(eventosPagamento);
        assertThat(metricas.counter("pagamento.substituido_aprovado").count()).isZero();
    }

    @Test
    void pagamentoJaAprovadoNaoGeraNovoEvento() {
        pagamento.aprovar("7000", AGORA);
        mpResponde("rejected", "cc_rejected_other_reason");

        useCase.processar(evento, AGORA);

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.APROVADO);
        verifyNoInteractions(eventosPagamento);
        assertThat(evento.getStatus()).isEqualTo(StatusWebhook.PROCESSADO);
    }

    @Test
    void parJaAplicadoViraProcessadoSemEfeito() {
        mpResponde("approved", "accredited");
        when(eventos.statusJaAplicado("9001", "approved")).thenReturn(true);

        useCase.processar(evento, AGORA);

        assertThat(evento.getStatus()).isEqualTo(StatusWebhook.PROCESSADO);
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        verifyNoInteractions(eventosPagamento);
        verify(eventos, never()).registrarStatusAplicado(any(), any(), any(), any());
    }

    @Test
    void statusPendenteSemEfeitoENaoRegistraOPar() {
        for (String status : new String[]{"pending", "in_process", "authorized"}) {
            WebhookEvento e = WebhookEvento.receber("r", "9001", "payment", "payment.updated", AGORA);
            mpResponde(status, null);

            useCase.processar(e, AGORA);

            assertThat(e.getStatus()).isEqualTo(StatusWebhook.PROCESSADO);
        }
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        verify(eventos, never()).registrarStatusAplicado(any(), any(), any(), any());
        verifyNoInteractions(eventosPagamento);
    }

    @Test
    void statusSemMapeamentoEIgnorado() {
        mpResponde("refunded", null);

        useCase.processar(evento, AGORA);

        assertThat(evento.getStatus()).isEqualTo(StatusWebhook.IGNORADO);
        verifyNoInteractions(eventosPagamento);
    }

    @Test
    void externalReferenceDesconhecidaOuInvalidaEIgnorada() {
        for (String referencia : new String[]{UUID.randomUUID().toString(), "abc-nao-uuid", "", null}) {
            WebhookEvento e = WebhookEvento.receber("r", "9001", "payment", "payment.updated", AGORA);
            when(gateway.consultarPagamento("9001")).thenReturn(new PagamentoMp("9001", "approved", null, referencia));

            useCase.processar(e, AGORA);

            assertThat(e.getStatus()).as("referencia %s", referencia).isEqualTo(StatusWebhook.IGNORADO);
        }
        verifyNoInteractions(eventosPagamento);
    }

    @Test
    void mercadoPagoForaAgendaNovaTentativaSemGravarEfeito() {
        when(gateway.consultarPagamento("9001")).thenThrow(new MercadoPagoIndisponivelException("fora", null));

        useCase.processar(evento, AGORA);

        assertThat(evento.getStatus()).isEqualTo(StatusWebhook.PENDENTE);
        assertThat(evento.getTentativas()).isEqualTo(1);
        assertThat(evento.getProximaTentativaEm()).isEqualTo(AGORA.plusSeconds(2));
        verify(eventos).salvar(evento);
        verifyNoInteractions(eventosPagamento);
        verify(pagamentos, never()).salvar(any());
    }

    @Test
    void naQuintaFalhaOEventoViraErro() {
        when(gateway.consultarPagamento("9001")).thenThrow(new MercadoPagoIndisponivelException("fora", null));

        for (int i = 0; i < 5; i++) {
            useCase.processar(evento, AGORA);
        }

        assertThat(evento.getStatus()).isEqualTo(StatusWebhook.ERRO);
        assertThat(evento.getTentativas()).isEqualTo(5);
    }

    @Test
    void orcamentoSumidoFazAExcecaoPropagarParaDesfazerATransacao() {
        mpResponde("approved", "accredited");
        when(orcamentos.buscarPorId(orcamento.getId())).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> useCase.processar(evento, AGORA))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void processarProximoSemEventoElegivelDevolveFalse() {
        when(eventos.travarProximoPendente(AGORA)).thenReturn(Optional.empty());
        assertThat(useCase.processarProximo()).isFalse();
    }

    @Test
    void processarProximoTrataOEventoTravado() {
        when(eventos.travarProximoPendente(AGORA)).thenReturn(Optional.of(evento));
        mpResponde("pending", null);
        assertThat(useCase.processarProximo()).isTrue();
        assertThat(evento.getStatus()).isEqualTo(StatusWebhook.PROCESSADO);
    }

    @Test
    void executarProcessaAteOLoteEParaQuandoNaoHaMais() {
        when(transacao.execute(any())).thenReturn(true, true, true, false);
        assertThat(useCase.executar()).isEqualTo(3);

        org.mockito.Mockito.reset(transacao);
        when(transacao.execute(any())).thenReturn(true);
        assertThat(useCase.executar()).isEqualTo(ProcessarNotificacaoPagamentoUseCase.LOTE);
    }
}
