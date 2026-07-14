#!/usr/bin/env python3
"""Sprint 3: Initialize system data for 信科公司 (Xinke Company) business management platform."""

import psycopg2

conn = psycopg2.connect(host="localhost", port=5432, dbname="sys_man", user="root", password="root")
conn.autocommit = True
cur = conn.cursor()

# ============================================================
# TASK 1: Create missing tables
# ============================================================
print("=== TASK 1: Creating tables ===")

tables_sql = """
-- DQ tables (ensure they exist)
CREATE TABLE IF NOT EXISTS ecos_dq_rule (
    id BIGSERIAL PRIMARY KEY, name VARCHAR(200), description TEXT,
    rule_type VARCHAR(50), config_json TEXT, severity VARCHAR(20),
    enabled BOOLEAN DEFAULT true, created_at TIMESTAMP DEFAULT NOW(), updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_dq_issue (
    id BIGSERIAL PRIMARY KEY, rule_id VARCHAR(50), asset_id VARCHAR(200),
    description TEXT, status VARCHAR(20) DEFAULT 'open', severity VARCHAR(20),
    created_at TIMESTAMP DEFAULT NOW(), resolved_at TIMESTAMP
);

-- Business tables
CREATE TABLE IF NOT EXISTS ecos_biz_department (
    id VARCHAR(20) PRIMARY KEY, name VARCHAR(100), manager VARCHAR(50), parent_id VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS ecos_biz_project (
    id VARCHAR(20) PRIMARY KEY, name VARCHAR(200), project_type VARCHAR(20),
    dept_id VARCHAR(20), customer_name VARCHAR(100), contract_amount DECIMAL(18,2),
    status VARCHAR(20), start_date DATE, end_date DATE, manager VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS ecos_biz_contract (
    id VARCHAR(20) PRIMARY KEY, contract_no VARCHAR(50), contract_type VARCHAR(20),
    project_id VARCHAR(20), party_name VARCHAR(100), amount DECIMAL(18,2),
    signed_date DATE, status VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS ecos_biz_metric (
    id VARCHAR(20) PRIMARY KEY, dept_id VARCHAR(20), metric_type VARCHAR(30),
    metric_value DECIMAL(18,2), target_value DECIMAL(18,2),
    metric_month VARCHAR(7), created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ecos_biz_target (
    id VARCHAR(20) PRIMARY KEY, dept_id VARCHAR(20), target_type VARCHAR(30),
    target_value DECIMAL(18,2), target_year INTEGER, created_by VARCHAR(50)
);
"""

cur.execute(tables_sql)
print("  Tables created (IF NOT EXISTS)")

# Verify tables
cur.execute("SELECT tablename FROM pg_catalog.pg_tables WHERE schemaname='public' AND tablename LIKE 'ecos_biz_%' ORDER BY tablename")
biz_tables = [r[0] for r in cur.fetchall()]
print(f"  Biz tables: {biz_tables}")


# ============================================================
# TASK 2: Insert initialization data
# ============================================================
print("\n=== TASK 2: Inserting business data ===")

# --- Departments (5) ---
depts = [
    ("jd", "机电事业部", "张伟", None),
    ("sz", "数字信息事业部", "李强", None),
    ("ex", "E行事业部", "王芳", None),
    ("cw", "财务部", "陈明", None),
    ("zh", "综合管理部", "刘洋", None),
]
for d in depts:
    cur.execute("""
        INSERT INTO ecos_biz_department (id, name, manager, parent_id)
        VALUES (%s, %s, %s, %s)
        ON CONFLICT (id) DO UPDATE SET name=EXCLUDED.name, manager=EXCLUDED.manager
    """, d)
cur.execute("SELECT COUNT(*) FROM ecos_biz_department")
print(f"  Departments: {cur.fetchone()[0]}")

# --- Projects (10) ---
projects = [
    # Production projects (5)
    ("prj-001", "湖南省政务云平台建设", "production", "sz", "湖南省政府", 28000000.00,
     "in_progress", "2024-01-15", "2025-06-30", "赵刚"),
    ("prj-002", "电网调度自动化系统", "production", "jd", "国家电网湖南公司", 15600000.00,
     "in_progress", "2024-02-01", "2025-03-31", "孙磊"),
    ("prj-003", "智慧城市大数据平台", "production", "sz", "长沙市政府", 35000000.00,
     "in_progress", "2024-03-01", "2026-02-28", "周明"),
    ("prj-004", "长沙地铁ITS系统", "production", "ex", "长沙轨道交通集团", 12000000.00,
     "in_progress", "2024-01-20", "2025-09-30", "吴杰"),
    ("prj-005", "烟草物流管理平台", "production", "ex", "湖南省烟草公司", 8900000.00,
     "completed", "2023-06-01", "2024-05-31", "郑涛"),
    # Research projects (3)
    ("prj-101", "AI智能经营分析平台", "research", "sz", None, 5000000.00,
     "in_progress", "2024-04-01", "2025-06-30", "黄伟"),
    ("prj-102", "大数据湖仓一体平台", "research", "sz", None, 8000000.00,
     "in_progress", "2024-03-15", "2025-12-31", "钱峰"),
    ("prj-103", "信创适配技术平台", "research", "sz", None, 3500000.00,
     "planning", "2024-07-01", "2025-06-30", "冯凯"),
    # Management projects (2)
    ("prj-201", "ISO27001信息安全体系认证", "management", "zh", None, 1200000.00,
     "in_progress", "2024-01-01", "2024-12-31", "刘洋"),
    ("prj-202", "CMMI5级评估认证", "management", "zh", None, 1800000.00,
     "planning", "2024-08-01", "2025-06-30", "刘洋"),
]
for p in projects:
    cur.execute("""
        INSERT INTO ecos_biz_project (id, name, project_type, dept_id, customer_name,
            contract_amount, status, start_date, end_date, manager)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (id) DO UPDATE SET name=EXCLUDED.name, project_type=EXCLUDED.project_type,
            dept_id=EXCLUDED.dept_id, status=EXCLUDED.status, manager=EXCLUDED.manager
    """, p)
cur.execute("SELECT COUNT(*) FROM ecos_biz_project")
print(f"  Projects: {cur.fetchone()[0]}")

# --- Contracts (12) ---
contracts = [
    # Income contracts (8)
    ("ctr-001", "HT-2024-001", "income", "prj-001", "湖南省政府", 28000000.00, "2024-01-10", "active"),
    ("ctr-002", "HT-2024-002", "income", "prj-002", "国家电网湖南公司", 15600000.00, "2024-01-25", "active"),
    ("ctr-003", "HT-2024-003", "income", "prj-003", "长沙市政府", 35000000.00, "2024-02-20", "active"),
    ("ctr-004", "HT-2024-004", "income", "prj-004", "长沙轨道交通集团", 12000000.00, "2024-01-15", "active"),
    ("ctr-005", "HT-2023-010", "income", "prj-005", "湖南省烟草公司", 8900000.00, "2023-05-20", "completed"),
    ("ctr-006", "HT-2024-011", "income", "prj-001", "湖南省政府", 5000000.00, "2024-04-01", "active"),
    ("ctr-007", "HT-2024-012", "income", "prj-003", "长沙市政府", 3000000.00, "2024-03-15", "active"),
    ("ctr-008", "HT-2024-013", "income", "prj-002", "国家电网湖南公司", 2000000.00, "2024-05-01", "active"),
    # Expense contracts (4)
    ("ctr-101", "CG-2024-001", "expense", "prj-001", "华为技术有限公司", 8500000.00, "2024-02-01", "active"),
    ("ctr-102", "CG-2024-002", "expense", "prj-003", "浪潮电子信息产业", 12000000.00, "2024-03-01", "active"),
    ("ctr-103", "CG-2024-003", "expense", "prj-002", "南瑞集团有限公司", 4500000.00, "2024-02-15", "active"),
    ("ctr-104", "CG-2024-004", "expense", "prj-104", "阿里云计算有限公司", 2000000.00, "2024-04-15", "active"),
]
for c in contracts:
    cur.execute("""
        INSERT INTO ecos_biz_contract (id, contract_no, contract_type, project_id, party_name,
            amount, signed_date, status)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (id) DO UPDATE SET contract_no=EXCLUDED.contract_no,
            amount=EXCLUDED.amount, status=EXCLUDED.status
    """, c)
cur.execute("SELECT COUNT(*) FROM ecos_biz_contract")
print(f"  Contracts: {cur.fetchone()[0]}")

# --- Monthly Metrics (2024年1-6月, 全公司汇总) ---
metrics = []
# Revenue (营业收入: 计量金额不含税)
revenues = [8200000, 9500000, 10200000, 11500000, 12800000, 13600000]
# Collection (回款)
collections = [6500000, 7800000, 8800000, 9200000, 10500000, 11200000]
# Cost (成本)
costs = [5800000, 6700000, 7200000, 8200000, 9000000, 9500000]
# Profit (利润 = revenue - cost)
profits = [r - c for r, c in zip(revenues, costs)]

for i, month in enumerate(["2024-01", "2024-02", "2024-03", "2024-04", "2024-05", "2024-06"]):
    ms = month.replace("-0", "-")
    type_map = {"revenue": "rev", "collection": "coll", "cost": "cost", "profit": "prf"}
    for mtype, val in [("revenue", revenues[i]), ("collection", collections[i]),
                        ("cost", costs[i]), ("profit", profits[i])]:
        metrics.append((f"met-{ms}-{type_map[mtype]}", "sz", mtype, val, None, month))

# Also per-department metrics for 2024-06
dept_metrics_06 = [
    ("met-jd-r-06", "jd", "revenue", 4200000.00, None, "2024-06"),
    ("met-sz-r-06", "sz", "revenue", 6800000.00, None, "2024-06"),
    ("met-ex-r-06", "ex", "revenue", 2600000.00, None, "2024-06"),
    ("met-jd-p-06", "jd", "profit", 1100000.00, None, "2024-06"),
    ("met-sz-p-06", "sz", "profit", 1800000.00, None, "2024-06"),
    ("met-ex-p-06", "ex", "profit", 650000.00, None, "2024-06"),
]
for m in dept_metrics_06:
    metrics.append(m)

for m in metrics:
    cur.execute("""
        INSERT INTO ecos_biz_metric (id, dept_id, metric_type, metric_value, target_value, metric_month)
        VALUES (%s, %s, %s, %s, %s, %s)
        ON CONFLICT (id) DO UPDATE SET metric_value=EXCLUDED.metric_value
    """, m)
cur.execute("SELECT COUNT(*) FROM ecos_biz_metric")
print(f"  Metrics: {cur.fetchone()[0]}")

# --- Annual Targets (2024) ---
targets = [
    ("tgt-sz-revenue", "sz", "revenue", 150000000.00, 2024, "admin"),
    ("tgt-jd-revenue", "jd", "revenue", 90000000.00, 2024, "admin"),
    ("tgt-ex-revenue", "ex", "revenue", 60000000.00, 2024, "admin"),
    ("tgt-sz-profit", "sz", "profit", 30000000.00, 2024, "admin"),
    ("tgt-jd-profit", "jd", "profit", 18000000.00, 2024, "admin"),
    ("tgt-ex-profit", "ex", "profit", 12000000.00, 2024, "admin"),
    ("tgt-sz-collection", "sz", "collection", 135000000.00, 2024, "admin"),
    ("tgt-jd-collection", "jd", "collection", 80000000.00, 2024, "admin"),
    ("tgt-ex-collection", "ex", "collection", 52000000.00, 2024, "admin"),
    ("tgt-total-revenue", "sz", "revenue_total", 300000000.00, 2024, "admin"),
    ("tgt-total-profit", "sz", "profit_total", 60000000.00, 2024, "admin"),
    ("tgt-total-collection", "sz", "collection_total", 267000000.00, 2024, "admin"),
]
for t in targets:
    cur.execute("""
        INSERT INTO ecos_biz_target (id, dept_id, target_type, target_value, target_year, created_by)
        VALUES (%s, %s, %s, %s, %s, %s)
        ON CONFLICT (id) DO UPDATE SET target_value=EXCLUDED.target_value
    """, t)
cur.execute("SELECT COUNT(*) FROM ecos_biz_target")
print(f"  Targets: {cur.fetchone()[0]}")


# ============================================================
# TASK 3: Initialize Knowledge Graph data
# ============================================================
print("\n=== TASK 3: Inserting Knowledge Graph data ===")

# Clear old KG data
cur.execute("DELETE FROM ecos_knowledge_graph_edge")
cur.execute("DELETE FROM ecos_knowledge_graph_node")

# Department nodes
dept_nodes = [
    ("kg-dept-jd", "机电事业部", "department", "负责机电工程、电网调度等业务", '{"manager":"张伟"}'),
    ("kg-dept-sz", "数字信息事业部", "department", "负责数字信息化、大数据、AI等业务", '{"manager":"李强"}'),
    ("kg-dept-ex", "E行事业部", "department", "负责ETC出行、交通ITS等业务", '{"manager":"王芳"}'),
    ("kg-dept-cw", "财务部", "department", "负责公司财务管理", '{"manager":"陈明"}'),
    ("kg-dept-zh", "综合管理部", "department", "负责行政管理、资质认证等", '{"manager":"刘洋"}'),
]
for n in dept_nodes:
    cur.execute("""
        INSERT INTO ecos_knowledge_graph_node (id, label, node_type, description, properties_json)
        VALUES (%s, %s, %s, %s, %s::jsonb)
        ON CONFLICT (id) DO UPDATE SET label=EXCLUDED.label, node_type=EXCLUDED.node_type
    """, n)

# Project nodes
project_nodes = [
    ("kg-prj-001", "湖南省政务云平台建设", "project", "生产项目-湖南省政府", '{"type":"production","dept":"数字信息事业部","amount":28000000,"status":"in_progress"}'),
    ("kg-prj-002", "电网调度自动化系统", "project", "生产项目-国家电网湖南公司", '{"type":"production","dept":"机电事业部","amount":15600000,"status":"in_progress"}'),
    ("kg-prj-003", "智慧城市大数据平台", "project", "生产项目-长沙市政府", '{"type":"production","dept":"数字信息事业部","amount":35000000,"status":"in_progress"}'),
    ("kg-prj-004", "长沙地铁ITS系统", "project", "生产项目-长沙轨道交通集团", '{"type":"production","dept":"E行事业部","amount":12000000,"status":"in_progress"}'),
    ("kg-prj-005", "烟草物流管理平台", "project", "生产项目-湖南省烟草公司", '{"type":"production","dept":"E行事业部","amount":8900000,"status":"completed"}'),
    ("kg-prj-101", "AI智能经营分析平台", "project", "科研项目-内部研发", '{"type":"research","dept":"数字信息事业部","amount":5000000,"status":"in_progress"}'),
    ("kg-prj-102", "大数据湖仓一体平台", "project", "科研项目-内部研发", '{"type":"research","dept":"数字信息事业部","amount":8000000,"status":"in_progress"}'),
    ("kg-prj-103", "信创适配技术平台", "project", "科研项目-内部研发", '{"type":"research","dept":"数字信息事业部","amount":3500000,"status":"planning"}'),
    ("kg-prj-201", "ISO27001信息安全体系认证", "project", "管理项目-信息安全", '{"type":"management","dept":"综合管理部","amount":1200000,"status":"in_progress"}'),
    ("kg-prj-202", "CMMI5级评估认证", "project", "管理项目-资质认证", '{"type":"management","dept":"综合管理部","amount":1800000,"status":"planning"}'),
]
for n in project_nodes:
    cur.execute("""
        INSERT INTO ecos_knowledge_graph_node (id, label, node_type, description, properties_json)
        VALUES (%s, %s, %s, %s, %s::jsonb)
        ON CONFLICT (id) DO UPDATE SET label=EXCLUDED.label, node_type=EXCLUDED.node_type
    """, n)

# Contract nodes
contract_nodes = [
    ("kg-ctr-001", "HT-2024-001 湖南省政务云", "contract", "收入合同-湖南省政府", '{"type":"income","party":"湖南省政府","amount":28000000}'),
    ("kg-ctr-002", "HT-2024-002 电网调度", "contract", "收入合同-国家电网湖南公司", '{"type":"income","party":"国家电网湖南公司","amount":15600000}'),
    ("kg-ctr-003", "HT-2024-003 智慧城市", "contract", "收入合同-长沙市政府", '{"type":"income","party":"长沙市政府","amount":35000000}'),
    ("kg-ctr-004", "HT-2024-004 长沙地铁ITS", "contract", "收入合同-长沙轨道交通集团", '{"type":"income","party":"长沙轨道交通集团","amount":12000000}'),
    ("kg-ctr-005", "HT-2023-010 烟草物流", "contract", "收入合同-湖南省烟草公司", '{"type":"income","party":"湖南省烟草公司","amount":8900000}'),
    ("kg-ctr-101", "CG-2024-001 华为采购", "contract", "支出合同-华为技术", '{"type":"expense","party":"华为技术有限公司","amount":8500000}'),
    ("kg-ctr-102", "CG-2024-002 浪潮采购", "contract", "支出合同-浪潮", '{"type":"expense","party":"浪潮电子信息产业","amount":12000000}'),
    ("kg-ctr-103", "CG-2024-003 南瑞采购", "contract", "支出合同-南瑞", '{"type":"expense","party":"南瑞集团有限公司","amount":4500000}'),
]
for n in contract_nodes:
    cur.execute("""
        INSERT INTO ecos_knowledge_graph_node (id, label, node_type, description, properties_json)
        VALUES (%s, %s, %s, %s, %s::jsonb)
        ON CONFLICT (id) DO UPDATE SET label=EXCLUDED.label, node_type=EXCLUDED.node_type
    """, n)

# Edges
edges = [
    ("kg-edge-001", "kg-dept-sz", "kg-prj-001", "responsible_for"),
    ("kg-edge-002", "kg-dept-jd", "kg-prj-002", "responsible_for"),
    ("kg-edge-003", "kg-dept-sz", "kg-prj-003", "responsible_for"),
    ("kg-edge-004", "kg-dept-ex", "kg-prj-004", "responsible_for"),
    ("kg-edge-005", "kg-dept-ex", "kg-prj-005", "responsible_for"),
    ("kg-edge-006", "kg-dept-sz", "kg-prj-101", "responsible_for"),
    ("kg-edge-007", "kg-dept-sz", "kg-prj-102", "responsible_for"),
    ("kg-edge-008", "kg-dept-sz", "kg-prj-103", "responsible_for"),
    ("kg-edge-009", "kg-dept-zh", "kg-prj-201", "responsible_for"),
    ("kg-edge-010", "kg-dept-zh", "kg-prj-202", "responsible_for"),
    ("kg-edge-011", "kg-prj-001", "kg-ctr-001", "has_contract"),
    ("kg-edge-012", "kg-prj-002", "kg-ctr-002", "has_contract"),
    ("kg-edge-013", "kg-prj-003", "kg-ctr-003", "has_contract"),
    ("kg-edge-014", "kg-prj-004", "kg-ctr-004", "has_contract"),
    ("kg-edge-015", "kg-prj-005", "kg-ctr-005", "has_contract"),
    ("kg-edge-016", "kg-prj-001", "kg-ctr-101", "has_expense"),
    ("kg-edge-017", "kg-prj-003", "kg-ctr-102", "has_expense"),
    ("kg-edge-018", "kg-prj-002", "kg-ctr-103", "has_expense"),
]
for e in edges:
    cur.execute("""
        INSERT INTO ecos_knowledge_graph_edge (id, source_node_id, target_node_id, relationship)
        VALUES (%s, %s, %s, %s)
        ON CONFLICT (id) DO UPDATE SET source_node_id=EXCLUDED.source_node_id, target_node_id=EXCLUDED.target_node_id
    """, e)

cur.execute("SELECT COUNT(*) FROM ecos_knowledge_graph_node")
node_cnt = cur.fetchone()[0]
cur.execute("SELECT COUNT(*) FROM ecos_knowledge_graph_edge")
edge_cnt = cur.fetchone()[0]
print(f"  KG Nodes: {node_cnt}, KG Edges: {edge_cnt}")


# ============================================================
# Verify all tables
# ============================================================
print("\n=== Verification ===")
tables_to_check = [
    "ecos_dq_rule", "ecos_dq_issue",
    "ecos_biz_department", "ecos_biz_project", "ecos_biz_contract",
    "ecos_biz_metric", "ecos_biz_target",
    "ecos_knowledge_graph_node", "ecos_knowledge_graph_edge",
]
for t in tables_to_check:
    cur.execute(f"SELECT COUNT(*) FROM {t}")
    print(f"  {t}: {cur.fetchone()[0]} rows")

cur.close()
conn.close()
print("\n=== DONE: All database initialization completed ===")
