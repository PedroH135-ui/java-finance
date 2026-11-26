ALTER TABLE budget_commitment ADD COLUMN monthly boolean NOT NULL DEFAULT false;
ALTER TABLE savings_goal ADD CONSTRAINT savings_goal_owner_key UNIQUE (id,owner_id);
ALTER TABLE bank_investment ADD COLUMN goal_id uuid,
 ADD CONSTRAINT investment_goal_owner FOREIGN KEY (goal_id,owner_id) REFERENCES savings_goal(id,owner_id);
CREATE UNIQUE INDEX bank_investment_goal ON bank_investment(goal_id) WHERE goal_id IS NOT NULL;
