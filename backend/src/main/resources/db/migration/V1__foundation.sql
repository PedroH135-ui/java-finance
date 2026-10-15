CREATE TABLE app_user (
  id UUID PRIMARY KEY,
  email VARCHAR(254) NOT NULL UNIQUE,
  display_name VARCHAR(100) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  credential_version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE account (
  id UUID PRIMARY KEY,
  owner_id UUID NOT NULL REFERENCES app_user(id),
  name VARCHAR(100) NOT NULL,
  institution VARCHAR(100) NOT NULL DEFAULT '',
  kind VARCHAR(30) NOT NULL CHECK (kind IN ('CHECKING','SAVINGS','CASH','WALLET','INVESTMENT','OTHER')),
  currency VARCHAR(3) NOT NULL DEFAULT 'BRL' CHECK (currency = 'BRL'),
  opening_date DATE NOT NULL,
  archived BOOLEAN NOT NULL DEFAULT FALSE,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE (id, owner_id)
);
CREATE UNIQUE INDEX account_name_owner ON account(owner_id, lower(name));
CREATE TABLE category (
  id UUID PRIMARY KEY,
  owner_id UUID NOT NULL REFERENCES app_user(id),
  name VARCHAR(80) NOT NULL,
  kind VARCHAR(10) NOT NULL CHECK (kind IN ('INCOME','EXPENSE')),
  essential BOOLEAN NOT NULL DEFAULT FALSE,
  archived BOOLEAN NOT NULL DEFAULT FALSE,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE(id, owner_id)
);
CREATE UNIQUE INDEX category_name_owner ON category(owner_id, kind, lower(name));
CREATE TABLE movement (
  id UUID PRIMARY KEY,
  owner_id UUID NOT NULL REFERENCES app_user(id),
  description VARCHAR(200) NOT NULL,
  amount NUMERIC(19,2) NOT NULL,
  kind VARCHAR(20) NOT NULL CHECK (kind IN ('OPENING','INCOME','EXPENSE','TRANSFER','ADJUSTMENT')),
  status VARCHAR(15) NOT NULL CHECK (status IN ('PLANNED','CONFIRMED','CANCELLED')),
  account_id UUID NOT NULL,
  destination_id UUID,
  category_id UUID,
  operation_date DATE NOT NULL,
  competence DATE NOT NULL CHECK (extract(day from competence) = 1),
  due_date DATE NOT NULL,
  settled_date DATE,
  notes VARCHAR(1000) NOT NULL DEFAULT '',
  source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
  external_reference VARCHAR(200),
  batch_id UUID,
  request_key UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  UNIQUE(owner_id, request_key),
  UNIQUE(id, owner_id),
  FOREIGN KEY(account_id, owner_id) REFERENCES account(id, owner_id),
  FOREIGN KEY(destination_id, owner_id) REFERENCES account(id, owner_id),
  FOREIGN KEY(category_id, owner_id) REFERENCES category(id, owner_id),
  CHECK (amount > 0 OR kind IN ('OPENING','ADJUSTMENT')),
  CHECK (kind != 'ADJUSTMENT' OR amount != 0),
  CHECK ((kind = 'TRANSFER' AND destination_id IS NOT NULL AND destination_id != account_id) OR (kind != 'TRANSFER' AND destination_id IS NULL)),
  CHECK ((kind IN ('INCOME','EXPENSE') AND category_id IS NOT NULL) OR (kind NOT IN ('INCOME','EXPENSE') AND category_id IS NULL)),
  CHECK (status != 'CONFIRMED' OR settled_date IS NOT NULL)
);
CREATE INDEX movement_owner_competence ON movement(owner_id, competence);
CREATE INDEX movement_owner_due_date ON movement(owner_id, status, due_date);
CREATE TABLE posting (
  id UUID PRIMARY KEY,
  owner_id UUID NOT NULL,
  movement_id UUID NOT NULL,
  account_id UUID NOT NULL,
  amount NUMERIC(19,2) NOT NULL,
  UNIQUE(movement_id, account_id),
  FOREIGN KEY(movement_id, owner_id) REFERENCES movement(id, owner_id),
  FOREIGN KEY(account_id, owner_id) REFERENCES account(id, owner_id)
);
CREATE INDEX posting_account ON posting(account_id);
CREATE TABLE audit_event (
  id UUID PRIMARY KEY,
  owner_id UUID NOT NULL REFERENCES app_user(id),
  resource_id UUID NOT NULL,
  action VARCHAR(40) NOT NULL,
  reason VARCHAR(300) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);

