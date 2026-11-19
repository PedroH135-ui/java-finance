-- A later transfer pairing/cancellation invalidates an earlier independent nature confirmation.
-- Keep V14 immutable: it has already been applied to the local fictitious database.
CREATE OR REPLACE VIEW valid_financial_nature AS
 SELECT n.* FROM bank_financial_nature n JOIN bank_entry b ON b.id=n.entry_id AND b.owner_id=n.owner_id
 WHERE n.entry_signature=md5(concat_ws('|',b.description,b.amount::text,b.date::text,b.direction,b.provider_status))
 AND b.provider_status='POSTED' AND b.movement_id IS NULL AND b.transfer_id IS NULL AND b.state<>'IGNORED';
