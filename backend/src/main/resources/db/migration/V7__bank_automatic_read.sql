ALTER TABLE bank_link ADD COLUMN auto_checked_through DATE;
ALTER TABLE bank_link ADD COLUMN auto_error_at TIMESTAMPTZ;

-- Only delivery metadata, never raw webhook bodies or credentials.
CREATE TABLE bank_sync_event (
  owner_id UUID NOT NULL REFERENCES app_user(id),
  event_id VARCHAR(150) NOT NULL,
  item_id UUID NOT NULL,
  received_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY(owner_id,event_id)
);
CREATE TABLE bank_sync_job (
  owner_id UUID NOT NULL REFERENCES app_user(id),
  item_id UUID NOT NULL,
  requested_version BIGINT NOT NULL DEFAULT 0,
  completed_version BIGINT NOT NULL DEFAULT 0,
  lease_token UUID,
  lease_until TIMESTAMPTZ,
  last_error_at TIMESTAMPTZ,
  PRIMARY KEY(owner_id,item_id)
);
