-- Persist explicit corrections independently of the bounded retrieval history.
CREATE TABLE category_correction (
    owner_id uuid NOT NULL REFERENCES app_user(id),
    direction varchar(6) NOT NULL CHECK(direction IN ('DEBIT','CREDIT')),
    description_key text NOT NULL CHECK(length(description_key)<=200),
    category_id uuid NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(owner_id,direction,description_key),
    FOREIGN KEY(category_id,owner_id) REFERENCES category(id,owner_id)
);

INSERT INTO category_correction(owner_id,direction,description_key,category_id)
SELECT DISTINCT ON (b.owner_id,b.direction,lower(regexp_replace(btrim(b.description),'\s+',' ','g')))
    b.owner_id,b.direction,lower(regexp_replace(btrim(b.description),'\s+',' ','g')),b.category_id
FROM bank_entry b JOIN category c ON c.id=b.category_id AND c.owner_id=b.owner_id
LEFT JOIN LATERAL (
    SELECT max(created_at) corrected_at FROM audit_event
    WHERE owner_id=b.owner_id AND resource_id=b.id AND action='BANK_CATEGORY_CHANGED'
) a ON true
WHERE b.category_source='MANUAL' AND b.category_id IS NOT NULL
  AND b.category_description=b.description AND b.category_direction=b.direction
  AND NOT c.archived AND c.kind=CASE WHEN b.direction='DEBIT' THEN 'EXPENSE' ELSE 'INCOME' END
ORDER BY b.owner_id,b.direction,lower(regexp_replace(btrim(b.description),'\s+',' ','g')),
    a.corrected_at DESC NULLS LAST,b.date DESC,b.id;
