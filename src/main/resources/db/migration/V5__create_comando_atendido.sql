-- Idempotencia por chave de negocio dos comandos do OS (F4-h, F6-c): cadeia de ids e resposta guardada.

CREATE TABLE comando_atendido (
    os_id          UUID         NOT NULL,
    tipo           VARCHAR(100) NOT NULL,
    ids            JSONB        NOT NULL,
    resposta       JSONB,
    atualizado_em  TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (os_id, tipo)
);
