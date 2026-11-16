-- Apply only in the dedicated category_vectors database, as its migration role.
CREATE EXTENSION IF NOT EXISTS vector;
CREATE SCHEMA IF NOT EXISTS category_rag;
CREATE TABLE category_rag.example (
    owner_id uuid NOT NULL,
    direction text NOT NULL CHECK(direction IN ('EXPENSE','INCOME')),
    snapshot text NOT NULL,
    category_id uuid NOT NULL,
    description text NOT NULL CHECK(length(description)<=200),
    model_key text NOT NULL,
    embedding vector(384) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(owner_id,direction,snapshot,category_id,description)
);
ALTER TABLE category_rag.example ENABLE ROW LEVEL SECURITY;
ALTER TABLE category_rag.example FORCE ROW LEVEL SECURITY;
CREATE POLICY own_examples ON category_rag.example USING(owner_id=current_setting('category_rag.owner',true)::uuid)
    WITH CHECK(owner_id=current_setting('category_rag.owner',true)::uuid);
CREATE TABLE category_rag.model_budget (
    bucket text NOT NULL,
    period timestamptz NOT NULL,
    used integer NOT NULL,
    PRIMARY KEY(bucket,period)
);
