# ADR-11 — 向量形态载体：pgvector 为基线，降级须显式标记

> 来源: 需求检视报告 §四 G3/§十 规划 + 《数据湖存储分层规范》v2.0 §七A 实查更正 | 日期: 2026-09-28 | 责任人: AI Agent
> 版本: v1.0
> 裁决: **Accepted**
> 上游: 湖规 v2.0 §〇（知识层双形态）/§七A（版本×分层矩阵）+ 《数据库访问规范》v1.2 MC03/MC05/ST08
> 状态: 生效

---

## 1. 背景（含对旧文档的事实更正）

旧版湖规与《数据库访问规范》曾记载"pgvector 未装（CSE 不装扩展）""三处 MD5 伪向量"。2026-09-28 实查结论不同：

| 事实 | 实查 |
|:--|:--|
| 扩展 | `vector 0.6.2` **已安装**于 dev 库（与 `pg_trgm` / `pgcrypto` 并存），**无需新容器** |
| kb 链路 | 真实向量已实现：`ecos_knowledge.knowledge_embedding.embedding_vec` + HNSW 索引 + `<=>` 余弦检索；嵌入经 `llm-gateway POST /api/v1/llm/embedding`（`PgVectorSupport` 探测 + `QueryEmbeddingHelper`） |
| 数据量 | 该表 **0 行**——缺的是数据与召回实测，不是能力 |
| 真实遗留降级点 | DQ `DqKnowledgeSinkServiceImpl`（MD5 伪向量存 `ecos_dq.dq_knowledge_entry.embedding text`）、cognitive `PrecedentRef`（pgvector 不可用即回退关键词） |
| 违规存量 | `public.ecos_knowledge_document.embedding jsonb`：向量数据落主控制 schema = MC05 + ST08 双违（knownLegacy，不迁不删） |

## 2. 决策

1. **基线载体 = PG + pgvector**（三档同构可用，因扩展随库不随档），**专用向量引擎不是前置项**：`enterprise/ultimate` 若引入专用引擎须另立 ADR，且必须仍走 `llm-gateway` 取嵌入。
2. **降级只在探测缺失时发生，且必须显式标记**（MC03）：`PgVectorSupport` 探测失败 → 链路降级为关键词/文本相似，且响应体与日志必须携带 `vectorDegraded=true`，前端必须可见（禁静默降级冒充向量检索）。
3. **嵌入生成唯一出口 = `llm-gateway`**：任何引擎不得自行封装 embedding HTTP 调用或本地哈希伪向量（铁律 §2.5-2；改造项 C1 属同类问题）。
4. **向量数据禁入控制域 schema**（MC05/ST08）：新向量列只准落业务域知识层载体（`ecos_knowledge.knowledge_embedding` 作为知识层双形态的 PG 承载，或 Doris∨CH/专用引擎）；降级存储用 `TEXT`（列名 `*_text` / `embedding_text`；【2026-09-30 校订】此处 `*_text` **仅指序列化向量串**，MC05 正文点名的合规例外，不适用 DR04「JSON 语义列须 `_json` 后缀」口径——JSON 语义列一律 `*_json` TEXT），**禁 `JSONB` 存向量**（v1.2 后 JSONB 不在 MC02 白名单）。
5. **DQ / cognitive 的向量依赖点收口**（工程清单 #13）：改造为经 `runtime-access` + `llm-gateway`，消除引擎内自建算法。

## 3. 影响

| 维度 | 影响 |
|:--|:--|
| 需求 | REQ-KB-* 的向量部分从"待建设"改为"已具备、待实测"；PRD-00 结论段与湖规缺口表已同步更正 |
| 验收 | 新增验收项：**有数据后的召回实测**（recall@5 / mrr@5 / ndcg@5 三指标跑通，替换空表状态） |
| 多库兼容 | ⚠️ 向量列不是可移植类型 → 只出现在业务域，且必须有 TEXT 降级分支，控制域零向量列即保持 MC01~MC03 |
| 存量 | `public.ecos_knowledge_document.embedding jsonb` 保留为 knownLegacy；新写路径禁复用该列 |

## 4. 关联

湖规 v2.0 §〇/§一/§七A、《数据库访问规范》v1.2 MC02/MC03/MC05/ST08、ADR-13（列存二选一，同为业务域专用承载）、工程清单 #10~#11/#13、详细设计 04 册。

## 5. 变更日志

| 日期 | 版本 | 变更 | 责任人 |
|:--|:--|:--|:--|
| 2026-09-28 | v1.0 | 首次成文；固化"已装 0.6.2 / kb 链路真实 / 0 行"实测事实，明确降级标记、嵌入唯一出口与向量禁入控制域 | AI Agent |
