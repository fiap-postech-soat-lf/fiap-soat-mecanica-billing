-- Pagamento (cobranca no Mercado Pago, F6-h). Um pagamento vigente (nao SUBSTITUIDO) por OS.

CREATE TABLE pagamentos (
    id               UUID PRIMARY KEY,
    os_id            UUID           NOT NULL,
    orcamento_id     UUID           NOT NULL REFERENCES orcamentos (id),
    cliente_id       UUID           NOT NULL,
    valor            NUMERIC(12, 2) NOT NULL,
    status           VARCHAR(20)    NOT NULL
        CONSTRAINT ck_pagamentos_status CHECK (status IN ('PENDENTE', 'APROVADO', 'RECUSADO', 'SUBSTITUIDO')),
    mp_preference_id VARCHAR(100),
    link             TEXT,
    mp_payment_id    VARCHAR(50),
    motivo           TEXT,
    criado_em        TIMESTAMPTZ    NOT NULL,
    atualizado_em    TIMESTAMPTZ    NOT NULL,
    versao           BIGINT         NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_pagamentos_vigente_por_os ON pagamentos (os_id) WHERE status <> 'SUBSTITUIDO';
CREATE INDEX ix_pagamentos_os ON pagamentos (os_id, criado_em DESC);
