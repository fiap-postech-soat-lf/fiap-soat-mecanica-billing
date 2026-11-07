package br.com.fiap.soat.mecanica.billing.support;

import br.com.fiap.soat.mecanica.billing.adapters.in.web.security.JwtProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Substitui o JwtProperties lido do ambiente por um com a chave publica gerada em memoria. */
@TestConfiguration(proxyBeanMethods = false)
public class ChavePublicaDeTeste {

    @Bean
    @Primary
    JwtProperties jwtPropertiesDeTeste() {
        return new JwtProperties(JwtTestTokens.chavePublicaPem(), JwtTestTokens.ISSUER, JwtTestTokens.AUDIENCE);
    }
}
