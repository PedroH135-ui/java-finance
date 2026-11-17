-- Application rollback companion, NOT a Flyway down migration.
-- Execute only in an explicitly authorized rollback, after verifying recovery,
-- stopping additional product reads and testing against an isolated copy.
-- Preserve all V17/V18 tables, categories, corrections and reconciliation history.
-- This keeps the original V16 column shape and excludes the new CARD source.
-- A payment with reconciliation history replaces its card details in consumption;
-- unrelated, previously confirmed financial natures keep their former semantics.
CREATE OR REPLACE VIEW consumption_record AS
 SELECT f.* FROM financial_record f
 LEFT JOIN valid_financial_nature n
   ON n.entry_id=f.bank_entry_id AND n.owner_id=f.owner_id
 WHERE n.entry_id IS NULL OR n.nature='ORDINARY'
   OR (n.nature='CARD_PAYMENT' AND EXISTS(
     SELECT 1 FROM card_bill_reconciliation r
     WHERE r.entry_id=f.bank_entry_id AND r.owner_id=f.owner_id));
