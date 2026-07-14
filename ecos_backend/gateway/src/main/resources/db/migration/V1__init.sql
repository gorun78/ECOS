-- ============================================================
-- V1__init.sql — 初始化演示表 (demo_customer, demo_supplier, demo_invoice)
-- ============================================================

CREATE TABLE IF NOT EXISTS demo_customer (
    id          VARCHAR(64) PRIMARY KEY,
    name        VARCHAR(255),
    industry    VARCHAR(128),
    region      VARCHAR(64),
    level       VARCHAR(16),
    credit_score INTEGER,
    status      VARCHAR(32) DEFAULT 'active',
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS demo_supplier (
    id              VARCHAR(64) PRIMARY KEY,
    name            VARCHAR(255),
    industry        VARCHAR(128),
    region          VARCHAR(64),
    level           VARCHAR(16),
    supply_capacity VARCHAR(128),
    status          VARCHAR(32) DEFAULT 'active',
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS demo_invoice (
    id          VARCHAR(64) PRIMARY KEY,
    amount      DECIMAL(12,2),
    customer_id VARCHAR(64),
    supplier_id VARCHAR(64),
    status      VARCHAR(32) DEFAULT 'pending',
    created_at  TIMESTAMP DEFAULT NOW()
);

-- Seed data (only insert if empty)
INSERT INTO demo_customer (id, name, industry, region, level, credit_score)
SELECT * FROM (VALUES
    ('c001','华为技术有限公司','通信','华南','VIP',95),
    ('c002','宝钢集团','钢铁','华东','A',88),
    ('c003','阿里巴巴集团','互联网','华东','VIP',92),
    ('c004','比亚迪股份','新能源','华南','A',85),
    ('c005','天元建设','建筑','华中','B',62)
) AS t
WHERE NOT EXISTS (SELECT 1 FROM demo_customer);

INSERT INTO demo_supplier (id, name, industry, region, level)
SELECT * FROM (VALUES
    ('s001','英特尔中国','半导体','华东','VIP'),
    ('s002','中芯国际','半导体','华东','A'),
    ('s003','中建三局','建筑','华中','A')
) AS t
WHERE NOT EXISTS (SELECT 1 FROM demo_supplier);
