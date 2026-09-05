# kb-engine-api — 知识库引擎·服务接口层

> 子模块: kb-engine/api | 端口: 共享父模块 18086 | 依赖: Neo4j (enterprise/flagship)
> 上层: 见 ../AGENTS.md（kb-engine 顶层）

## 本模块干什么
- **接口层/服务层契约**：承载 KG 检索/规则 CRUD/文章 RAG/同步/Post-extraction 的 `interface` + `model` 契约。
- **唯一定义契约的模块**：本模块定义 `KgSyncService`，供 `cognitive-engine` / `ontology-engine` 跨引擎调用（架构铁律 2.1：cross-engine 调 api 接口，不调 impl）。

## 主要 code（接口/模型）
- `KnowledgeGraphService` — 节点/关系 CRUD + 子图查询契约。
- `KnowledgeRetrievalService` — RAG 向量检索 + topK 契约。
- `EcosKnowledgeGraphService` — 知识图谱通用契约（兼容端点）。
- `ExpertRuleService` — 规则 CRUD + 版本契约。
- `KnowledgeSettingsService` — 知识设置契约。
- `KgSyncService` — 本体→KG 同步契约（被 ontology-engine 跨模块注入）。
- 模型：`KnowledgeNode` / `KnowledgeEdge` / `KnowledgeEmbedding` / `GraphSubgraph` / `SyncLogEntry` — KG 数据模型。
- 模型：`ComplianceRule` / `RuleVersion` / `ExpertRule` / `RuleExecutionResult` — 规则数据模型。
- 模型：`KnowledgeArticle` / `ExtractionSource` — 文章与抽取源。

## 调用链（只读 + 调谁）
- → 上层 engine: **无**（契约层只读。被 kb-engine-impl 与 跨 engine 的 cognitive/ontology/boot 引用）。
- ← 被调用方: `kb-engine-impl` 的 `KnowledgeGraphController` / `RagController` / `ExpertRuleController` / `ComplianceRuleController` / `KnowledgeArticleController` / `KnowledgeApiController` / `EcosKnowledgeGraphController` / `GraphSyncController` 等。
- 跨引擎：cognitive-engine 顶层依赖 `kb-engine-api` 的 `KG_QUERY` / `RULE_CHECK` 接口（见 cognitive AGENTS.md）。

## 端点 / 补丁
- 本模块**不暴露 REST 端点**（无 `@RestController`）。
- 双契约一致性：impl 必须保持签名一致、参数 DTO 来自 api。
- 示例（KG 同步契约）：
```java
public interface KgSyncService {
    /** 对象→KG 同步入口（ontology-engine 调用，REST :18086/api/v1/kb/graph/sync） */
    ApiResponse<SyncLogEntry> sync(SyncRequest request);
    ApiResponse<Void> batchDelete(List<String> nodeIds);
}
```

## 禁止
- 不在此模块加任何业务实现类（带 `interface` 与 `entity` 的池外禁止）。
- 不改既有方法签名（API 只增不改）。
- 不 import `*-engine-impl`（违反架构铁律 2.1 = 验收失败）。
- 不硬编码 token / BOD / metadata；不在本 api 内 `@Value` 字面量。
- 实体新提自有 driver 禁止（Neo4j Driver 收敛 `runtime-access`，本模块仅定义签名）。
- RAG topK 必须由 `KnowledgeRetrievalService` 接口契约约束；不要在 impl 自建向量库（runtime-access 提供）。
