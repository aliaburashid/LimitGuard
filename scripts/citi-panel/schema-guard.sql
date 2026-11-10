CREATE TEMP TABLE approved_foreign_keys (child text, parent text, name text, definition text);
INSERT INTO approved_foreign_keys VALUES
    ('users', 'financial_institutions', 'fk78e95a4vh0d0b9fru9k8vrb8u', 'FOREIGN KEY (financial_institution_id) REFERENCES financial_institutions(id)'),
    ('audit_logs', 'financial_institutions', 'fk37kqs723fn382ks5ywvh5usck', 'FOREIGN KEY (financial_institution_id) REFERENCES financial_institutions(id)'),
    ('credit_limits', 'financial_institutions', 'fkmhtxffdm01prtimogragrutkw', 'FOREIGN KEY (financial_institution_id) REFERENCES financial_institutions(id)'),
    ('group_credit_limits', 'financial_institutions', 'fk6k35o89ep7814t2ay6ma1ubb', 'FOREIGN KEY (financial_institution_id) REFERENCES financial_institutions(id)'),
    ('user_tokens', 'users', 'fk61iiu6gfevpvo2v3yl76sar7r', 'FOREIGN KEY (user_id) REFERENCES users(id)'),
    ('audit_logs', 'users', 'fkf2uqqjmo7gvd8fq7ac81fgc0m', 'FOREIGN KEY (actor_id) REFERENCES users(id)'),
    ('credit_requests', 'users', 'fksx2gpqlb2rokcneyg5suafi9j', 'FOREIGN KEY (requester_id) REFERENCES users(id)'),
    ('approval_decisions', 'users', 'fkemds3a5ywqo8ufkc9abo8fm38', 'FOREIGN KEY (decided_by_id) REFERENCES users(id)'),
    ('email_verification_codes', 'users', 'fkpjww6kxejvkaaivcg6vv7r9w9', 'FOREIGN KEY (user_id) REFERENCES users(id)'),
    ('password_reset_codes', 'users', 'fkke97isexxbl8lf7yibo6q02ir', 'FOREIGN KEY (user_id) REFERENCES users(id)'),
    ('credit_limits', 'counterparties', 'fkq2n70wqbedqdhgh4uyomexk74', 'FOREIGN KEY (counterparty_id) REFERENCES counterparties(id)'),
    ('corporate_group_memberships', 'counterparties', 'fk6t4brt1gv839hckgbr4f96ha2', 'FOREIGN KEY (counterparty_id) REFERENCES counterparties(id)'),
    ('counterparty_financial_profiles', 'counterparties', 'fkcxqbucjrqmi3syy1et2aukcrq', 'FOREIGN KEY (counterparty_id) REFERENCES counterparties(id)'),
    ('credit_requests', 'credit_limits', 'fk5dw2jjua6398b1nkmvjptw0pc', 'FOREIGN KEY (credit_limit_id) REFERENCES credit_limits(id)'),
    ('approval_decisions', 'credit_requests', 'fk3q583jn3qhfpfk4frs05179wk', 'FOREIGN KEY (credit_request_id) REFERENCES credit_requests(id)'),
    ('corporate_group_memberships', 'corporate_groups', 'fk803g1sl1aqa8thtqoonk2xivq', 'FOREIGN KEY (corporate_group_id) REFERENCES corporate_groups(id)'),
    ('group_credit_limits', 'corporate_groups', 'fkg95eavw937vuw7so4lapyl2t', 'FOREIGN KEY (corporate_group_id) REFERENCES corporate_groups(id)');
CREATE TEMP TABLE current_foreign_keys AS
SELECT conrelid::regclass::text AS child, confrelid::regclass::text AS parent,
       conname::text AS name, pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE contype = 'f'
  AND (connamespace = 'public'::regnamespace
       OR confrelid IN (SELECT oid FROM pg_class WHERE relnamespace = 'public'::regnamespace));

SELECT pg_temp.require(NOT EXISTS (TABLE current_foreign_keys EXCEPT TABLE approved_foreign_keys)
    AND NOT EXISTS (TABLE approved_foreign_keys EXCEPT TABLE current_foreign_keys),
    'The live foreign-key dependencies changed. Request a fresh approval.');
SELECT pg_temp.require(NOT EXISTS (
    SELECT 1 FROM pg_trigger trigger
    JOIN pg_class relation ON relation.oid = trigger.tgrelid
    WHERE NOT trigger.tgisinternal AND relation.relnamespace = 'public'::regnamespace
      AND relation.relname IN (SELECT unnest(pg_temp.reset_tables()))
), 'An unexpected database trigger could change protected data.');
