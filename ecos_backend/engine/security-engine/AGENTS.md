# security-engine — 安全引擎

> 端口: **18081** | PMO: **ecos-be** | 依赖: PostgreSQL

## 我负责的
- 认证（JWT签发/验证/刷新）
- 授权（RBAC/ABAC/PBAC）
- 审计日志（操作审计、数据访问审计）
- 数据脱敏（手机/邮箱/身份证/金额）
- 数据权限（行级/列级）
- 加密服务（密钥管理）

## 我暴露的端点

### Phase 1 当前可用
| 端点 | 方法 | 用途 |
|------|------|------|
| `/api/security/mask` | POST | 数据脱敏（SHA256/PHONE/EMAIL/ID_CARD/AMOUNT） |
| `/api/security/evaluate-filter` | POST | 安全过滤规则评估 |
| `/api/security/decrypt` | POST | 解密仿真 |
| `/api/security/audit-logs` | GET | 审计日志查询 |

### Phase 2+ 规划
| 端点 | 方法 | 用途 |
|------|------|------|
| `/api/v1/security/policy/evaluate` | POST | ABAC策略评估 |
| `/api/v1/security/audit/log` | POST | 写审计日志 |
| `/api/v1/security/rls/apply` | POST | 行级安全 |
| `/api/v1/security/cls/columns` | POST | 列级安全 |

> 完整接入规则: `docs/1-sysman/03-安全接入规则.md`

## 我的数据库表
- 用户表、角色表、权限表、审计日志表、脱敏规则表、ABAC策略表

## 我依赖的外部端点
无。security-engine是底层引擎。

## 禁止
1. 不存储明文密码
2. 不硬编码密钥
3. 审计日志不可物理删除（只标记archived）

## Modules

> 原 3 份子模块 AGENTS.md 合并入此章节。原文件已删除，git 历史保留。

### security-engine-api（接口层）

> 端口: 共享父 18081 | 契约唯一原则

- **接口/服务层契约**：承载安全策略 / 行级 / 列级 / 脱敏的 `interface` + `entity` 契约。唯一定义契约的模块，impl/boot 仅参考，不修改契约签名（API 只增不改）。

#### 主要 code（契约清单）
- `IDataSecurityPolicyService` — 安全策略契约（CRUD + assess）
- `RowLevelSecurityService` — 行级安全（RLS WHERE 注入）契约
- `ColumnLevelSecurityService` — 列级安全（CLS）契约
- `DataSecurityPolicy` — 策略实体（domain + 字段集合 + 作用对象类型）
- `DataSecurityPolicyDao` — 策略持久化契约

#### 调用链
- → 上层: 无（底层契约，被 impl 与 gateway 的 `VersionPrefixRewriteFilter` 引用）
- ← 被调方: impl 的 `RowLevelSecurityServiceImpl` / `ColumnLevelSecurityServiceImpl` / `SecurityPolicyController` / `RlsController` / `ClsController`

#### 禁止
- 不改既有方法签名（API 只增不改）
- 不在此模块写业务/实现类（带 `interface` 与 `entity` 的池外禁止）
- 不 import `*-engine-impl`（架构铁律：契约不依赖业务实现）
- 不硬编码 token / BOD / metadata
- 不引入新 PG 表/字段，schema 变更走 ADR

### security-engine-impl（实现层）

> 端口: 共享父 18081 | 测试充分: 11 test class（PDP/PEP/PIP/ABAC/Rls/Crypto/HashChain/Masking），ArchUnit 守门

- **实现层（业务）**：承载全部安全能力 Controller / Service / DAO / KMS Adapter。

#### 主要 code（控制器/服务/DAO）
- `RlsController` → `/api/v1/security/rls` + `/api/security/rls`（行级，双路径兼容）
- `ClsController` → `/api/v1/security/cls` + `/api/security/cls`（列级）
- `PolicyEngineController` → `/api/v1/policy-engine` + `/api/v1/security/policy-engine`（ABAC）
- `CryptoAuditController` → `/api/v1/audit/crypto`（加密审计）
- `KeyManagementServiceFullImpl` / `KeyManagementServiceImpl` — KMS 门面（AWS/Vault/Azure 可选）
- `RowLevelSecurityServiceImpl` / `ColumnLevelSecurityServiceImpl` — 行/列安全决策
- `AbacPermissionCheckerImpl` / `AbacPolicyServiceImpl` — ABAC 缓存 + 决策
- `PolicyEnforcementPointImpl` — PEP 拦截点（与 PDP 协作）
- `AuditHashChainService` — 审计日志哈希链（防篡改）
- `DataMaskingService` — PHONE/EMAIL/ID_CARD/AMOUNT 脱敏
- `SecuritySandboxService` + `SecurityController` — P1 模拟器与解密仿真

#### 调用链
- → 本模块内: `RlsController` → `RowLevelSecurityServiceImpl` → `PolicyDecisionPointImpl` → `PolicyAdministrationPointImpl`（读 PG）
- → 引擎外: OPA REST（谨慎使用，需要时按 Task 下发再启用）
- → 不依赖其他引擎 impl（仅 common-api / runtime 工具）
- ← 被调方: gateway（`/api/security/mask` `evaluate-filter` `decrypt` `audit-logs`）+ 各引擎 caller 的 RLS/CLS/mask REST

#### 端点 / 补丁
- 双路径：新写 Controller 优先 `/api/v1/security/...`，并保留 `/api/security/...` 兼容（双 `@RequestMapping` 数组写法）
- 审计一律走 `AuditHashChainService`，新写 Controller 需 `@Autowired(required = false)` 接入

#### 禁止
- 引擎内重复实现安全判定/脱敏逻辑（统一由本模块 REP 裁决，其他引擎不得自建）
- 不 import 其他 engine-impl（架构铁律 2.1）
- 不物理删除 audit 记录（只 `archived` 标记）
- 不存储明文密码/密钥，密钥走 `KeyManagementService`
- 不直接 import `org.jasypt` / `org.bouncycastle` 裸用，统一封装于 `crypto/kms` 适配器
- 不硬编码 token / BOD / metadata
- 实体新提自有 driver / 直连 PG 的 DAO 用 `@MapperScan` 统一收敛；不 `new DataSource`

### security-engine-boot（启动器）

> 端口: 共享父 18081 | 仅开发调试用 | 生产统一走 gateway 的 `excludeFilters` 聚合加载

- `SecurityEngineApplication.java`（唯一 file）：

```java
@SpringBootApplication(exclude = {
    HibernateJpaAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class})
@EnableScheduling
@ComponentScan(basePackages = {
    "com.chinacreator.gzcm.engine.security",
    "com.chinacreator.gzcm.sysman",
    "com.chinacreator.gzcm.runtime"
})
public class SecurityEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(SecurityEngineApplication.class, args);
    }
}
```

> 未标 `@MapperScan`，依赖 commybatis 2.3 mybatis-spring 自动扫描。新加 Mapper 接口需 class path 可被自动扫描（若不可用，在 `SecurityBootConfig` 加 `@MapperScan("com.chinacreator.gzcm.engine.security.**.dao")`）。

#### 调用链
- ComponentScan 扫 `security/` + `sysman/` + `runtime/` 三个 package（与 Gateway 对齐）
- 排除 `HibernateJpaAutoConfiguration` + `JpaRepositoriesAutoConfiguration`
- 被调方: 开发环境独立 JVM；生产 gateway 通过 `excludeFilters` 排除本类同名副本

#### 端点 / 补丁
- Phase 2+ boot 不增端点，端点池在 impl 子模块
- 新增端点流程：impl 加 Controller → gateway 的 `VersionPrefixRewriteFilter` → `sysman/SecurityConfig` permitAll → `ClearanceInterceptor` 双路径豁免 → `mvn install -DskipTests` 验证

#### 禁止
- 不在此模块新加业务代码
- 不删除 `@SpringBootApplication(exclude=...)` 的 JPA 排除（架构铁律 4 / 数据层 3.1）
- 不硬编码 token / BOD / metadata（密钥走 `application-*.yml` 与 `KeyManagementService`）
- 不在本 boot 内 `new Driver` / `new JdbcTemplate`（PG 连接池随 `runtime-access` 注入）
- 不启用 Flyway（`spring.flyway.enabled: false`）
- 生产发布禁止以 boot 启动，生产只走 gateway
