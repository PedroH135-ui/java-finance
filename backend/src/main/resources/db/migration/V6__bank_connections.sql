CREATE TABLE bank_link (
  id UUID PRIMARY KEY,
  owner_id UUID NOT NULL REFERENCES app_user(id),
  item_id UUID NOT NULL,
  external_account_id UUID NOT NULL,
  account_id UUID NOT NULL,
  name VARCHAR(100) NOT NULL,
  import_from DATE NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  last_checked_at TIMESTAMPTZ,
  last_attempt_at TIMESTAMPTZ,
  provider_updated_at TIMESTAMPTZ,
  reported_balance NUMERIC(19,2),
  UNIQUE(owner_id,external_account_id),
  UNIQUE(owner_id,account_id),
  UNIQUE(id,owner_id),
  FOREIGN KEY(account_id,owner_id) REFERENCES account(id,owner_id)
);
CREATE TABLE bank_entry (
  id UUID PRIMARY KEY,
  owner_id UUID NOT NULL,
  link_id UUID NOT NULL,
  external_id VARCHAR(200) NOT NULL,
  description VARCHAR(200) NOT NULL,
  amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
  date DATE NOT NULL,
  direction VARCHAR(6) NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
  provider_status VARCHAR(10) NOT NULL CHECK (provider_status IN ('POSTED','PENDING')),
  state VARCHAR(12) NOT NULL DEFAULT 'REVIEW' CHECK (state IN ('REVIEW','IMPORTED','MATCHED','IGNORED','CHANGED')),
  movement_id UUID,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE(link_id,external_id),
  FOREIGN KEY(link_id,owner_id) REFERENCES bank_link(id,owner_id),
  FOREIGN KEY(movement_id,owner_id) REFERENCES movement(id,owner_id)
);
CREATE INDEX bank_entry_owner_date ON bank_entry(owner_id,date DESC,id);
CREATE UNIQUE INDEX bank_entry_movement_once ON bank_entry(owner_id,movement_id,link_id) WHERE movement_id IS NOT NULL;
