-- Additive metadata only. No movement, posting, balance, category choice or transfer is created.
ALTER TABLE bank_entry ADD COLUMN category_source VARCHAR(6) CHECK (category_source IN ('MANUAL','AUTO'));
ALTER TABLE bank_entry ADD COLUMN category_auto_blocked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE bank_entry ADD COLUMN category_auto_attempted_at TIMESTAMPTZ;
ALTER TABLE bank_entry ADD COLUMN category_auto_method VARCHAR(7) CHECK (category_auto_method IN ('HISTORY','RULE','LLM'));
ALTER TABLE bank_entry ADD COLUMN category_auto_confidence VARCHAR(6) CHECK (category_auto_confidence IN ('HIGH','MEDIUM','LOW'));
-- Existing choices predate automation and belong to the user.
UPDATE bank_entry SET category_source='MANUAL',category_auto_blocked=TRUE WHERE category_id IS NOT NULL;
-- Preserve explicit removals made before this feature existed as well.
UPDATE bank_entry e SET category_source='MANUAL',category_auto_blocked=TRUE
WHERE e.category_id IS NULL AND EXISTS (
    SELECT 1 FROM audit_event a WHERE a.owner_id=e.owner_id AND a.resource_id=e.id AND a.action='BANK_CATEGORY_CHANGED'
);
