CREATE TABLE budget_commitment (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL,
    account_id uuid NOT NULL,
    description varchar(200) NOT NULL,
    statement_description varchar(200) NOT NULL,
    due_date date NOT NULL,
    amount numeric(19,2) NOT NULL CHECK (amount > 0),
    FOREIGN KEY (account_id, owner_id) REFERENCES account(id, owner_id),
    UNIQUE (owner_id, due_date, account_id, amount, statement_description)
);
