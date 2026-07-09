ALTER TABLE invoices
    ADD COLUMN payment_milestone_key VARCHAR(100);

CREATE INDEX idx_invoices_payment_milestone_key ON invoices (payment_milestone_key);