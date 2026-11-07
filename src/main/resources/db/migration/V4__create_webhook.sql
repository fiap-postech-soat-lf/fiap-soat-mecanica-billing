-- Webhook do Mercado Pago (F6-i): eventos recebidos e idempotencia por (payment.id, status).

CREATE TABLE webhook_eventos (
    id                   UUID PRIMARY KEY,
    request_id           VARCHAR(100),
    mp_payment_id        VARCHAR(50)  NOT NULL,
    tipo                 VARCHAR(50)  NOT NULL,
    acao                 VARCHAR(100),
    recebido_em          TIMESTAMPTZ  NOT NULL,
    status               VARCHAR(20)  NOT NULL
        CONSTRAINT ck_webhook_eventos_status CHECK (status IN ('PENDENTE', 'PROCESSADO', 'IGNORADO', 'ERRO')),
    tentativas           INT          NOT NULL DEFAULT 0,
    proxima_tentativa_em TIMESTAMPTZ,
    status_mp            VARCHAR(30),
    ultimo_erro          TEXT
);
CREATE INDEX ix_webhook_eventos_pendentes ON webhook_eventos (proxima_tentativa_em) WHERE status = 'PENDENTE';

CREATE TABLE mp_status_aplicado (
    mp_payment_id VARCHAR(50)  NOT NULL,
    status        VARCHAR(30)  NOT NULL,
    pagamento_id  UUID,
    aplicado_em   TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (mp_payment_id, status)
);
