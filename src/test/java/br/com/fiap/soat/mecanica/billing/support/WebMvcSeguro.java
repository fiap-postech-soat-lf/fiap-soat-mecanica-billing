package br.com.fiap.soat.mecanica.billing.support;

import br.com.fiap.soat.mecanica.billing.adapters.in.web.security.JwtTokenValidator;
import br.com.fiap.soat.mecanica.billing.adapters.in.web.security.UsuarioAutenticadoAdapter;
import br.com.fiap.soat.mecanica.billing.config.SecurityConfig;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-anotacao dos testes de controller: carrega a seguranca real (SecurityConfig + JwtTokenValidator).
 * Os tokens sao assinados de verdade por {@link JwtTestTokens}; a chave publica vem de {@link ChavePublicaDeTeste}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({SecurityConfig.class, JwtTokenValidator.class, ChavePublicaDeTeste.class, UsuarioAutenticadoAdapter.class})
public @interface WebMvcSeguro {
}
