package br.com.fiap.soat.mecanica.billing.application.saga;

import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemEnvelope;
import br.com.fiap.soat.mecanica.billing.application.mensageria.MensagemFactory;
import br.com.fiap.soat.mecanica.billing.application.mensageria.OutboxPort;
import br.com.fiap.soat.mecanica.billing.application.pagamento.EmissorDeCobranca;
import br.com.fiap.soat.mecanica.billing.application.pagamento.exception.MercadoPagoIndisponivelException;
import br.com.fiap.soat.mecanica.billing.application.saga.RespostasSaga.Resposta;
import br.com.fiap.soat.mecanica.billing.application.saga.dto.SolicitarPagamentoPayload;
import br.com.fiap.soat.mecanica.billing.application.saga.dto.CancelarOrcamentoPayload;
import br.com.fiap.soat.mecanica.billing.application.saga.dto.GerarOrcamentoPayload;
import br.com.fiap.soat.mecanica.billing.application.saga.dto.GerarOrcamentoPayload.ItemServico;
import br.com.fiap.soat.mecanica.billing.application.saga.dto.GerarOrcamentoPayload.PecaOrcada;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.OrcamentoRepository;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.StatusOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.TipoItem;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.Pagamento;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.PagamentoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Uma linha da tabela 4.1 por teste, mais a idempotencia por chave de negocio (F6-c). */
class ParticipanteSagaTest {

    static final Instant AGORA = Instant.parse("2026-11-07T12:00:00Z");
    static final Duration TTL = Duration.ofHours(24);
    static final UUID OS = UUID.randomUUID();
    static final UUID SAGA = UUID.randomUUID();
    static final UUID CLIENTE = UUID.randomUUID();

    OrcamentoRepository orcamentos = mock(OrcamentoRepository.class);
    PagamentoRepository pagamentos = mock(PagamentoRepository.class);
    ComandosAtendidos comandos = mock(ComandosAtendidos.class);
    EmissorDeCobranca emissor = mock(EmissorDeCobranca.class);
    OutboxPort outbox = mock(OutboxPort.class);
    ObjectMapper mapper = new ObjectMapper();
    ParticipanteSaga participante;

    @BeforeEach
    void preparar() {
        Clock clock = Clock.fixed(AGORA, ZoneOffset.UTC);
        RespostasSaga respostas = new RespostasSaga(new MensagemFactory(mapper), mapper);
        participante = new ParticipanteSaga(orcamentos, pagamentos, comandos, respostas, emissor, outbox, clock, TTL);
        when(orcamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));
        when(comandos.buscar(any(), anyString())).thenReturn(Optional.empty());
    }

    // ---- GerarOrcamento -------------------------------------------------------------------------------

    private static GerarOrcamentoPayload comando(BigDecimal total) {
        return new GerarOrcamentoPayload(OS, CLIENTE, List.of(
                new ItemServico(UUID.randomUUID(), "Troca de oleo", new BigDecimal("80.0"), List.of(
                        new PecaOrcada(UUID.randomUUID(), "Filtro", 2, new BigDecimal("39.9")))),
                new ItemServico(UUID.randomUUID(), "Alinhamento", new BigDecimal("120.00"), List.of())), total);
    }

    private static MensagemEnvelope envelope(String tipo, UUID messageId, UUID causationId) {
        return new MensagemEnvelope(messageId, tipo, 1, SAGA, OS, "corr", causationId, AGORA, null, null);
    }

    private MensagemEnvelope enviado() {
        ArgumentCaptor<MensagemEnvelope> captor = ArgumentCaptor.forClass(MensagemEnvelope.class);
        verify(outbox).registrar(eq("billing-eventos"), captor.capture());
        return captor.getValue();
    }

    @Test
    void gerarCriaOrcamentoComItensDeServicoEPecaERespondeOrcamentoGerado() {
        UUID messageId = UUID.randomUUID();

        participante.gerarOrcamento(envelope("GerarOrcamento", messageId, null), comando(new BigDecimal("279.80")));

        ArgumentCaptor<Orcamento> salvo = ArgumentCaptor.forClass(Orcamento.class);
        verify(orcamentos).salvar(salvo.capture());
        Orcamento o = salvo.getValue();
        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.GERADO);
        assertThat(o.getSagaId()).isEqualTo(SAGA);
        assertThat(o.getClienteId()).isEqualTo(CLIENTE);
        assertThat(o.getValorTotal()).isEqualByComparingTo("279.80");
        assertThat(o.getExpiraEm()).isEqualTo(AGORA.plus(TTL));
        assertThat(o.getItens()).extracting("tipo").containsExactly(TipoItem.SERVICO, TipoItem.PECA, TipoItem.SERVICO);

        MensagemEnvelope resposta = enviado();
        assertThat(resposta.type()).isEqualTo("OrcamentoGerado");
        assertThat(resposta.sagaId()).isEqualTo(SAGA);
        assertThat(resposta.osId()).isEqualTo(OS);
        assertThat(resposta.causationId()).isEqualTo(messageId);
        assertThat(resposta.payload().path("orcamentoId").asText()).isEqualTo(o.getId().toString());
        assertThat(resposta.payload().path("expiraEm").asText()).isEqualTo(AGORA.plus(TTL).toString());
        verify(comandos).registrar(eq(OS), eq("GerarOrcamento"), eq(messageId), any(Resposta.class));
    }

    @Test
    void totalDivergenteRespondeOrcamentoFalhouESemCriarNada() {
        UUID messageId = UUID.randomUUID();

        participante.gerarOrcamento(envelope("GerarOrcamento", messageId, null), comando(new BigDecimal("100.00")));

        verify(orcamentos, never()).salvar(any());
        MensagemEnvelope resposta = enviado();
        assertThat(resposta.type()).isEqualTo("OrcamentoFalhou");
        assertThat(resposta.payload().path("motivo").asText()).contains("Total divergente");
        // a falha tambem fica guardada, para o reenvio reemitir a mesma falha
        ArgumentCaptor<Resposta> guardada = ArgumentCaptor.forClass(Resposta.class);
        verify(comandos).registrar(eq(OS), eq("GerarOrcamento"), eq(messageId), guardada.capture());
        assertThat(guardada.getValue().tipo()).isEqualTo("OrcamentoFalhou");
    }

    @Test
    void itemSemPrecoDeMaoDeObraRespondeOrcamentoFalhou() {
        GerarOrcamentoPayload semPreco = new GerarOrcamentoPayload(OS, CLIENTE, List.of(
                new ItemServico(UUID.randomUUID(), "Revisao", null, List.of())), new BigDecimal("10.00"));

        participante.gerarOrcamento(envelope("GerarOrcamento", UUID.randomUUID(), null), semPreco);

        assertThat(enviado().type()).isEqualTo("OrcamentoFalhou");
        verify(orcamentos, never()).salvar(any());
    }

    @Test
    void payloadSemItensRespondeOrcamentoFalhou() {
        participante.gerarOrcamento(envelope("GerarOrcamento", UUID.randomUUID(), null),
                new GerarOrcamentoPayload(OS, CLIENTE, null, BigDecimal.ZERO));

        assertThat(enviado().type()).isEqualTo("OrcamentoFalhou");
    }

    @Test
    void novaRodadaCancelaOGeradoAnteriorComoSubstituidoAntesDeGravarONovo() {
        Orcamento anterior = Orcamento.gerar(OS, SAGA, CLIENTE, List.of(
                br.com.fiap.soat.mecanica.billing.domain.orcamento.ItemOrcamento.servico(UUID.randomUUID(), "Antigo", new BigDecimal("50.00"))),
                new BigDecimal("50.00"), TTL, AGORA.minusSeconds(60));
        when(orcamentos.buscarVivoPorOs(OS)).thenReturn(Optional.of(anterior));

        participante.gerarOrcamento(envelope("GerarOrcamento", UUID.randomUUID(), null), comando(new BigDecimal("279.80")));

        ArgumentCaptor<Orcamento> salvos = ArgumentCaptor.forClass(Orcamento.class);
        verify(orcamentos, org.mockito.Mockito.times(2)).salvar(salvos.capture());
        assertThat(salvos.getAllValues().get(0)).isSameAs(anterior);
        assertThat(anterior.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(anterior.getMotivo()).isEqualTo("SUBSTITUIDO");
        assertThat(salvos.getAllValues().get(1).getStatus()).isEqualTo(StatusOrcamento.GERADO);
        assertThat(enviado().type()).isEqualTo("OrcamentoGerado");
    }

    @Test
    void novaRodadaComOrcamentoAprovadoRespondeOrcamentoFalhouESemMexerNele() {
        Orcamento aprovado = Orcamento.gerar(OS, SAGA, CLIENTE, List.of(
                br.com.fiap.soat.mecanica.billing.domain.orcamento.ItemOrcamento.servico(UUID.randomUUID(), "Antigo", new BigDecimal("50.00"))),
                new BigDecimal("50.00"), TTL, AGORA);
        aprovado.aprovar(CLIENTE, AGORA);
        when(orcamentos.buscarVivoPorOs(OS)).thenReturn(Optional.of(aprovado));

        participante.gerarOrcamento(envelope("GerarOrcamento", UUID.randomUUID(), null), comando(new BigDecimal("279.80")));

        verify(orcamentos, never()).salvar(any());
        assertThat(enviado().type()).isEqualTo("OrcamentoFalhou");
        assertThat(aprovado.getStatus()).isEqualTo(StatusOrcamento.APROVADO);
    }

    // ---- F6-c: reenvio --------------------------------------------------------------------------------

    private ComandoAtendido guardado(UUID... ids) {
        Resposta r = new RespostasSaga(new MensagemFactory(mapper), mapper)
                .orcamentoFalhou(OS, "Total divergente: calculado 1.00, informado 2.00");
        return new ComandoAtendido(List.of(ids), r.tipo(), r.payload());
    }

    @Test
    void reenvioComCausationIdDoOriginalReemiteARespostaGuardadaSemEfeitoDeNegocio() {
        UUID original = UUID.randomUUID();
        UUID reenvio = UUID.randomUUID();
        when(comandos.buscar(OS, "GerarOrcamento")).thenReturn(Optional.of(guardado(original)));

        participante.gerarOrcamento(envelope("GerarOrcamento", reenvio, original), comando(new BigDecimal("279.80")));

        verify(orcamentos, never()).salvar(any());
        verify(comandos).acrescentar(OS, "GerarOrcamento", reenvio);
        verify(comandos, never()).registrar(any(), anyString(), any(), any());
        MensagemEnvelope resposta = enviado();
        assertThat(resposta.type()).isEqualTo("OrcamentoFalhou");
        assertThat(resposta.messageId()).isNotIn(original, reenvio);
        assertThat(resposta.causationId()).isEqualTo(reenvio);
    }

    @Test
    void reenvioComCausationIdDoPrimeiroReenvioTambemReemite() {
        UUID original = UUID.randomUUID();
        UUID primeiroReenvio = UUID.randomUUID();
        when(comandos.buscar(OS, "GerarOrcamento")).thenReturn(Optional.of(guardado(original, primeiroReenvio)));

        participante.gerarOrcamento(envelope("GerarOrcamento", UUID.randomUUID(), primeiroReenvio),
                comando(new BigDecimal("279.80")));

        verify(orcamentos, never()).salvar(any());
        assertThat(enviado().type()).isEqualTo("OrcamentoFalhou");
    }

    @Test
    void comandoForaDaCadeiaEUmaNovaRodadaQueSubstituiAChave() {
        UUID novo = UUID.randomUUID();
        when(comandos.buscar(OS, "GerarOrcamento")).thenReturn(Optional.of(guardado(UUID.randomUUID())));

        participante.gerarOrcamento(envelope("GerarOrcamento", novo, UUID.randomUUID()),
                comando(new BigDecimal("279.80")));

        verify(comandos, never()).acrescentar(any(), anyString(), any());
        verify(comandos).registrar(eq(OS), eq("GerarOrcamento"), eq(novo), any(Resposta.class));
        assertThat(enviado().type()).isEqualTo("OrcamentoGerado");
    }

    // ---- CancelarOrcamento ----------------------------------------------------------------------------

    private Orcamento gerado() {
        return Orcamento.gerar(OS, SAGA, CLIENTE, List.of(
                br.com.fiap.soat.mecanica.billing.domain.orcamento.ItemOrcamento.servico(UUID.randomUUID(), "Servico", new BigDecimal("50.00"))),
                new BigDecimal("50.00"), TTL, AGORA);
    }

    private static CancelarOrcamentoPayload cancelar(Orcamento o) {
        return new CancelarOrcamentoPayload(OS, o.getId(), "Revisao do diagnostico");
    }

    @Test
    void cancelarGeradoCancelaESemResposta() {
        Orcamento o = gerado();
        when(orcamentos.buscarPorId(o.getId())).thenReturn(Optional.of(o));

        participante.cancelarOrcamento(envelope("CancelarOrcamento", UUID.randomUUID(), null), cancelar(o));

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(o.getMotivo()).isEqualTo("Revisao do diagnostico");
        verify(orcamentos).salvar(o);
        verifyNoInteractions(outbox, comandos);
    }

    @Test
    void cancelarAprovadoSemPagamentoCancela() {
        Orcamento o = gerado();
        o.aprovar(CLIENTE, AGORA);
        when(orcamentos.buscarPorId(o.getId())).thenReturn(Optional.of(o));
        when(pagamentos.buscarVigentePorOs(OS)).thenReturn(Optional.empty());

        participante.cancelarOrcamento(envelope("CancelarOrcamento", UUID.randomUUID(), null), cancelar(o));

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
    }

    @Test
    void cancelarAprovadoComPagamentoNaoFazNada() {
        Orcamento o = gerado();
        o.aprovar(CLIENTE, AGORA);
        when(orcamentos.buscarPorId(o.getId())).thenReturn(Optional.of(o));
        when(pagamentos.buscarVigentePorOs(OS)).thenReturn(Optional.of(
                Pagamento.criar(OS, o.getId(), CLIENTE, o.getValorTotal(), AGORA)));

        participante.cancelarOrcamento(envelope("CancelarOrcamento", UUID.randomUUID(), null), cancelar(o));

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.APROVADO);
        verify(orcamentos, never()).salvar(any());
    }

    @Test
    void cancelarOrcamentoEmOutroEstadoNaoFazNada() {
        Orcamento o = gerado();
        o.recusar(CLIENTE, "caro", AGORA);
        when(orcamentos.buscarPorId(o.getId())).thenReturn(Optional.of(o));

        participante.cancelarOrcamento(envelope("CancelarOrcamento", UUID.randomUUID(), null), cancelar(o));

        assertThat(o.getStatus()).isEqualTo(StatusOrcamento.RECUSADO);
        verify(orcamentos, never()).salvar(any());
    }

    @Test
    void cancelarOrcamentoInexistenteOuDeOutraOsNaoFazNada() {
        Orcamento deOutraOs = Orcamento.gerar(UUID.randomUUID(), SAGA, CLIENTE, List.of(
                br.com.fiap.soat.mecanica.billing.domain.orcamento.ItemOrcamento.servico(UUID.randomUUID(), "Servico", new BigDecimal("50.00"))),
                new BigDecimal("50.00"), TTL, AGORA);
        when(orcamentos.buscarPorId(deOutraOs.getId())).thenReturn(Optional.of(deOutraOs));

        participante.cancelarOrcamento(envelope("CancelarOrcamento", UUID.randomUUID(), null),
                new CancelarOrcamentoPayload(OS, UUID.randomUUID(), "x"));
        participante.cancelarOrcamento(envelope("CancelarOrcamento", UUID.randomUUID(), null), cancelar(deOutraOs));

        assertThat(deOutraOs.getStatus()).isEqualTo(StatusOrcamento.GERADO);
        verify(orcamentos, never()).salvar(any());
    }

    @Test
    void naoUsaListaMutavelNoPayloadDeItens() {
        // garante que ArrayList (sem copia defensiva no payload) nao quebra o mapeamento
        List<ItemServico> itens = new ArrayList<>(comando(new BigDecimal("279.80")).itens());
        participante.gerarOrcamento(envelope("GerarOrcamento", UUID.randomUUID(), null),
                new GerarOrcamentoPayload(OS, CLIENTE, itens, new BigDecimal("279.80")));

        assertThat(enviado().type()).isEqualTo("OrcamentoGerado");
    }

    // ---- SolicitarPagamento ---------------------------------------------------------------------------

    private Orcamento aprovado() {
        Orcamento o = gerado();
        o.aprovar(CLIENTE, AGORA);
        when(orcamentos.buscarPorId(o.getId())).thenReturn(Optional.of(o));
        return o;
    }

    private Pagamento cobranca(Orcamento o) {
        Pagamento p = Pagamento.criar(OS, o.getId(), CLIENTE, o.getValorTotal(), AGORA);
        p.registrarPreferencia("pref-1", "https://mp/checkout?pref_id=1");
        when(emissor.emitir(o)).thenReturn(p);
        when(pagamentos.salvar(any())).thenAnswer(i -> i.getArgument(0));
        return p;
    }

    private static SolicitarPagamentoPayload solicitar(Orcamento o, BigDecimal valor) {
        return new SolicitarPagamentoPayload(OS, o.getId(), valor);
    }

    @Test
    void solicitarPagamentoComOrcamentoAprovadoCriaACobrancaERespondeCobrancaCriada() {
        Orcamento o = aprovado();
        Pagamento p = cobranca(o);
        UUID messageId = UUID.randomUUID();

        participante.solicitarPagamento(envelope("SolicitarPagamento", messageId, null), solicitar(o, o.getValorTotal()));

        verify(pagamentos).salvar(p);
        verify(comandos).registrar(eq(OS), eq("SolicitarPagamento"), eq(messageId), any(Resposta.class));
        MensagemEnvelope resposta = enviado();
        assertThat(resposta.type()).isEqualTo("CobrancaCriada");
        assertThat(resposta.sagaId()).isEqualTo(SAGA);
        assertThat(resposta.causationId()).isEqualTo(messageId);
        assertThat(resposta.payload().path("pagamentoId").asText()).isEqualTo(p.getId().toString());
        assertThat(resposta.payload().path("link").asText()).isEqualTo("https://mp/checkout?pref_id=1");
        assertThat(resposta.payload().path("status").asText()).isEqualTo("PENDENTE");
    }

    @Test
    void solicitarPagamentoComValorDivergenteCobraOValorDoOrcamento() {
        Orcamento o = aprovado();
        cobranca(o);

        participante.solicitarPagamento(envelope("SolicitarPagamento", UUID.randomUUID(), null),
                solicitar(o, new BigDecimal("1.00")));

        verify(emissor).emitir(o);
        assertThat(enviado().type()).isEqualTo("CobrancaCriada");
    }

    @Test
    void solicitarPagamentoSemOrcamentoAprovadoNaoCriaPreferenciaENaoResponde() {
        Orcamento o = gerado();
        when(orcamentos.buscarPorId(o.getId())).thenReturn(Optional.of(o));

        participante.solicitarPagamento(envelope("SolicitarPagamento", UUID.randomUUID(), null), solicitar(o, o.getValorTotal()));
        participante.solicitarPagamento(envelope("SolicitarPagamento", UUID.randomUUID(), null),
                new SolicitarPagamentoPayload(OS, UUID.randomUUID(), o.getValorTotal()));

        verifyNoInteractions(emissor, outbox);
        verify(pagamentos, never()).salvar(any());
        verify(comandos, never()).registrar(any(), anyString(), any(), any());
    }

    @Test
    void solicitarPagamentoDeOrcamentoDeOutraOsNaoCria() {
        Orcamento deOutraOs = Orcamento.gerar(UUID.randomUUID(), SAGA, CLIENTE, gerado().getItens(),
                new BigDecimal("50.00"), TTL, AGORA);
        deOutraOs.aprovar(CLIENTE, AGORA);
        when(orcamentos.buscarPorId(deOutraOs.getId())).thenReturn(Optional.of(deOutraOs));

        participante.solicitarPagamento(envelope("SolicitarPagamento", UUID.randomUUID(), null),
                solicitar(deOutraOs, deOutraOs.getValorTotal()));

        verifyNoInteractions(emissor, outbox);
    }

    @Test
    void solicitarPagamentoComPagamentoVigenteNaoCriaOutro() {
        Orcamento o = aprovado();
        when(pagamentos.buscarVigentePorOs(OS)).thenReturn(Optional.of(Pagamento.criar(OS, o.getId(), CLIENTE,
                o.getValorTotal(), AGORA)));

        participante.solicitarPagamento(envelope("SolicitarPagamento", UUID.randomUUID(), null), solicitar(o, o.getValorTotal()));

        verifyNoInteractions(emissor, outbox);
    }

    @Test
    void reenvioDeSolicitarPagamentoReemiteSemNovaPreferencia() {
        UUID original = UUID.randomUUID();
        UUID reenvio = UUID.randomUUID();
        Pagamento p = Pagamento.criar(OS, UUID.randomUUID(), CLIENTE, new BigDecimal("50.00"), AGORA);
        p.registrarPreferencia("pref", "https://mp/c");
        Resposta guardada = respostasReais().cobrancaCriada(p);
        when(comandos.buscar(OS, "SolicitarPagamento")).thenReturn(Optional.of(
                new ComandoAtendido(List.of(original), guardada.tipo(), guardada.payload())));

        participante.solicitarPagamento(envelope("SolicitarPagamento", reenvio, original),
                solicitar(gerado(), new BigDecimal("50.00")));

        verifyNoInteractions(emissor, pagamentos);
        verify(comandos).acrescentar(OS, "SolicitarPagamento", reenvio);
        verify(comandos, never()).registrar(any(), anyString(), any(), any());
        MensagemEnvelope resposta = enviado();
        assertThat(resposta.type()).isEqualTo("CobrancaCriada");
        assertThat(resposta.causationId()).isEqualTo(reenvio);
        assertThat(resposta.payload()).isEqualTo(guardada.payload());
    }

    @Test
    void mercadoPagoForaPropagaESemEfeitoGravado() {
        Orcamento o = aprovado();
        when(emissor.emitir(o)).thenThrow(new MercadoPagoIndisponivelException("fora", null));

        assertThatThrownBy(() -> participante.solicitarPagamento(
                envelope("SolicitarPagamento", UUID.randomUUID(), null), solicitar(o, o.getValorTotal())))
                .isInstanceOf(MercadoPagoIndisponivelException.class);

        verify(pagamentos, never()).salvar(any());
        verify(comandos, never()).registrar(any(), anyString(), any(), any());
        verifyNoInteractions(outbox);
    }

    private RespostasSaga respostasReais() {
        return new RespostasSaga(new MensagemFactory(mapper), mapper);
    }
}
