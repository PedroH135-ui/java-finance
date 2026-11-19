-- Additive organization only. No ledger, balance, import, consent or existing choice is changed.
ALTER TABLE bank_entry ADD COLUMN counterparty_type varchar(8) NOT NULL DEFAULT 'UNKNOWN'
 CHECK(counterparty_type IN ('PERSON','BUSINESS','UNKNOWN'));
ALTER TABLE bank_entry DROP CONSTRAINT bank_entry_category_auto_method_check;
ALTER TABLE bank_entry ADD CONSTRAINT bank_entry_category_auto_method_check CHECK(category_auto_method IN ('HISTORY','RULE','LLM','MEMORY','RAG'));
ALTER TABLE bank_entry ADD COLUMN category_auto_resolution varchar(20) CHECK(category_auto_resolution IN ('MATCHED','NO_CATEGORY'));

CREATE TABLE financial_knowledge (
 id varchar(40) PRIMARY KEY, match_text varchar(100) NOT NULL, direction varchar(6) NOT NULL CHECK(direction IN ('DEBIT','CREDIT')),
 nature varchar(20) NOT NULL CHECK(nature IN ('INVESTMENT_IN','INVESTMENT_OUT','CARD_PAYMENT')),
 instrument varchar(20) NOT NULL, explanation varchar(500) NOT NULL, source_url varchar(250) NOT NULL,
 revision integer NOT NULL DEFAULT 1, active boolean NOT NULL DEFAULT true
);
INSERT INTO financial_knowledge(id,match_text,direction,nature,instrument,explanation,source_url) VALUES
 ('RDB_APPLICATION','aplicacao rdb','DEBIT','INVESTMENT_IN','RDB','RDB é renda fixa. No Nubank pode compor uma Caixinha; confirme o destino. A regra vale para RDB em outros bancos, sem presumir liquidez ou rendimento.','https://nubank.com.br/nu/imposto-de-renda/como-declarar-caixinhas-nubank'),
 ('RDB_REDEMPTION','resgate rdb','CREDIT','INVESTMENT_OUT','RDB','Resgate de RDB é retirada de investimento; o valor pode incluir principal e rendimentos, não separar sem extrato.','https://www.bcb.gov.br/content/cidadaniafinanceira/documentos_cidadania/Informacoes_gerais/glossario_cidadania_financeira.pdf'),
 ('CDB_APPLICATION','aplicacao cdb','DEBIT','INVESTMENT_IN','CDB','Aplicação em CDB, investimento de renda fixa; confirme a natureza e o produto.','https://www.bcb.gov.br/content/cidadaniafinanceira/documentos_cidadania/Informacoes_gerais/glossario_cidadania_financeira.pdf'),
 ('CDB_REDEMPTION','resgate cdb','CREDIT','INVESTMENT_OUT','CDB','Resgate de CDB; não presumimos que todo o valor seja rendimento.','https://www.bcb.gov.br/content/cidadaniafinanceira/documentos_cidadania/Informacoes_gerais/glossario_cidadania_financeira.pdf'),
 ('CARD_PAYMENT','pagamento de fatura','DEBIT','CARD_PAYMENT','NONE','Pode representar quitação de cartão, não uma nova compra. Confirme antes de excluir dos indicadores de consumo.','https://www.bcb.gov.br/cidadaniafinanceira');

CREATE TABLE investment_position (
 id uuid PRIMARY KEY, owner_id uuid NOT NULL REFERENCES app_user(id), name varchar(100) NOT NULL,
 institution varchar(100) NOT NULL, instrument varchar(20) NOT NULL CHECK(instrument IN ('RDB','CDB','OTHER')),
 reported_value numeric(19,2) NOT NULL CHECK(reported_value>=0), value_date date NOT NULL, version bigint NOT NULL DEFAULT 0,
 UNIQUE(id,owner_id)
);
CREATE TABLE bank_financial_nature (
 entry_id uuid PRIMARY KEY, owner_id uuid NOT NULL,
 nature varchar(20) NOT NULL CHECK(nature IN ('ORDINARY','OWN_TRANSFER','CARD_PAYMENT','INVESTMENT_IN','INVESTMENT_OUT')),
 instrument varchar(20) NOT NULL CHECK(instrument IN ('NONE','RDB','CDB','OTHER')),
 position_id uuid, entry_signature varchar(32) NOT NULL, confirmed_at timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(entry_id,owner_id) REFERENCES bank_entry(id,owner_id),
 FOREIGN KEY(position_id,owner_id) REFERENCES investment_position(id,owner_id)
);
CREATE VIEW valid_financial_nature AS
 SELECT n.* FROM bank_financial_nature n JOIN bank_entry b ON b.id=n.entry_id AND b.owner_id=n.owner_id
 WHERE n.entry_signature=md5(concat_ws('|',b.description,b.amount::text,b.date::text,b.direction,b.provider_status))
 AND b.provider_status='POSTED' AND b.movement_id IS NULL;
-- Consumption indicators use this projection; cash flow and the transaction list keep financial_record.
CREATE VIEW consumption_record AS
 SELECT f.* FROM financial_record f LEFT JOIN valid_financial_nature n ON n.entry_id=f.bank_entry_id AND n.owner_id=f.owner_id
 WHERE n.entry_id IS NULL OR n.nature='ORDINARY';

CREATE TABLE savings_goal (
 id uuid PRIMARY KEY, owner_id uuid NOT NULL REFERENCES app_user(id), title varchar(100) NOT NULL,
 target numeric(19,2) NOT NULL CHECK(target>0), saved numeric(19,2) NOT NULL CHECK(saved>=0),
 months integer NOT NULL CHECK(months BETWEEN 1 AND 120), created_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0
);
