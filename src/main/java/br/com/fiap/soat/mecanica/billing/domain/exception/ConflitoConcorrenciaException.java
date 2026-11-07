package br.com.fiap.soat.mecanica.billing.domain.exception;

/** Outra operacao alterou o mesmo agregado (bloqueio otimista): responde 409. */
public class ConflitoConcorrenciaException extends RuntimeException {
    public ConflitoConcorrenciaException(String message, Throwable cause) {
        super(message, cause);
    }
}
