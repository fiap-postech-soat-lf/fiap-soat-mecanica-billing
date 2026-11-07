package br.com.fiap.soat.mecanica.billing.adapters.in.web.security;

import br.com.fiap.soat.mecanica.billing.support.IntegracaoTest;
import br.com.fiap.soat.mecanica.billing.support.JwtTestTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz de perfis (tabela 3.2 da F6) das rotas existentes: para cada rota, sem token -> 401, perfil errado -> 403
 * e perfil certo passa da seguranca (2xx, 404 ou 422; nunca 401, 403 ou 5xx). As rotas usam ids inexistentes,
 * entao nada e gravado. A rota que ainda nao existe entra na matriz com o seu T: o webhook (T06).
 */
@Transactional
class PerfisIT extends IntegracaoTest {

    static final String CLIENTE = "CLIENTE";
    static final String ATENDENTE = "ATENDENTE";
    static final String MECANICO = "MECANICO";
    static final String ALMOXARIFE = "ALMOXARIFE";
    static final List<String> PERFIS = List.of(CLIENTE, ATENDENTE, MECANICO, ALMOXARIFE);

    record Rota(HttpMethod metodo, String caminho, String corpo, Set<String> permitidos) {
        @Override
        public String toString() {
            return metodo + " " + caminho + " -> " + permitidos;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    static Stream<Rota> rotas() {
        String id = UUID.randomUUID().toString();
        Set<String> clienteOuAtendente = Set.of(CLIENTE, ATENDENTE);
        return Stream.of(
                new Rota(HttpMethod.GET, "/orcamentos/" + id, null, clienteOuAtendente),
                new Rota(HttpMethod.GET, "/orcamentos/por-os/" + id, null, clienteOuAtendente),
                new Rota(HttpMethod.PATCH, "/orcamentos/" + id + "/aprovar", null, clienteOuAtendente),
                new Rota(HttpMethod.PATCH, "/orcamentos/" + id + "/recusar", "{\"motivo\":\"Caro\"}", clienteOuAtendente),
                new Rota(HttpMethod.GET, "/pagamentos/por-os/" + id, null, clienteOuAtendente),
                new Rota(HttpMethod.POST, "/pagamentos/" + id + "/nova-cobranca", null, Set.of(ATENDENTE)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rotas")
    @DisplayName("sem token responde 401")
    void semTokenResponde401(Rota rota) throws Exception {
        mockMvc.perform(requisicao(rota, null)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rotas")
    @DisplayName("perfil errado responde 403")
    void perfilErradoResponde403(Rota rota) throws Exception {
        for (String perfil : PERFIS) {
            if (!rota.permitidos().contains(perfil)) {
                mockMvc.perform(requisicao(rota, perfil)).andExpect(status().isForbidden());
            }
        }
    }

    static Stream<Arguments> rotasEPerfisPermitidos() {
        return rotas().flatMap(rota -> rota.permitidos().stream().sorted()
                .map(perfil -> Arguments.of(Named.of(rota.metodo() + " " + rota.caminho(), rota), perfil)));
    }

    @ParameterizedTest(name = "{0} com {1}")
    @MethodSource("rotasEPerfisPermitidos")
    @DisplayName("perfil certo passa da seguranca (2xx, 404 ou 422)")
    void perfilCertoPassa(Rota rota, String perfil) throws Exception {
        int status = mockMvc.perform(requisicao(rota, perfil)).andReturn().getResponse().getStatus();
        assertThat(status).as("%s com %s", rota, perfil).satisfiesAnyOf(
                s -> assertThat(s).isBetween(200, 299),
                s -> assertThat(s).isIn(404, 422));
    }

    private static MockHttpServletRequestBuilder requisicao(Rota rota, String perfil) {
        MockHttpServletRequestBuilder builder = request(rota.metodo(), rota.caminho());
        if (rota.corpo() != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(rota.corpo());
        }
        if (perfil != null) {
            builder.header("Authorization", "Bearer " + token(perfil));
        }
        return builder;
    }

    private static String token(String perfil) {
        return CLIENTE.equals(perfil)
                ? JwtTestTokens.cliente(UUID.randomUUID())
                : JwtTestTokens.funcionario(UUID.randomUUID(), perfil);
    }
}
