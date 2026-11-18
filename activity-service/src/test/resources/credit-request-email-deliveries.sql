CREATE TABLE public.credit_request_email_deliveries (
    event_id BIGINT PRIMARY KEY,
    financial_institution_id BIGINT NOT NULL,
    credit_request_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL CHECK (event_type IN (
        'CREDIT_REQUEST_APPROVED', 'CREDIT_REQUEST_REJECTED', 'CREDIT_REQUEST_EXPIRED'
    )),
    reason TEXT,
    delivery_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    queued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
