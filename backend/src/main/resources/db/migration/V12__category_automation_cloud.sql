-- Opt-in only. No financial view, posting, movement or balance is changed.
CREATE TABLE category_automation_preference (
    owner_id uuid PRIMARY KEY REFERENCES app_user(id),
    enabled boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE category_automation_job (
    owner_id uuid PRIMARY KEY REFERENCES app_user(id),
    requested_version bigint NOT NULL DEFAULT 1,
    completed_version bigint NOT NULL DEFAULT 0,
    lease_token uuid,
    lease_until timestamptz,
    attempts integer NOT NULL DEFAULT 0,
    last_run timestamptz,
    CHECK (completed_version <= requested_version)
);
