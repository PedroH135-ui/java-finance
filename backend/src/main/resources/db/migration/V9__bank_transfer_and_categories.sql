ALTER TABLE bank_entry ADD CONSTRAINT bank_entry_id_owner UNIQUE(id,owner_id);
ALTER TABLE bank_entry ADD COLUMN category_id UUID;
ALTER TABLE bank_entry ADD COLUMN category_description VARCHAR(200);
ALTER TABLE bank_entry ADD COLUMN category_direction VARCHAR(6);
ALTER TABLE bank_entry ADD CONSTRAINT bank_entry_category_owner FOREIGN KEY(category_id,owner_id) REFERENCES category(id,owner_id);
CREATE TABLE bank_transfer_pair (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_user(id),
    debit_entry_id UUID NOT NULL,
    credit_entry_id UUID NOT NULL,
    amount NUMERIC(19,2) NOT NULL CHECK(amount>0),
    debit_date DATE NOT NULL,
    credit_date DATE NOT NULL,
    debit_description VARCHAR(200) NOT NULL,
    credit_description VARCHAR(200) NOT NULL,
    previous_debit_category UUID,
    previous_credit_category UUID,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE(id,owner_id),
    CHECK(debit_entry_id<>credit_entry_id),
    FOREIGN KEY(previous_debit_category,owner_id) REFERENCES category(id,owner_id),
    FOREIGN KEY(previous_credit_category,owner_id) REFERENCES category(id,owner_id),
    FOREIGN KEY(debit_entry_id,owner_id) REFERENCES bank_entry(id,owner_id),
    FOREIGN KEY(credit_entry_id,owner_id) REFERENCES bank_entry(id,owner_id)
);
CREATE UNIQUE INDEX bank_transfer_debit_active ON bank_transfer_pair(owner_id,debit_entry_id) WHERE active;
CREATE UNIQUE INDEX bank_transfer_credit_active ON bank_transfer_pair(owner_id,credit_entry_id) WHERE active;
ALTER TABLE bank_entry ADD COLUMN transfer_id UUID;
ALTER TABLE bank_entry ADD CONSTRAINT bank_entry_transfer_owner FOREIGN KEY(transfer_id,owner_id) REFERENCES bank_transfer_pair(id,owner_id);

-- If financial data or the provider description changes, it returns to the report until reconfirmed.
-- Visibility edits do not invalidate an identified transfer.
CREATE VIEW valid_bank_transfer AS
SELECT p.id,p.owner_id FROM bank_transfer_pair p
JOIN bank_entry d ON d.id=p.debit_entry_id AND d.owner_id=p.owner_id AND d.transfer_id=p.id
JOIN bank_entry c ON c.id=p.credit_entry_id AND c.owner_id=p.owner_id AND c.transfer_id=p.id
JOIN bank_link dl ON dl.id=d.link_id AND dl.owner_id=p.owner_id
JOIN bank_link cl ON cl.id=c.link_id AND cl.owner_id=p.owner_id
WHERE p.active AND d.direction='DEBIT' AND c.direction='CREDIT'
  AND d.provider_status='POSTED' AND c.provider_status='POSTED'
  AND d.movement_id IS NULL AND c.movement_id IS NULL AND dl.account_id<>cl.account_id
  AND d.amount=p.amount AND c.amount=p.amount
  AND d.date=p.debit_date AND c.date=p.credit_date
  AND d.description=p.debit_description AND c.description=p.credit_description;

CREATE OR REPLACE VIEW financial_record AS
SELECT m.id,m.owner_id,m.description,m.amount,m.kind,m.status,m.account_id,m.destination_id,m.category_id,
       m.operation_date,m.competence,m.due_date,m.settled_date,m.source,m.notes,m.version,
       b.id bank_entry_id,b.link_id bank_link_id,b.version bank_entry_version,
       b.provider_status bank_provider_status,b.state bank_state,
       EXISTS(SELECT 1 FROM bank_entry x WHERE x.owner_id=m.owner_id AND x.movement_id=m.id
              AND (x.hidden OR x.state='IGNORED')) hidden
FROM movement m
LEFT JOIN LATERAL (
    SELECT e.* FROM bank_entry e WHERE e.owner_id=m.owner_id AND e.movement_id=m.id
    ORDER BY e.hidden DESC,e.id LIMIT 1
) b ON TRUE
UNION ALL
SELECT e.id,e.owner_id,e.description,e.amount,
       CASE WHEN t.id IS NOT NULL THEN 'TRANSFER' WHEN e.direction='DEBIT' THEN 'EXPENSE' ELSE 'INCOME' END,
       CASE WHEN e.provider_status='POSTED' THEN 'CONFIRMED' ELSE 'PLANNED' END,
       l.account_id,NULL::uuid,CASE WHEN t.id IS NULL AND e.category_description=e.description AND e.category_direction=e.direction THEN e.category_id ELSE NULL::uuid END,e.date,date_trunc('month',e.date)::date,e.date,
       CASE WHEN e.provider_status='POSTED' THEN e.date ELSE NULL::date END,
       'BANK','',e.version,e.id,e.link_id,e.version,e.provider_status,e.state,(e.hidden OR e.state='IGNORED')
FROM bank_entry e
JOIN bank_link l ON l.id=e.link_id AND l.owner_id=e.owner_id
LEFT JOIN valid_bank_transfer t ON t.id=e.transfer_id AND t.owner_id=e.owner_id
WHERE e.movement_id IS NULL;
