package br.com.fiap.soat.mecanica.billing.adapters.out.rest.mercadopago;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * RestClient proprio do Mercado Pago (F6-b): URL base configuravel e timeout de 5 s. Parte do builder do
 * Spring (X-Correlation-Id) mas <b>sem</b> repasse do JWT: o token do usuario nunca sai para o Mercado Pago (F4-b).
 * O Bearer e posto por chamada pelo {@link MercadoPagoClient}, para um token vazio nunca ser enviado.
 */
@Configuration
@EnableConfigurationProperties(MercadoPagoProperties.class)
public class MercadoPagoClientConfig {

    @Bean
    RestClient mercadoPagoRestClient(RestClient.Builder builder, MercadoPagoProperties propriedades) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1) // sem upgrade h2c: a API e o WireMock respondem em HTTP/1.1
                .connectTimeout(propriedades.timeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(propriedades.timeout());
        return builder
                .requestFactory(requestFactory)
                .baseUrl(propriedades.baseUrl())
                .build();
    }
}
