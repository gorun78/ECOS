# security-engine-impl — 安全引擎·实现层

> 子模块: security-engine/impl | 端口: 共享父模块 18081 | DB: PostgreSQL
> 上层: 见 ../AGENTS.md（security-engine 顶层）

## 本模块干什么
- **实现层（业务）**：承载全部安全能力 Controller / Service / DAO / KMS Adapter。
- 测试充分：11 个 test class（含 PDP/PEP/PIP/ABAC/Rls/Crypto/HashChain/Masking），ArchUnit 守门。

## 主要 code（服务/控制器/配置/事件）
- `RlsController` → `/api/v1/security/rls` + `/api/security/rls`（行级，双路径兼容）。
- `ClsController` → `/api/v1/security/cls` + `/api/security/cls`（列级）。
- `PolicyEngineController` → `/api/v1/policy-engine` + `/api/v1/security/policy-engine`（ABAC）。
- `CryptoAuditController` → `/api/v1/audit/crypto`（加密审计）。
- `KeyManagementServiceFullImpl` / `KeyManagementServiceImpl` — KMS 门面（AWS/Vault/Azure 可选）。
- `RowLevelSecurityServiceImpl` / `ColumnLevelSecurityServiceImpl` — 行/列安全决策。
- `AbacPermissionCheckerImpl` / `AbacPolicyServiceImpl` — ABAC 缓存 + 决策。
- `PolicyEnforcementPointImpl` — PEP 拦截点（与 PDP 协作）。
- `AuditHashChainService` — 审计日志哈希链（防篡改）。
- `DataMaskingService` — PHONE/EMAIL/ID_CARD/AMOUNT 脱敏。
- `SecuritySandboxService` + `SecurityController` — P1 模拟器与解密仿真。

## 调用链（只读 + 调谁）
- → 本模块内: `RlsController` → `RowLevelSecurityServiceImpl` → `PolicyDecisionPointImpl` → `PolicyAdministrationPointImpl`（读 PG）。
- → 引擎外: OPA REST（`OpaPolicyService` 走 OPA 节点，谨慎使用，需要时按 Task 下发再启用）。
- → 不依赖其他引擎 impl（仅 common-api / runtime 工具）。
- ← 被调用方: gateway（`/api/security/mask` `evaluate-filter` `decrypt` `audit-logs`）、各引擎 caller 的 RLS/CLS/mask REST。

## 端点 / 补丁
- 双路径：新写 Controller 优先 `/api/v1/security/...`，并保留 `/api/security/...` 兼容（双 `@RequestMapping` 数组写法）。
- 示例（RlsController 片段）：
```java
@RestController
@RequestMapping({"/api/security/rls", "/api/v1/security/rls"})
public class RlsController {
    private final RowLevelSecurityServiceImpl rlsService;
    @PostMapping("/apply")
    public ApiResponse<Map<String, String>> apply(RlsApplyRequest req) {
        // ... 构造 whereClause
    }
}
```
- 审计一律走 `AuditHashChainService`，新写 Controller 需 `@Autowired(required = false)` 接入。

## 禁止
- 引擎内重复实现安全判定/脱敏逻辑（违反架构铁律 2.4。统一由本模块 REP 裁决，其他引擎不得自建）。
- 不 import 其他 engine-impl（架构铁律 2.1）。
- 不物理删除 audit 记录（只 `archived` 标记，违反 1-sysman 不可逆原则）。
- 不存储明文密码/密钥，密钥走 `KeyManagementService`。
- 不直接 import `org.jasypt` / `org.bouncycastle` 裸用，统一封装于 `crypto/kms` 适配器。
- 硬编码 token / BOD / metadata 禁止。
- 实体新提自有 driver / 直连 PG 的 DAO 用 `@MapperScan` 统一收敛（现状无 MapperScan，新增时**不要**自己 new DataSource，注入 `JdbcTemplate`/`NamedParameterJdbcTemplate`）。
