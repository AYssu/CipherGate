-- Existing plans remain unlimited and keep their checkout price.
ALTER TABLE portal_pricing_plan
    ADD COLUMN original_price_fen BIGINT NULL,
    ADD COLUMN stock_remaining INT NULL COMMENT 'NULL=unlimited; excludes reserved and sold units',
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_portal_plan_original_price CHECK (original_price_fen IS NULL OR original_price_fen >= price_fen),
    ADD CONSTRAINT chk_portal_plan_stock CHECK (stock_remaining IS NULL OR stock_remaining >= 0);

ALTER TABLE portal_payment_order
    ADD COLUMN stock_reserved BOOLEAN NOT NULL DEFAULT FALSE,
    ADD INDEX idx_portal_order_stock (plan_id, stock_reserved, status);
