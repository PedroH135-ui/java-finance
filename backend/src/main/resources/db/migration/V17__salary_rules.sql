-- User instructions only. No existing preferences, classifications or financial data are changed.
ALTER TABLE bank_entry ADD COLUMN first_seen_at timestamptz;
ALTER TABLE bank_entry ALTER COLUMN first_seen_at SET DEFAULT now();
CREATE TABLE salary_rule (
 id uuid PRIMARY KEY, owner_id uuid NOT NULL REFERENCES app_user(id), link_id uuid NOT NULL,
 description_key varchar(200) NOT NULL, description varchar(200) NOT NULL,
 category_id uuid NOT NULL, starts_on date NOT NULL, active boolean NOT NULL DEFAULT true,
 version bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL DEFAULT now(),effective_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(owner_id,link_id,description_key),
 FOREIGN KEY(link_id,owner_id) REFERENCES bank_link(id,owner_id),
 FOREIGN KEY(category_id,owner_id) REFERENCES category(id,owner_id)
);
ALTER TABLE bank_entry DROP CONSTRAINT bank_entry_category_source_check;
ALTER TABLE bank_entry ADD CONSTRAINT bank_entry_category_source_check CHECK(category_source IN ('MANUAL','AUTO','RULE'));
