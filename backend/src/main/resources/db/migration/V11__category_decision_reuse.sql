-- Only expands metadata provenance; no financial data or category choice is changed.
ALTER TABLE bank_entry DROP CONSTRAINT bank_entry_category_auto_method_check;
ALTER TABLE bank_entry ADD CONSTRAINT bank_entry_category_auto_method_check
    CHECK (category_auto_method IN ('HISTORY','RULE','LLM','MEMORY'));
