# ontology-engine-api — 本体引擎·服务接口层

> 子模块: ontology-engine/api | 端口: 共享父模块 18083 | DB: PostgreSQL
> 上层: 见 ../AGENTS.md（ontology-engine 顶层）

## 本模块干什么
- **接口层/服务层契约**：承载本体配置/版本/图谱/工作流/Git/Copilot/ActionType 的 `interface` + `model` 契约。
- 这是**唯一定义契约的模块**，impl/boot 仅参考，不修改契约签名（API 只增不改）。

## 主要 code（接口/模型）
- `OntologyConfigService` — 本体配置契约。
- `OntologyGitService` — 本体 Git 同步契约（委托 runtime 的 Git 服务，禁止本模块操作 git）。
- `OntologyGraphService` — 本体检索契约（对象→KG 同步入口）。
- `OntologyCopilotService` — 本体 Copilot 契约。
- `OntologyWorkflowService` — 工作流（审批/发布）契约。
- `ActionTypeService` + `model/ActionType` — 动作类型契约。
- `model/ExtractedSubGraph` — 抽取子图模型（供 ai-engine `KnowledgeExtractorService` 复用）。

## 调用链（只读 + 调谁）
- → 上层 engine: **无**（契约层只读）。
- ← 被调用方: `ontology-engine-impl` 的 `OntologyConfigController` / `OntologyGitController` / `OntologyGraphController` / `OntologyCopilotController` / `OntologyWorkflowController` / `ActionTypeController` 等。
- 跨引擎：`ontology-engine` 顶层依赖 `kb-engine-api`（KG 同步），在本 api 模块中只暴露方法签名（`KgSyncService` 注入），不直接调 impl。

## 端点 / 补丁
- 本模块**不暴露 REST 端点**（无 `@RestController`）。
- 双契约一致性：impl 必须保持签名一致、参数 DTO 来自 api。
- 示例（Domain 入驻契约）：
```java
public interface OntologyGraphService {
    /** domain 注册（→ domain listAll/cascade/get） */
    ApiResponse<OntologyDomain> registerDomain(OntologyDomainSaveDTO dto);
    List<OntologyDomain> listAllDomains();
    OntologyDomain getDomainCascade(String domainCode);
    ApiResponse<Void> releaseDomainDomain(String domainCode);
}
```
- 新契约的 impl 在 `*-impl` 内独立文件，注释必须 `JSDoc` 风格。

## 禁止
- 不改既有方法签名（API 只增不改）。
- 不在此模块写实现类（带 `interface` 与 `model` 的池外禁止）。
- 不 import `*-engine-impl`（违反架构铁律 2.1）。
- 不硬编码 token / BOD / metadata；不在此 api 内用 `@Value` 字符串注入。
- 对象物理删除建议在 API 层改为 `logicallyDelete`（顶层红线 #3：对象实例只标记逻辑删除）。
- 实体新提自有 driver 禁止（Driver 收敛 `runtime-access`）。
