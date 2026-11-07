package br.com.fiap.soat.mecanica.billing.application.pagamento.usecase;

import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemFactory;
import br.com.fiap.soat.mecanica.billing.application.mensageria.OutboxPort;
import br.com.fiap.soat.mecanica.billing.application.pagamento.EmissorDeCobranca;
import br.com.fiap.soat.mecanica.billing.application.pagamento.exception.MercadoPagoIndisponivelException;
import br.com.fiap.soat.mecanica.billing.application.saga.RespostasSaga;
import br.com.fiap.soat.mecanica.billing.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.billing.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.Pagamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.PagamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.StatusPagamento;
import br.com.fiap.soat.mecanica.billing.support.Dados;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NovaCobrancaUseCaseTest {

    static final Instant AGORA = Instant.parse("2026-11-07T12:00:00Z");

    PagamentoRepository pagamentos = mock(PagamentoRepository.class);
    OrcamentoRepository orcamentos = mock(OrcamentoRepository.class);
    EmissorDeCobranca emissor = mock(EmissorDeCobranca.class);
    OutboxPort outbox = mock(OutboxPort.class);
    ObjectMapper mapper = new ObjectMapper();
    NovaCobrancaUseCase useCase;

    UUID os = UUID.randomUUID();
    Orcamento orcamento = Dados.orcamento(os, UUID.randomUUID(), Duration.ofHours(1));

    @BeforeEach
    void preparar() {
        RespostasSaga respostas = new RespostasSaga(new MensagemFactory(mapper), mapper);
        useCase = new NovaCobrancaUseCase(pagamentos, orcamentos, emissor, respostas, outbox,
                Clock.fixed(AGORA, ZoneOffset.UTC));
        when(orcamentos.buscarPorId(orcamento.getId())).thenReturn(Optional.of(orcamento));
        when(pagamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Pagamento vigente(boolean recusado) {
        Pagamento p = Dados.pagamento(orcamento);
        p.registrarPreferencia("pref-antiga", "https://mp/antigo");
        if (recusado) {
            p.recusar("99", "cc_rejected_other_reason", AGORA);
        }
        when(pagamentos.buscarVigentePorOs(os)).thenReturn(Optional.of(p));
        return p;
    }

    private Pagamento novoPagamento() {
        Pagamento novo = Dados.pagamento(orcamento);
        novo.registrarPreferencia("pref-nova", "https://mp/novo");
        when(emissor.emitir(orcamento)).thenReturn(novo);
        return novo;
    }

    @Test
    void recusadoViraSubstituidoENasceUmNovoPendenteComCobrancaCriadaDoSagaDoOrcamento() {
        Pagamento antigo = vigente(true);
        Pagamento novo = novoPagamento();

        Pagamento resultado = useCase.executar(os);

        assertThat(resultado).isSameAs(novo);
        assertThat(antigo.getStatus()).isEqualTo(StatusPagamento.SUBSTITUIDO);
        assertThat(novo.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        ArgumentCaptor<MensagemEnvelope> envio = ArgumentCaptor.forClass(MensagemEnvelope.class);
        verify(outbox).registrar(eq("billing-eventos"), envio.capture());
        assertThat(envio.getValue().type()).isEqualTo("CobrancaCriada");
        assertThat(envio.getValue().sagaId()).isEqualTo(orcamento.getSagaId());
        assertThat(envio.getValue().osId()).isEqualTo(os);
        assertThat(envio.getValue().payload().path("pagamentoId").asText()).isEqualTo(novo.getId().toString());
        assertThat(envio.getValue().payload().path("link").asText()).isEqualTo("https://mp/novo");
        assertThat(envio.getValue().payload().path("status").asText()).isEqualTo("PENDENTE");
    }

    @Test
    void vigentePendenteOuAprovadoEhRegraDeNegocioESemChamarOMercadoPago() {
        vigente(false);

        assertThatThrownBy(() -> useCase.executar(os)).isInstanceOf(RegraNegocioException.class);

        verifyNoInteractions(emissor, outbox);
        verify(pagamentos, never()).salvar(any());
    }

    @Test
    void semPagamentoEh404() {
        when(pagamentos.buscarVigentePorOs(os)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(os)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void orcamentoDoPagamentoInexistenteEh404() {
        vigente(true);
        when(orcamentos.buscarPorId(orcamento.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(os)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void mercadoPagoForaNaoGravaNada() {
        Pagamento antigo = vigente(true);
        when(emissor.emitir(orcamento)).thenThrow(new MercadoPagoIndisponivelException("fora", null));

        assertThatThrownBy(() -> useCase.executar(os)).isInstanceOf(MercadoPagoIndisponivelException.class);

        assertThat(antigo.getStatus()).isEqualTo(StatusPagamento.RECUSADO);
        verify(pagamentos, never()).salvar(any());
        verifyNoInteractions(outbox);
    }
}
