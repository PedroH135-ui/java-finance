-- Bank snapshots preserve the signed numeric value; internal ledger money stays unchanged.
-- Recreate dependent views atomically. Never use CASCADE or rewrite existing rows.
DROP VIEW consumption_record;
DROP VIEW valid_card_reconciliation;
DROP VIEW card_bill_snapshot;
ALTER TABLE bank_card_bill DROP CONSTRAINT bank_card_bill_total_check;
ALTER TABLE bank_card_bill ALTER COLUMN total TYPE numeric;
ALTER TABLE bank_card_bill ADD CONSTRAINT bank_card_bill_total_check
 CHECK(total BETWEEN -999999999999999.99 AND 999999999999999.99 AND scale(total)<=64);

CREATE VIEW card_bill_snapshot AS
 SELECT b.id,b.owner_id,b.card_id,b.external_id,b.due_date,b.total,
   md5(concat_ws('|',b.total::text,b.due_date::text,b.present::text,c.present::text,c.bills_available::text,
     coalesce(string_agg(concat_ws('|',x.id::text,x.description,x.amount::text,x.date::text,x.direction,x.provider_status,x.present::text),'#' ORDER BY x.id),''))) signature,
   coalesce(sum(CASE WHEN x.direction='DEBIT' THEN x.amount ELSE -x.amount END) FILTER(WHERE x.present),0)::numeric(19,2) detailed_total,
   count(x.id) FILTER(WHERE x.present) detail_count,
   count(x.id) FILTER(WHERE x.present AND x.provider_status<>'POSTED') pending_count,
   (b.present AND c.present AND c.bills_available) available
 FROM bank_card_bill b JOIN bank_card c ON c.id=b.card_id AND c.owner_id=b.owner_id
 LEFT JOIN bank_card_charge x ON x.card_id=b.card_id AND x.owner_id=b.owner_id AND x.external_bill_id=b.external_id
 GROUP BY b.id,c.id;

CREATE VIEW valid_card_reconciliation AS
 SELECT r.bill_id,r.owner_id,r.entry_id FROM card_bill_reconciliation r
 JOIN card_bill_snapshot s ON s.id=r.bill_id AND s.owner_id=r.owner_id AND s.signature=r.bill_signature
 JOIN bank_entry e ON e.id=r.entry_id AND e.owner_id=r.owner_id
 JOIN valid_financial_nature n ON n.entry_id=e.id AND n.owner_id=e.owner_id AND n.nature='CARD_PAYMENT'
 WHERE s.total>0 AND s.total=trunc(s.total,2) AND r.active AND s.available AND s.detail_count>0 AND s.pending_count=0 AND s.detailed_total=s.total
 AND e.amount=s.total AND NOT e.hidden AND e.state<>'IGNORED' AND e.direction='DEBIT' AND e.transfer_id IS NULL;

-- New card details affect consumption only after explicit, exact reconciliation.
-- Cash flow, financial_record and account/posting balances remain unchanged.
CREATE VIEW consumption_record AS
 SELECT f.* FROM financial_record f LEFT JOIN valid_financial_nature n ON n.entry_id=f.bank_entry_id AND n.owner_id=f.owner_id
 WHERE n.entry_id IS NULL OR n.nature='ORDINARY'
   OR (n.nature='CARD_PAYMENT' AND EXISTS(SELECT 1 FROM card_bill_reconciliation r WHERE r.entry_id=f.bank_entry_id AND r.owner_id=f.owner_id)
       AND NOT EXISTS(SELECT 1 FROM valid_card_reconciliation v WHERE v.entry_id=f.bank_entry_id AND v.owner_id=f.owner_id))
 UNION ALL
 SELECT x.id,x.owner_id,x.description,(CASE WHEN x.direction='DEBIT' THEN x.amount ELSE -x.amount END)::numeric(19,2),
   'EXPENSE','CONFIRMED',l.account_id,NULL::uuid,
   CASE WHEN x.category_description=x.description AND x.category_direction=x.direction THEN x.category_id ELSE NULL::uuid END,
   x.date,date_trunc('month',b.due_date)::date,b.due_date,b.due_date,'CARD','Detalhe de fatura conciliada',x.version,
   NULL::uuid,NULL::uuid,NULL::bigint,NULL::varchar(10),NULL::varchar(12),false
 FROM bank_card_charge x JOIN bank_card_bill b ON b.card_id=x.card_id AND b.owner_id=x.owner_id AND b.external_id=x.external_bill_id
 JOIN valid_card_reconciliation r ON r.bill_id=b.id AND r.owner_id=b.owner_id
 JOIN bank_entry e ON e.id=r.entry_id AND e.owner_id=r.owner_id
 JOIN bank_link l ON l.id=e.link_id AND l.owner_id=e.owner_id
 WHERE x.present AND x.provider_status='POSTED';
