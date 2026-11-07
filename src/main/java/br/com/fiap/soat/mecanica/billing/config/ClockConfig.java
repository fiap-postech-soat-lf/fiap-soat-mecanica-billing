package br.com.fiap.soat.mecanica.billing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Relogio unico da aplicacao (UTC), injetado nos casos de uso e nos schedulers para os testes fixarem o tempo. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
