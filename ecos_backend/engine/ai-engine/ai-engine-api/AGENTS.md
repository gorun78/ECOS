# ai-engine-api — AI 引擎·服务接口层

> 子模块: ai-engine/api | 端口: 共享父模块 18084 | 依赖: runtime/llm-gateway, cognitive-engine-api
> 上层: 见 ../AGENTS.md（ai-engine 顶层）

## 本模块干什么
- **接口层/服务层契约**：承载 Skill / AgentMesh / Guardrails / ActionBridge / PromptCompiler / CronJob 的 `interface` + `entity` 契约。
- **唯一定义契约的模块**：本模块是**跨引擎调用方** 的契约入口（cognitive-engine / kb-engine / security-engine 都通过 ai-engine-api 的契约识别 ai-engine）。

## 主要 code（接口/实体）
- `SkillService` + `entity/SkillEntity` — 技能（Skill）CRUD 契约（含 `/api/v1/ai-engine/skill/*`）。
- `CronJobService` + `entity/CronJobEntity` / `entity/CronJobExecutionEntity` — 定时任务契约（含委托 `runtime-task` 全局调度，不在本 api 内 `enableScheduling`）。
- `AgentMeshService` — Agent 网格（mesh）契约（含 `delegate_to_agent` 委托子 Agent，**单层**。上层契约禁止递归委托）。
- `GuardrailsService` — Guardrail 过滤契约（输出安全过滤）。
- `ActionBridgeService` — Action 桥接契约（Agent Action → external API）。
- `PromptCompilerService` — Prompt 编译器契约（模板插件化）。

## 调用链（只读 + 调谁）
- → 上层 engine: **无**（契约层只读）。
- ← 被调用方: `ai-engine-impl` 的 `SkillController` / `AgentMeshController` / `AIPGuardrailController` / `ActionBridgeController` / `PromptCompilerController` / `CronJobController` 等（27 个 Controller）。
- 跨引擎: ai-engine 顶层依赖 `cognitive-engine-api`（混合推理委托 `POST :18089/api/v1/knowledge/reason`），本 api 仅定义签名，不在本模块直接调 impl。

## 端点 / 补丁
- 本模块**不暴露 REST 端点**（无 `@RestController`）。
- 双契约一致性：impl 必须保持签名一致、参数 entity/dto 来自 api（含 `SkillEntity` / `CronJobEntity`，**禁止在 impl 内另起点 entity**）。
- 示例（Skill 契约）：
```java
public interface SkillService {
    /** 技能列表（含分页 + 关键词模糊 ILIKE） */
    PageResult<SkillVO> listSkills(SkillQuery query);
    SkillVO getSkill(String skillId);
    ApiResponse<SkillVO> addSkill(SkillSaveDTO dto);
}
```

## 禁止
- 不在此模块加任何业务实现类（带 `interface` 与 `entity` 的池外禁止）。
- 不改既有方法签名（API 只增不改）。
- 不 import `*-engine-impl` / 不 import `com.chinacreator.gzcm.cognitive` 不存在包（违反架构铁律 2.1 + PMO 5.1 #1）。
- 不硬编码 token / BOD / metadata（LLM 密钥走 `llm-gateway` 注入，不在 api 字面量）。
- `@Value` 禁止（契约不关心配置；配置在 impl / boot）。
- CronJob 实体本模块仅描述字段，调度执行必须委托 `runtime-task`（不在 api 内开线程）。
