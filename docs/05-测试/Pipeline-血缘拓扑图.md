# ECOS 数据工作台 — 血缘拓扑图

> 生成时间: 2026-07-11 17:14 | 数据源: ECOS本地PG (sys_man)

---

## Pipeline A: 客户供应商数据清洗与质量检查

```
                          ┌─────────────────────┐
                          │  demo_customer (8c)  │ ← Source
                          └─────────┬───────────┘
                                    │
                          ┌─────────▼───────────┐
                          │  clean_customer      │   email→lower(trim())
                          │  (Transform)         │   phone→coalesce('N/A')
                          └─────────┬───────────┘
                                    │
                          ┌─────────▼───────────┐
                          │  quality_customer    │   ✅ not_null(customer_id)
                          │  (Quality Check)     │   ✅ uniqueness(customer_id)
                          │                      │   ⚠️  regex(email, pattern)
                          └─────────┬───────────┘
                                    │
                          ┌─────────▼───────────┐
                          │  demo_customer_clean │ ← Sink (overwrite)
                          └─────────────────────┘


                          ┌─────────────────────┐
                          │  demo_supplier (8c)  │ ← Source
                          └─────────┬───────────┘
                                    │
                          ┌─────────▼───────────┐
                          │  clean_supplier      │   status→coalesce('UNKNOWN')
                          │  (Transform)         │   rating→cast(int)
                          └─────────┬───────────┘
                                    │
                          ┌─────────▼───────────┐
                          │  quality_supplier    │   ✅ not_null(supplier_id)
                          │  (Quality Check)     │   ⚠️  range(rating, 0-5)
                          └─────────┬───────────┘
                                    │
                          ┌─────────▼───────────┐
                          │  demo_supplier_clean │ ← Sink (overwrite)
                          └─────────────────────┘
```

---

## Pipeline B: 发票合同关联聚合与质量评分

```
┌─────────────────────┐        ┌──────────────────────┐
│  demo_invoice (6c)   │        │  ecos_biz_contract    │
│  (Source)            │        │  (8c, Source)         │
└─────────┬───────────┘        └───────────┬───────────┘
          │                                │
┌─────────▼───────────┐                    │
│  filter_valid_invoice│  amount>0         │
│  (Filter)            │  notnull(amount)  │
└─────────┬───────────┘                    │
          │                                │
          └────────────┬───────────────────┘
                       │
              ┌────────▼────────┐
              │  join_invoice    │  LEFT JOIN contract_id
              │  _contract       │
              │  (Join)          │
              └────────┬────────┘
                       │
              ┌────────▼────────┐
              │  agg_by_status   │  groupBy(status)
              │  (Aggregate)     │  sum/avg/max/count(amount)
              └────────┬────────┘
                       │
              ┌────────▼────────┐
              │  calc_quality    │  quality_score = round(count*100/(count+1),2)
              │  _score          │  avg_quality_tier = case(avg>10000→A, >1000→B, C)
              │  (Transform)     │
              └────────┬────────┘
                       │
              ┌────────▼────────┐
              │  invoice_quality │ ← Sink (overwrite)
              │  _report         │
              └─────────────────┘
```

---

## 全链路拓扑汇总

```
Nodes: 18
Edges: 14

数据源 (5):  demo_customer, demo_supplier, demo_invoice, ecos_biz_contract, [lineage_node 预留]
转换 (6):    clean_customer, clean_supplier, filter_valid_invoice, calc_quality_score, [coalesce]
质量 (2):    quality_customer, quality_supplier
关联 (1):    join_invoice_contract
聚合 (1):    agg_by_status
输出 (3):    demo_customer_clean, demo_supplier_clean, invoice_quality_report
```

---

## 执行结果

| Pipeline | Run ID | 状态 | 耗时 |
|----------|--------|:--:|-----|
| 客户供应商数据清洗与质量检查 | ce0232dc-3e02-4d32-8fc3-4abea5a78806 | ✅ SUCCEEDED | 62ms |
| 发票合同关联聚合与质量评分 | 61f495e8-0503-4d11-a073-eb1499570657 | ✅ SUCCEEDED | — |

---

## Git 版本记录

```
/tmp/ecos-git/
├── .ecos-pipeline-meta.json
├── pipelines/
│   ├── pl-clean-check/
│   │   ├── pipeline.yaml          (2.2 KB, 8 nodes)
│   │   ├── expressions/
│   │   └── config/
│   ├── pl-agg-score/
│   │   ├── pipeline.yaml          (1.9 KB, 6 nodes)
│   │   ├── expressions/
│   │   └── config/
└── shared/
    ├── transforms/
    └── udfs/

Commits:
  d40eac9 feat: 客户供应商数据清洗 Pipeline v1 + 发票合同聚合评分 Pipeline v1
  41d8833 feat: Pipeline v1 客户供应商数据清洗与质量检查
  5bfda0c feat: Pipeline v1 发票合同关联聚合与质量评分
```
