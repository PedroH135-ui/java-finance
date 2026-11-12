ALTER TABLE bank_entry ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE bank_entry SET hidden=TRUE WHERE state='IGNORED';

-- Read projection only: no automatic movement confirmation or ledger posting.
-- A linked bank entry is represented by its existing movement, once.
CREATE VIEW financial_record AS
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
       CASE WHEN e.direction='DEBIT' THEN 'EXPENSE' ELSE 'INCOME' END,
       CASE WHEN e.provider_status='POSTED' THEN 'CONFIRMED' ELSE 'PLANNED' END,
       l.account_id,NULL::uuid,NULL::uuid,e.date,date_trunc('month',e.date)::date,e.date,
       CASE WHEN e.provider_status='POSTED' THEN e.date ELSE NULL::date END,
       'BANK','',e.version,e.id,e.link_id,e.version,e.provider_status,e.state,(e.hidden OR e.state='IGNORED')
FROM bank_entry e
JOIN bank_link l ON l.id=e.link_id AND l.owner_id=e.owner_id
WHERE e.movement_id IS NULL;
