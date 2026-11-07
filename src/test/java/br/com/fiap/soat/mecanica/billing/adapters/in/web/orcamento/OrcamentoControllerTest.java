package br.com.fiap.soat.mecanica.billing.adapters.in.web.orcamento;

import br.com.fiap.soat.mecanica.billing.application.orcamento.usecase.AprovarOrcamentoUseCase;
import br.com.fiap.soat.mecanica.billing.application.orcamento.usecase.BuscarOrcamentoPorOsUseCase;
import br.com.fiap.soat.mecanica.billing.application.orcamento.usecase.BuscarOrcamentoUseCase;
import br.com.fiap.soat.mecanica.billing.application.orcamento.usecase.RecusarOrcamentoUseCase;
import br.com.fiap.soat.mecanica.billing.domain.exception.AcessoARecursoDeOutroClienteException;
import br.com.fiap.soat.mecanica.billing.domain.exception.ConflitoConcorrenciaException;
import br.com.fiap.soat.mecanica.billing.domain.exception.RecursoNaoEncontradoException;
import br.com.fiap.soat.mecanica.billing.domain.exception.RegraNegocioException;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.ItemOrcamento;
import br.com.fiap.soat.mecanica.billing.domain.orcamento.Orcamento;
import br.com.fiap.soat.mecanica.billing.support.JwtTestTokens;
import br.com.fiap.soat.mecanica.billing.support.WebMvcSeguro;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcSeguro
@WebMvcTest(value = OrcamentoController.class, excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
class OrcamentoControllerTest {

    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    BuscarOrcamentoUseCase buscar;
    @MockitoBean
    BuscarOrcamentoPorOsUseCase buscarPorOs;
    @MockitoBean
    AprovarOrcamentoUseCase aprovar;
    @MockitoBean
    RecusarOrcamentoUseCase recusar;

    final UUID cliente = UUID.randomUUID();
    final Orcamento orcamento = Orcamento.gerar(UUID.randomUUID(), UUID.randomUUID(), cliente,
            List.of(ItemOrcamento.servico(UUID.randomUUID(), "Revisao", new BigDecimal("100.00")),
                    ItemOrcamento.peca(UUID.randomUUID(), "Filtro", 2, new BigDecimal("19.90"))),
            new BigDecimal("139.80"), Duration.ofHours(24), Instant.parse("2026-11-07T12:00:00Z"));

    private String atendente() {
        return "Bearer " + JwtTestTokens.funcionario(UUID.randomUUID(), "ATENDENTE");
    }

    private String cliente() {
        return "Bearer " + JwtTestTokens.cliente(cliente);
    }

    @Test
    void getPorIdDevolveItensEValores() throws Exception {
        when(buscar.executar(orcamento.getId())).thenReturn(orcamento);

        mockMvc.perform(get("/orcamentos/" + orcamento.getId()).header("Authorization", cliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orcamento.getId().toString()))
                .andExpect(jsonPath("$.status").value("GERADO"))
                .andExpect(jsonPath("$.valorTotal").value(139.80))
                .andExpect(jsonPath("$.itens.length()").value(2))
                .andExpect(jsonPath("$.itens[1].tipo").value("PECA"))
                .andExpect(jsonPath("$.itens[1].valorTotal").value(39.80))
                .andExpect(jsonPath("$.expiraEm").value("2026-11-08T12:00:00Z"));
    }

    @Test
    void getPorOsDevolveOOrcamento() throws Exception {
        when(buscarPorOs.executar(orcamento.getOsId())).thenReturn(orcamento);

        mockMvc.perform(get("/orcamentos/por-os/" + orcamento.getOsId()).header("Authorization", atendente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.osId").value(orcamento.getOsId().toString()));
    }

    @Test
    void getInexistenteDa404() throws Exception {
        UUID id = UUID.randomUUID();
        when(buscar.executar(id)).thenThrow(new RecursoNaoEncontradoException("Orcamento nao encontrado"));

        mockMvc.perform(get("/orcamentos/" + id).header("Authorization", atendente()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDeOutroClienteDa403ComOCodigoDoPlano() throws Exception {
        when(buscar.executar(orcamento.getId()))
                .thenThrow(new AcessoARecursoDeOutroClienteException("Orcamento de outro cliente"));

        mockMvc.perform(get("/orcamentos/" + orcamento.getId()).header("Authorization", cliente()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.erro").value("ORCAMENTO_DE_OUTRO_CLIENTE"));
    }

    @Test
    void mecanicoNaoAcessaOBilling() throws Exception {
        String mecanico = "Bearer " + JwtTestTokens.funcionario(UUID.randomUUID(), "MECANICO");

        mockMvc.perform(get("/orcamentos/" + orcamento.getId()).header("Authorization", mecanico))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/orcamentos/" + orcamento.getId() + "/aprovar").header("Authorization", mecanico))
                .andExpect(status().isForbidden());

        verifyNoInteractions(buscar, aprovar);
    }

    @Test
    void patchAprovarDevolve200() throws Exception {
        orcamento.aprovar(cliente, Instant.parse("2026-11-07T13:00:00Z"));
        when(aprovar.executar(orcamento.getId())).thenReturn(orcamento);

        mockMvc.perform(patch("/orcamentos/" + orcamento.getId() + "/aprovar").header("Authorization", cliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADO"))
                .andExpect(jsonPath("$.decididoPor").value(cliente.toString()));
    }

    @Test
    void patchAprovarForaDeGeradoOuExpiradoDa422() throws Exception {
        when(aprovar.executar(orcamento.getId())).thenThrow(new RegraNegocioException("Orcamento expirado"));

        mockMvc.perform(patch("/orcamentos/" + orcamento.getId() + "/aprovar").header("Authorization", cliente()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void conflitoDeVersaoDa409() throws Exception {
        when(aprovar.executar(orcamento.getId()))
                .thenThrow(new ConflitoConcorrenciaException("conflito", new RuntimeException()));

        mockMvc.perform(patch("/orcamentos/" + orcamento.getId() + "/aprovar").header("Authorization", cliente()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro").value("CONFLITO_CONCORRENTE"));
    }

    @Test
    void patchRecusarComMotivo() throws Exception {
        orcamento.recusar(cliente, "Caro", Instant.parse("2026-11-07T13:00:00Z"));
        when(recusar.executar(eq(orcamento.getId()), any())).thenReturn(orcamento);

        mockMvc.perform(patch("/orcamentos/" + orcamento.getId() + "/recusar").header("Authorization", cliente())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Caro\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECUSADO"))
                .andExpect(jsonPath("$.motivo").value("Caro"));

        verify(recusar).executar(orcamento.getId(), "Caro");
    }

    @Test
    void patchRecusarSemCorpoAceitaMotivoNulo() throws Exception {
        when(recusar.executar(orcamento.getId(), null)).thenReturn(orcamento);

        mockMvc.perform(patch("/orcamentos/" + orcamento.getId() + "/recusar").header("Authorization", atendente()))
                .andExpect(status().isOk());

        verify(recusar).executar(orcamento.getId(), null);
    }

    @Test
    void motivoLongoDemaisDa400() throws Exception {
        String longo = "x".repeat(501);

        mockMvc.perform(patch("/orcamentos/" + orcamento.getId() + "/recusar").header("Authorization", cliente())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"" + longo + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(recusar);
    }
}
