package br.com.fiap.soat.mecanica.billing.adapters.in.web.pagamento;

import br.com.fiap.soat.mecanica.billing.application.pagamento.usecase.BuscarPagamentosPorOsUseCase;
import br.com.fiap.soat.mecanica.billing.application.pagamento.exception.MercadoPagoIndisponivelException;
import br.com.fiap.soat.mecanica.billing.application.pagamento.usecase.BuscarPagamentosPorOsUseCase.Resultado;
import br.com.fiap.soat.mecanica.billing.application.pagamento.usecase.NovaCobrancaUseCase;
import br.com.fiap.soat.mecanica.billing.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.billing.domain.exception.AcessoARecursoDeOutroClienteException;
import br.com.fiap.soat.mecanica.billing.domain.pagamento.Pagamento;
import br.com.fiap.soat.mecanica.billing.support.JwtTestTokens;
import br.com.fiap.soat.mecanica.billing.support.WebMvcSeguro;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcSeguro
@WebMvcTest(value = PagamentoController.class, excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
class PagamentoControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    BuscarPagamentosPorOsUseCase buscarPorOs;
    @MockitoBean
    NovaCobrancaUseCase novaCobranca;

    final UUID os = UUID.randomUUID();
    final UUID cliente = UUID.randomUUID();

    @Test
    void devolveOVigenteComLinkEOHistorico() throws Exception {
        Pagamento antigo = Pagamento.criar(os, UUID.randomUUID(), cliente, new BigDecimal("50.00"), Instant.EPOCH);
        antigo.recusar("1", "cc_rejected_other_reason", Instant.EPOCH);
        antigo.substituir(Instant.EPOCH);
        Pagamento vigente = Pagamento.criar(os, UUID.randomUUID(), cliente, new BigDecimal("50.00"), Instant.EPOCH);
        vigente.registrarPreferencia("pref", "https://mp/checkout");
        when(buscarPorOs.executar(os)).thenReturn(new Resultado(vigente, List.of(vigente, antigo)));

        mockMvc.perform(get("/pagamentos/por-os/" + os)
                        .header("Authorization", "Bearer " + JwtTestTokens.cliente(cliente)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vigente.status").value("PENDENTE"))
                .andExpect(jsonPath("$.vigente.link").value("https://mp/checkout"))
                .andExpect(jsonPath("$.historico.length()").value(2))
                .andExpect(jsonPath("$.historico[1].status").value("SUBSTITUIDO"));
    }

    @Test
    void clienteDeOutroDa403() throws Exception {
        when(buscarPorOs.executar(os)).thenThrow(new AcessoARecursoDeOutroClienteException("Pagamento de outro cliente"));

        mockMvc.perform(get("/pagamentos/por-os/" + os)
                        .header("Authorization", "Bearer " + JwtTestTokens.cliente(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.erro").value("ORCAMENTO_DE_OUTRO_CLIENTE"));
    }

    @Test
    void almoxarifeNaoAcessa() throws Exception {
        mockMvc.perform(get("/pagamentos/por-os/" + os)
                        .header("Authorization", "Bearer " + JwtTestTokens.funcionario(UUID.randomUUID(), "ALMOXARIFE")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(buscarPorOs);
    }

    private String atendente() {
        return "Bearer " + JwtTestTokens.funcionario(UUID.randomUUID(), "ATENDENTE");
    }

    @Test
    void novaCobrancaDevolve201ComOPagamentoNovo() throws Exception {
        Pagamento novo = Pagamento.criar(os, UUID.randomUUID(), cliente, new BigDecimal("50.00"), Instant.EPOCH);
        novo.registrarPreferencia("pref", "https://mp/novo");
        when(novaCobranca.executar(os)).thenReturn(novo);

        mockMvc.perform(post("/pagamentos/" + os + "/nova-cobranca").header("Authorization", atendente()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.link").value("https://mp/novo"));
    }

    @Test
    void novaCobrancaSemVigenteRecusadoDa422() throws Exception {
        when(novaCobranca.executar(os)).thenThrow(new RegraNegocioException("Nova cobranca exige pagamento vigente RECUSADO"));

        mockMvc.perform(post("/pagamentos/" + os + "/nova-cobranca").header("Authorization", atendente()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void novaCobrancaComMercadoPagoForaDa502() throws Exception {
        when(novaCobranca.executar(os)).thenThrow(new MercadoPagoIndisponivelException("fora", null));

        mockMvc.perform(post("/pagamentos/" + os + "/nova-cobranca").header("Authorization", atendente()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.erro").value("MERCADOPAGO_INDISPONIVEL"));
    }

    @Test
    void novaCobrancaNaoEDoCliente() throws Exception {
        mockMvc.perform(post("/pagamentos/" + os + "/nova-cobranca")
                        .header("Authorization", "Bearer " + JwtTestTokens.cliente(cliente)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(novaCobranca);
    }
}
