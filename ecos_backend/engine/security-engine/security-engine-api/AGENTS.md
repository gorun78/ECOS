# security-engine-api — 安全引擎·服务接口层

> 子模块: security-engine/api | 端口: 共享父模块 18081 | DB: PostgreSQL
> 上层: 见 ../AGENTS.md（security-engine 顶层）

## 本模块干什么
- **接口层/服务层契约**：承载安全策略/行级/列级安全/脱敏的 `interface` + `entity` 契约。
- 这是**唯一定义契约的模块**，impl/boot 仅参考，不修改契约签名（API 只增不改）。

## 主要 code（接口/契约）
- `IDataSecurityPolicyService` — 安全策略契约（CRUD + assess）。
- `RowLevelSecurityService` — 行级安全（RLS WHERE 注入）契约。
- `ColumnLevelSecurityService` — 列级安全（CLS）契约。
- `DataSecurityPolicy` — 策略实体（domain + 字段集合 + 作用对象类型）。
- `DataSecurityPolicyDao` — 策略持久化契约。

## 调用链（只读 + 调谁）
- → 上层 engine: **无**（底层契约。被 security-engine-impl 与 gateway 的 `VersionPrefixRewriteFilter` 引用）。
- ← 被调用方: 仅 security-engine-impl 的 `RowLevelSecurityServiceImpl`、`ColumnLevelSecurityServiceImpl`、`SecurityPolicyController`、`RlsController`、`ClsController` 等。

## 端点 / 补丁
- 本模块**不暴露 REST 端点**（无 `@RestController`）。
- 契约唯一原则：所有 impl 必须 `implements` 该模块的 `interface`，再写带 fallback 的具体实现：
```java
public RowLevelSecurityService rowLevelSecurityService() {
    return new RowLevelSecurityServiceImpl(pdpService, auditLogService);
}
```

## 禁止
- 不改既有方法签名（API 只增不改）。
- 不在此模块写任何业务/实现类（带 `interface` 与 `entity` 的池外均禁止）。
- 不 import `*-engine-impl`（违反架构铁律：契约不依赖业务实现）。
- 不硬编码 token / BOD / metadata。
- 不引入新 PG 表/字段，schema 变更走 ADR。
