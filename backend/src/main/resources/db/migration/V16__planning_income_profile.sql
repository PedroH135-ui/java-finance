-- Informative planning inputs only. No account, movement, posting or bank balance writes.
CREATE TABLE planning_income_profile (
 owner_id uuid PRIMARY KEY REFERENCES app_user(id),
 net_salary numeric(19,2) CHECK(net_salary>=0),
 monthly_expenses numeric(19,2) CHECK(monthly_expenses>=0),
 version bigint NOT NULL DEFAULT 0,
 updated_at timestamptz NOT NULL DEFAULT now()
);
