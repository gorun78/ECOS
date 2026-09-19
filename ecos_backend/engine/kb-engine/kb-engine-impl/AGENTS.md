# kb-engine-impl — 知识库引擎·实现层

> 子模块: kb-engine/impl | 端口: 共享父模块 18086 | 依赖: PostgreSQL (MyBatis) + Neo4j (enterprise/flagship)
> 上层: 见 ../AGENTS.md（kb-engine 顶层）

## 本模块干什么
- **实现层（业务）**：承载 KG 图查询/节点关系 CRUD、RAG 向量检索、规则 CRUD+版本、KG 同步、抽取、合规规则 CRUD。
- 测试充分（9 个 test class / 67 case P0-3 search + P0-4 compliance long）：`KGQueryTest` / `RagTopKTest` / `ComplianceRuleTest` / `ExtractedSubGraphTest` / `SubGraphRoundTripTest` / `CryptoAuditTest` / `HashChainAuditTest` 等。
- **不执行规则判定**（那是 cognitive-engine 的事，顶层红线 #1）。

## 主要 code（控制器/服务/DAO）
- `KnowledgeGraphController` — `/api/v1/kb/graph/{query|nodes|edges}` KG Cypher 查询 + 节点/关系 CRUD。
- `RagController` — `/api/v1/kb/rag` RAG topK 检索。
- `ExpertRuleController` — `/api/v1/kb/rules` 规则 CRUD + 版本。
- `ComplianceRuleController` — 合规规则 CRUD（compliance_rules 表）。
- `KnowledgeArticleController` — `/api/v1/kb/articles` 知识文章。
- `KnowledgeApiController` / `EcosKnowledgeGraphController` — 兼容端点。
- `GraphSyncController` — `/api/v1/kb/graph/sync` 本体→KG 同步（被 ontology-engine REST 调用）。
- `EntityLinkController` / `KnowledgeSettingsController` / `KbEngineHealthController` / `ExtractionController` — 实体链接/设置/健康/抽取。
- 服务：`KnowledgeGraphService` / `KGWriterService`（SubGraph 批量写入 Neo4j）/ `RuleGraphService`（规则→实体→关系图）/ `KnowledgeRetrievalService` / `ExpertRuleService` / `ComplianceRuleMapper`（MyBatis XML 屏蔽，Data 走 `$/mapper/ComplianceRuleMapper.xml`）。
- 服务：`crypto/` 子包（`DataEnc...` + `KeyManagementServiceImpl` 加密 facade，与 security-engine `KeyManagementService` 同 Pond 接口）。

## 调用链（只读 + 调谁）
- → 同 engine api: 注入 `KnowledgeGraphService` / `KnowledgeRetrievalService` / `EcosKnowledgeGraphService` / `ExpertRuleService` / `KnowledgeSettingsService` / `KgSyncService` 等接口（来自 `kb-engine-api`）。
- → Neo4j (enterprise 档): 经 `KGWriterService` / `KnowledgeGraphService` 的 `org.neo4j.driver.Driver`（**现状合规风险区**，`KGWriterService` `import org.neo4j.driver.*`；新代码禁止再 `new Driver`，必须走 `runtime-access` 提供的 `Neo4jClient` Bean；存量代码须在 Wave 5 前迁 runtime）。
- → PostgreSQL: `ComplianceRuleMapper` MyBatis（schema: `sys_man`）。
- → 不 import 其他 engine-impl（架构铁律 2.1）。
- ← 被调用方: ontology-engine（KG_SYNC :18086/api/v1/kb/graph/sync）、cognitive-engine（GET :18086/api/v1/kb/rules / POST /api/v1/kb/graph/query）。

## 端点 / 补丁
- 路径池：
  - `/api/v1/kb/graph/{query,nodes,edges}` — KG 端点（`KnowledgeGraphController`）。
  - `/api/v1/kb/rag` — RAG topK（`RagController`）。
  - `/api/v1/kb/rules` + `/api/v1/kb/rules/versions` — 规则 CRUD + 版本（`ExpertRuleController`）。
  - `/api/v1/kb/articles` — 文章（`KnowledgeArticleController`）。
  - `/api/v1/kb/graph/sync` — KG 入湖（`GraphSyncController`）。
  - `/api/v1/knowledge/sync/*` — 同步状态/日志/任务列表/预览/回滚（`GraphSyncController`，PMO-50 T2/T4）：
    `GET /status`、`POST /trigger`、`POST /object/{objectType}`、`GET /logs`、`GET /jobs`、
    `POST /jobs/{jobId}/preview`、`POST /jobs/{jobId}/rollback`、`GET /jobs/{jobId}/logs`。
  - `POST /api/v1/knowledge/graph/build` — 图谱构建（`KnowledgeIngestController`，方案 §5.3 修正，PMO-B3-2 T4）：
    入参 `GraphBuildRequest{mode=FULL|INCREMENTAL, dryRun=false|true}`，出参 `{jobId,status}`；
    异步执行「骨架版本对齐（B1）→ 契约驱动 DW 层实例抽取（B3-2）」，结果回填 `kg_sync_log.nodes/edges/report`。
  - `/api/v1/knowledge/ecos-graph` — Ecos 通用兼容端点（`EcosKnowledgeGraphController`，2 method：
    `GET /` 取图快照 / `POST /sync` 同步到 Neo4j）。
    注意：gateway 侧同名 Controller 用 base `/api/v1/ecos/knowledge-graph`，本模块侧路径独立，勿混。
  - `POST /api/v1/knowledge/docs/ingest` — 非结构化文档登记与解析（`KnowledgeIngestController`，
    B5-1 / ADR-2 A3 过渡态，multipart：`file` + 可选 `source`/`docId`/`chunkSize`/`chunkOverlap`）：
    原文写近源层 `raw/unstructured/{source}/{docId}/{fileName}`（复用 runtime-access `MinioStorageService`）
    → REST 登记数据工作台 `POST /api/v1/datanet/datalake/unstructured`（RAW/UNSTRUCTURED/LAKE_OBJECT）
    → 复用 `DocumentParserService` 解析 → 滑动窗口切分落 kb 自有过渡表 `ecos_knowledge.kb_doc_chunk`
    → 复用 B4 `KnowledgeVectorWriteService` 向量化 → REST 登记 `POST /api/v1/datanet/metadata/resources`
    （layer=CURATED, source_path=ecos_knowledge.kb_doc_chunk）。状态机 `queued→parsing→extracting→done/failed`
    落 `ecos_knowledge.kb_doc`（V138）。**A3 禁令**：kb 不直写 DW 层 CURATED 表（A2 永久否决）。
    关键类：`service/KnowledgeDocIngestService`、`doc/mapper/{KbDocMapper,KbDocChunkMapper}`、
    `doc/model/{KbDoc,KbDocChunk}`；chunkSize 取值须 ∈ {256,512,1024,2048}（与前端 `CHUNK_SIZE_OPTIONS` 对齐），
    默认由 `ecos.kb.doc.chunk-size`(512)/`ecos.kb.doc.chunk-overlap`(64) 配置。
- 实例抽取（PMO-B3-2）：`KbEntityInstanceExtractionService` — 经 REST 只读消费本体映射契约
  （`GET /api/v1/ontology/entity-mappings?ontologyId=`）与 DW 层实例行
  （`GET /api/v1/engine/data/layers/CURATED/resources/{id}/rows?watermark=&limit=`），
  按 C1~C4 校验写入 `graph_node`/`graph_edge`；水位线持久化于 `kb_extract_watermark`（V136），
  报告落 `kg_sync_log.report`（JSONB）。
- 示例（GraphSyncController 片段）：
```java
@RestController
@RequestMapping("/api/v1/kb/graph")
public class GraphSyncController {
    private final KgSyncService kgSyncService;
    @PostMapping("/sync")
    public ApiResponse<SyncLogEntry> sync(@Valid @RequestBody SyncFromOntologyRequest req) {
        // 调用 kgSyncService.sync()；MQ: 失败重试 3 次，仍失败回告 ontology
    }
}
```

## 禁止
- **不执行规则判定**（规则判定是 cognitive-engine 的事，顶层红线 #1）。
- **不直接调 LLM**（LLM 调用是 ai-engine 的事，顶层红线 #2；强制走 `llm-gateway`）。
- 不 import 其他 engine-impl（架构铁律 2.1）。
- 不直接 `new org.neo4j.driver.Driver`；新代码强制走 `runtime-access` 的 `Neo4jClient` Bean；存量 `KGWriterService` / `RuleGraphService` `import org.neo4j.driver.*` 需在 Wave 5 迁 runtime-access。
- 不硬编码 token / BOD / metadata（Neo4j / MinIO 凭据走 runtime-access，不在 Service 字面量）。
- 实体新提自有 driver 禁止（治理）。
- Nexus / Doris 关系变更走 `runtime-access`（Doris 在 ultimate 档启用）。
- 不引入 Flyway（schema 变更走 ADR）。
