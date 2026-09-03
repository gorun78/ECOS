# ontology-engine-impl — 本体引擎·实现层

> 子模块: ontology-engine/impl | 端口: 共享父模块 18083 | 依赖: PostgreSQL + kb-engine-api (KG 同步)
> 上层: 见 ../AGENTS.md（ontology-engine 顶层）

## 本模块干什么
- **实现层（业务）**：承载本体建模、对象 CRUD、版本管理、工作流、域管理、本体→KG 同步、术语表、自动发现、Git 同步。
- 测试：`Wave31OntologyConvergenceTest` + `ArchitectureTest` 守门；Wave 2/3/4 需要 2 个 payload 兼容边界大版本，由 `OntologyService` 2-arg + `MappingStore.store` round 测试覆盖本轮约束。

## 主要 code（控制器/服务/DAO）
- `OntologyController` / `OntologyCompatController` — 本体模型 CRUD + 兼容接口（双路径）。
- `OntologyDomainApiController` + `OntologyDomainController` — `domain` 入驻（`domain` 注册 + `listAll` + `get/cascade`）。
- `OntologyVersionController` / `OntologyVersionSimpleController` — 版本管理（含简化版本查询）。
- `OntologyWorkflowController` / `WorkflowController` — 审批/发布/工作流。
- `OntologyConfigController` / `OntologyMappingController` — 配置 + 映射（含 `MappingStore.store` round-trip）。
- `OntologyGraphController` / `OntologyGitController` — 本体检索与 Git 同步。
- `OntologyCopilotController` — 本体 Copilot。
- `OntologyExportController` — 导出。
- `OntologyDataController` / `OntologyProposalController` / `OntologyRuleController` / `OntologyRelationshipController` / `OntologyPropertyController` — 对象/提案/规则/关系/属性。
- `GlossaryController` / `AutoDiscoverController` / `ActionTypeController` / `FunctionController` / `LineageController` — 术语/自动发现/动作/函数/血缘。
- `OntologyEngineStatusController` — `/api/v1/engine/ontology/*` 健康检查与统计。
- `Neo4jGraphService` / `OntologyKgSyncService` — 对象→KG 同步（直接 `org.neo4j.driver` import 现状：**合规风险区，必须收敛 `runtime-access`**，目前已在 impl 内 `import org.neo4j.driver.*`，需在 Wave 5 之前统一迁到 runtime；在此之前，**新代码禁止再 new Neo4j Driver**。

## 调用链（只读 + 调 谁）
- → 同 engine api: 注入 `OntologyConfigService`/`OntologyGitService`/`OntologyGraphService`/`OntologyCopilotService`/`OntologyWorkflowService`/`ActionTypeService`（来自 `ontology-engine-api`）。
- → kb-engine: KG 同步走 **REST** `POST :18086/api/v1/kb/graph/sync`（不 import kb-engine-impl，架构铁律 2.1）。
- → 引擎外: 不直接 import `*-engine-impl`；外部图查询通过 `Neo4jGraphService` 封装（隔离 driver 路径），应逐步迁入 runtime-access（TODO Wave5）。
- ← 被调用方: gateway 聚合加载，前端 `/api/v1/ecos/ontologies`、`/api/v1/ecos/entities`、`/api/v1/ecos/domains`、`/api/v1/ecos/versions`、`/api/v1/ecos/workflows`、`/api/v1/ecos/objects`。

## 端点 / 补丁
- 兼容双路径：`/api/v1/ecos/{ontologies|entities|domains|versions|workflows|objects}` (gateway / 前端)
  + `/api/v1/engine/ontology/*`（引擎自身健康检查 + 统计 + 域入驻）。
- Domain 入驻落地（`OntologyDomainApiController`）：
```java
@RestController
@RequestMapping("/api/v1/engine/ontology/jurisdiction")
public class OntologyDomainApiController {
    @PostMapping  // domain 注册
    public ApiResponse<OntologyDomain> register(@Valid @RequestBody OntologyDomainSaveDTO dto) { ... }
    @GetMapping("/listAll")
    public ApiResponse<List<OntologyDomain>> listAll() { ... }
    @GetMapping("/{domainCode}/cascade")
    public ApiResponse<OntologyDomainDtoCascade> getCascade(@PathVariable String domainCode) { ... }
    @DeleteMapping("/{domainCode}")
    public ApiResponse<Void> release(@PathVariable String domainCode) { ... }
}
```

## 禁止
- 不直接写 Neo4j（通过 kb-engine 的 `graph/sync` 端点，顶层红线 #1）。
- 本体模型变更不自动生效（必须走版本发布流程，顶层红线 #2）。
- 对象实例不支持物理删除（只标记逻辑删除，顶层红线 #3）。
- 不直接 import `kb-engine-impl` / `cognitive-engine-impl` / `ai-engine-impl`（架构铁律 2.1 = 验收失败）。
- 现状 `Neo4jGraphService` / `OntologyKgSyncService` 仍直接 `import org.neo4j.driver.*` — **新代码不得再 new `Neo4jDriver` 或 `Neo4jRouter`**，必须走 `runtime-access` 提供的 `Neo4jClient` Bean。
- 不硬编码 token / BOD / metadata（KG 凭据走 `runtime-access`/`application.yml`，不在 Service 字面量）。
- 实体新提自有 driver 禁止（治理）。
- 不引入 Flyway（`spring.flyway.enabled: false`，schema 变更走 ADR）。
