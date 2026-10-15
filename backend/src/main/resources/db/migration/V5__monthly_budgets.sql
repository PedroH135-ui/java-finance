CREATE TABLE monthly_budget (
  id UUID PRIMARY KEY,
  owner_id UUID NOT NULL REFERENCES app_user(id),
  category_id UUID NOT NULL,
  month DATE NOT NULL CHECK (extract(day from month) = 1 AND month BETWEEN DATE '1900-01-01' AND DATE '2200-12-01'),
  amount NUMERIC(19,2) NOT NULL CHECK (amount >= 0),
  version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
  UNIQUE(owner_id, category_id, month),
  FOREIGN KEY(category_id, owner_id) REFERENCES category(id, owner_id)
);
