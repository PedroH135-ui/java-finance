-- Preserva a identidade da criação mesmo após confirmação, revisão ou cancelamento.
ALTER TABLE movement ADD COLUMN request_fingerprint VARCHAR(64);
