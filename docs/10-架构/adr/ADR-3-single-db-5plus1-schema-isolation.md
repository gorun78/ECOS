# ADR-3 — Data Isolation: 单库 5+1 Schema（权威口径）

> 来源: 需求检视报告 §十二 Q6（用户批准 2026-09-28） | 日期: 2026-09-28 | 责任人: AI Agent
> 版本: v2.0（v1.x 为 `docs/40-实现/legacy-plans/current-plan.md` 内联表述，从未成为独立文件；本文件为其唯一权威载体）
> 裁决: **Accepted（v2.0 修订版）**
> 上游: 架构铁律 v2.0 §3.1 / §3.5 + 《数据库访问规范》v1.2 §〇/§二附 ST07~ST09/§三 + 《数据湖存储分层规范》v2.0 §〇/§七A
> 状态: 生效，全工程强制

---

## 1. 背景（Context）

ADR-3 自提出起从未独立成文，其表述在《ARCHITECTURE-RULES.md:80》与 `PRD-00:247` 中被引用为「standard 档共享 PG + Schema 隔离；**enterprise/ultimate 档每 service 独立 PG database**」。

2026-09-28 用户批准《数据库访问规范》v1.1 立新红线 **ST07：控制域 = 5 引擎 schema + 主控制 schema（单库内）**，同日实测盘点确认现网为 **1 库（`sys_man`）× 16 schema × 665 表**。两者并存产生硬冲突：

| 冲突点 | ADR-3 v1.x | ST07 v1.1 |
|:--|:--|:--|
| 隔离单位 | database（按 service） | schema（按**引擎**，仅 5 个 + 主控制） |
| 引擎 schema 数量 | 未定义（历史按 service 建了 5 个：`ecos_sysman/datanet/buszhi/dccheng/aiming`） | 严格 5 个（`ecos_data/ontology/knowledge/ai/cognitive`） |
| 现网事实 | 不符（无按 service 分库） | 相符（`ecos_data` 等已存在且被使用） |

不裁决则：设计文档的 JDBC URL、迁移序列、备份策略、`currentSchema` 配置全部无法定稿（PRD-01 与部分引擎 AGENTS.md 仍写"Phase 4-2 按 service 切流"）。

## 2. 决策（Decision）

1. **权威口径 = 单库 5+1 schema**：一个业务库内含 5 引擎 schema（`ecos_data` / `ecos_ontology` / `ecos_knowledge` / `ecos_ai` / `ecos_cognitive`）+ 1 主控制 schema（现基线 `public`，目标 `ecos_control`，见 ADR-12），**三档同构**。
2. **业务域数据不进控制域**：五层数据（SOURCE/RAW/CURATED/SEMANTIC/APPLICATION）落业务域载体——PG `ecos_dw` / MinIO / Doris∨ClickHouse / 向量载体（ST08 + 湖规 v2.0 §〇）。
3. **"每 service 独立 database" 降级为可选部署形态**：仅在甲方有物理隔离合规要求时启用；启用时**库内仍遵守 5+1 划分**，且该 service 的库内**只出现它自己拥有的引擎 schema**（sysman/workspace 用主控制 schema）。
4. **knownLegacy schema 只停写不迁移不删除**：`ecos_security`(53) / `ecos_sysman`(20) / `ecos_infra`(40) / `ecos_dq`(20) / `ecos_demo`(208) 及 4 个空壳（`ecos_aiming`/`ecos_buszhi`/`ecos_datanet`/`ecos_dccheng`）——DROP 属 R9 禁项；新表一律按 ST07 落点。
5. **配置化前置**：库名与 schema 名一律经 `ecos.db.control-schema` / `ecos.db.engine-schema.*` / `ecos.dw.*` 取数（MC06），这是第 3 条可选形态与本条迁移能够共存的技术前提。

## 3. 影响（Consequences）

| 维度 | 影响 |
|:--|:--|
| 存量 | ✅ **零迁移**：与已验收的 665 表盘点口径一致，不引入分库改造 |
| 多库兼容 | ✅ 单库单连接池，MC01~MC03 方言收敛面收窄 |
| 隔离强度 | ⚠️ 引擎间隔离由 schema 权限（PG role + `GRANT`）而非 database 承担；须配套每引擎独立 DB role（详细设计 00 册「数据设计」给出授权矩阵） |
| 运维 | ✅ 备份/恢复/迁移序列单一；⚠️ 单库容量与连接数上限需容量规划（REQ-NF-03） |
| 文档 | 需更正：`ARCHITECTURE-RULES.md:80`、`PRD-00:247`、`ecos_backend/engine/*/AGENTS.md` 的"按 service 切流"表述（需求基线修订任务 #26） |

## 4. 关联

- ADR-7（端口与头信任）、ADR-12（`public`→`ecos_control`）、ADR-13（列存二选一）、ADR-14（确定性计算产物的业务域落点）。
- 实测依据：`docs/40-实现/数据库现状盘点与schema归属映射-2026-09-28.md` §二/§三。

## 5. 变更日志

| 日期 | 版本 | 变更 | 责任人 |
|:--|:--|:--|:--|
| 2026-09（内联） | v1.x | "enterprise/ultimate 每 service 独立 database"（仅存在于 plan/ARCHITECTURE-RULES 行内） | PMO |
| 2026-09-28 | v2.0 | 独立成文 + 修订为单库 5+1 权威口径，独立 database 降级为可选形态（用户裁决 Q6） | AI Agent |
