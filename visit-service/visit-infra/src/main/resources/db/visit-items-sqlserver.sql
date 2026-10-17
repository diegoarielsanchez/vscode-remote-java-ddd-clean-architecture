-- Visit and visit plan items (product + quantity) for visit-service (SQL Server).
-- Required before deploying with SPRING_PROFILES_ACTIVE=prod (ddl-auto=validate); dev creates
-- these automatically (ddl-auto=update). Idempotent: safe to run more than once.

IF OBJECT_ID(N'dbo.visit_item', N'U') IS NULL
    CREATE TABLE dbo.visit_item (
        visit_id   VARCHAR(255) NOT NULL,  -- visits.id
        product_id VARCHAR(36)  NOT NULL,  -- product-catalog-service product id
        quantity   INT          NOT NULL,  -- units handed out, 1..1000
        CONSTRAINT fk_visit_item_visit FOREIGN KEY (visit_id) REFERENCES dbo.visits (id)
    );

IF OBJECT_ID(N'dbo.visit_plan_item', N'U') IS NULL
    CREATE TABLE dbo.visit_plan_item (
        visit_plan_id VARCHAR(255) NOT NULL,  -- visit_plans.id
        product_id    VARCHAR(36)  NOT NULL,  -- product-catalog-service product id
        quantity      INT          NOT NULL,  -- units to bring, 1..1000
        CONSTRAINT fk_visit_plan_item_visit_plan FOREIGN KEY (visit_plan_id) REFERENCES dbo.visit_plans (id)
    );

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_visit_item_visit_id')
    CREATE INDEX ix_visit_item_visit_id ON dbo.visit_item (visit_id);

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = N'ix_visit_plan_item_visit_plan_id')
    CREATE INDEX ix_visit_plan_item_visit_plan_id ON dbo.visit_plan_item (visit_plan_id);
