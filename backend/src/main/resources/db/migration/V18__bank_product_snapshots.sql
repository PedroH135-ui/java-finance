-- Optional cached provider data only; no existing ledger or classifications are changed.
CREATE FUNCTION financial_description_terms(value text) RETURNS text[]
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE AS $$
 SELECT regexp_split_to_array(trim(regexp_replace(translate(lower(value),'áàâãäéèêëíìîïóòôõöúùûüç','aaaaaeeeeiiiiooooouuuuc'),'[^a-z0-9]+',' ','g')),' +')
$$;
CREATE TABLE bank_product_sync (
 owner_id uuid NOT NULL REFERENCES app_user(id), item_id uuid NOT NULL,
 automatic boolean NOT NULL DEFAULT false, cards_status varchar(30) NOT NULL DEFAULT 'NOT_READ',
 investments_status varchar(30) NOT NULL DEFAULT 'NOT_READ', last_attempt_at timestamptz,lease_until timestamptz,
 cards_checked_at timestamptz, investments_checked_at timestamptz,
 PRIMARY KEY(owner_id,item_id)
);
CREATE TABLE bank_card (
 id uuid PRIMARY KEY,owner_id uuid NOT NULL REFERENCES app_user(id),item_id uuid NOT NULL,external_id uuid NOT NULL,
 name varchar(100) NOT NULL,bills_available boolean NOT NULL,present boolean NOT NULL DEFAULT true,checked_at timestamptz NOT NULL,
 UNIQUE(owner_id,item_id,external_id),UNIQUE(id,owner_id)
);
CREATE TABLE bank_card_bill (
 id uuid PRIMARY KEY,owner_id uuid NOT NULL,card_id uuid NOT NULL,external_id varchar(200) NOT NULL,
 due_date date NOT NULL,total numeric(19,2) NOT NULL CHECK(total>=0),present boolean NOT NULL DEFAULT true,
 UNIQUE(card_id,external_id),UNIQUE(id,owner_id),FOREIGN KEY(card_id,owner_id) REFERENCES bank_card(id,owner_id)
);
CREATE TABLE bank_card_charge (
 id uuid PRIMARY KEY,owner_id uuid NOT NULL,card_id uuid NOT NULL,external_id varchar(200) NOT NULL,
 description varchar(200) NOT NULL,amount numeric(19,2) NOT NULL CHECK(amount>0),date date NOT NULL,
 direction varchar(6) NOT NULL CHECK(direction IN ('DEBIT','CREDIT')),provider_status varchar(7) NOT NULL CHECK(provider_status IN ('POSTED','PENDING')),
 external_bill_id varchar(200),installment integer,total_installments integer,
 category_id uuid,category_description varchar(200),category_direction varchar(6),present boolean NOT NULL DEFAULT true,version bigint NOT NULL DEFAULT 0,
 category_source varchar(6) CHECK(category_source IN ('MANUAL','AUTO')),category_auto_blocked boolean NOT NULL DEFAULT false,
 category_auto_method varchar(7),category_auto_confidence varchar(6),category_auto_resolution varchar(20),category_auto_attempted_at timestamptz,
 UNIQUE(card_id,external_id),UNIQUE(id,owner_id),
 FOREIGN KEY(card_id,owner_id) REFERENCES bank_card(id,owner_id),FOREIGN KEY(category_id,owner_id) REFERENCES category(id,owner_id)
);
CREATE INDEX bank_card_charge_bill ON bank_card_charge(owner_id,card_id,external_bill_id);
CREATE INDEX bank_card_charge_owner_date ON bank_card_charge(owner_id,date DESC,id);
CREATE INDEX bank_card_bill_owner ON bank_card_bill(owner_id,card_id,due_date DESC);
CREATE TABLE bank_investment (
 id uuid PRIMARY KEY,owner_id uuid NOT NULL REFERENCES app_user(id),item_id uuid NOT NULL,external_id uuid NOT NULL,
 name varchar(100) NOT NULL,type varchar(40) NOT NULL,subtype varchar(60) NOT NULL,
 balance numeric(19,2) NOT NULL,value_date date,profit numeric(19,2),available numeric(19,2),
 status varchar(30) NOT NULL,present boolean NOT NULL DEFAULT true,checked_at timestamptz NOT NULL,
 UNIQUE(owner_id,item_id,external_id)
);

CREATE TABLE card_bill_reconciliation (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),bill_id uuid NOT NULL,owner_id uuid NOT NULL,entry_id uuid NOT NULL,
 bill_signature varchar(32) NOT NULL,active boolean NOT NULL DEFAULT true,
 confirmed_at timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(bill_id,owner_id) REFERENCES bank_card_bill(id,owner_id),
 FOREIGN KEY(entry_id,owner_id) REFERENCES bank_entry(id,owner_id)
);
CREATE UNIQUE INDEX card_reconciliation_active_bill ON card_bill_reconciliation(owner_id,bill_id) WHERE active;
CREATE UNIQUE INDEX card_reconciliation_active_entry ON card_bill_reconciliation(owner_id,entry_id) WHERE active;

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
 WHERE r.active AND s.available AND s.detail_count>0 AND s.pending_count=0 AND s.detailed_total=s.total
 AND e.amount=s.total AND NOT e.hidden AND e.state<>'IGNORED' AND e.direction='DEBIT' AND e.transfer_id IS NULL;

-- New card details affect consumption only after explicit, exact reconciliation.
-- Cash flow, financial_record and account/posting balances remain unchanged.
CREATE OR REPLACE VIEW consumption_record AS
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
