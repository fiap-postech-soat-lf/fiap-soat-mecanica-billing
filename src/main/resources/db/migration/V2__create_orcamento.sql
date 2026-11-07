-- Orcamento da OS (F6-d). No maximo um orcamento vivo (GERADO ou APROVADO) por OS.

CREATE TABLE orcamentos (
    id            UUID PRIMARY KEY,
    os_id         UUID           NOT NULL,
    saga_id       UUID           NOT NULL,
    cliente_id    UUID           NOT NULL,
    status        VARCHAR(20)    NOT NULL
        CONSTRAINT ck_orcamentos_status CHECK (status IN ('GERADO', 'APROVADO', 'RECUSADO', 'EXPIRADO', 'CANCELADO')),
    valor_total   NUMERIC(12, 2) NOT NULL,
    expira_em     TIMESTAMPTZ    NOT NULL,
    criado_em     TIMESTAMPTZ    NOT NULL,
    decidido_em   TIMESTAMPTZ,
    decidido_por  UUID,
    motivo        TEXT,
    versao        BIGINT         NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_orcamentos_vivo_por_os ON orcamentos (os_id) WHERE status IN ('GERADO', 'APROVADO');
CREATE INDEX ix_orcamentos_os ON orcamentos (os_id, criado_em DESC);
CREATE INDEX ix_orcamentos_a_expirar ON orcamentos (expira_em) WHERE status = 'GERADO';

CREATE TABLE itens_orcamento (
    id             UUID PRIMARY KEY,
    orcamento_id   UUID           NOT NULL REFERENCES orcamentos (id),
    ordem          INT            NOT NULL,
    tipo           VARCHAR(10)    NOT NULL
        CONSTRAINT ck_itens_orcamento_tipo CHECK (tipo IN ('SERVICO', 'PECA')),
    referencia_id  UUID           NOT NULL,
    descricao      VARCHAR(500)   NOT NULL,
    quantidade     INT            NOT NULL CHECK (quantidade >= 1),
    valor_unitario NUMERIC(12, 2) NOT NULL CHECK (valor_unitario >= 0),
    valor_total    NUMERIC(12, 2) NOT NULL CHECK (valor_total >= 0)
);
CREATE INDEX ix_itens_orcamento_orcamento ON itens_orcamento (orcamento_id, ordem);
