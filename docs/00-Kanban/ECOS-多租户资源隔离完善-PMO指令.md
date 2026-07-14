# PMO指令: 多租户资源隔离完善

> **来源**: 肖国荣租户管理审计 | **日期**: 2026-06-29
> **铁律**: Git提交=唯一绩效。DONE=commit hash+curl 200。不产出解释性文档。
> **前提**: Tenant实体有隔离设计，QuotaFilter有API限流——但数据层零隔离，两套实现分裂。

## 背景

当前租户管理三个致命问题：
1. **数据层零隔离**——`ecos_objects` / `ecos_pipeline_definition` / `ecos_dq_rule` 等业务表的SQL全不带 `tenant_id` 过滤。Tenant实体的 `isolationMode` 只是字段，从未被消费
2. **tenant_id只信任HTTP头**——`QuotaFilter`从 `X-Tenant-Id` 读取，可任意伪造。JWT中没有tenant_id
3. **三套表分裂**——`TenantController` 操作 `td_tenant`，`TenantServiceImpl` 通过DAO操作另一套，`TenantBillingController` 操作 `ecos_tenant_quota`——三个系统互不相通

**当前已有资产**：
- `QuotaFilter`：API限流+原子UPSERT ✅
- `TenantContextHolder`：ThreadLocal已就绪 ✅
- `RowLevelSecurityService`：权限行级过滤已有拦截器骨架 ✅
- `ecos_tenant_quota` / `ecos_tenant_usage`：配额表+种子数据 ✅

## 禁止清单

1. **禁止**在MVP阶段实现SCHEMA/DATABASE隔离——50租户以内ROW_FILTER足够。Pn需求来了再上
2. **禁止**新增Maven模块——所有改动在gateway/sysman-impl/workspace-impl内
3. **禁止**新建Controller或Service接口——扩展现有类，不建新类（Migrations除外）
4. **禁止**修改已有API的响应格式——tenant_id字段只增不减
5. **禁止**产出分析文档——唯一产出是git commit

---

## P0-1: 统一租户数据表（2天）

**目标**: 一张 `ecos_tenant` 替代 `td_tenant`，配额和用量保持现有表

### 任务

| ID | 任务 | 文件 | 工作量 |
|:--|------|------|:--:|
| P0-1.1 | 建表+数据迁移 | `V37__ecos_tenant_unified.sql` | 0.5d |
| P0-1.2 | 重写TenantController | `TenantController.java` → 读写`ecos_tenant` | 1d |
| P0-1.3 | TenantBillingController合并 | 配额/用量端点合并入TenantController | 0.5d |

### V37 建表SQL

```sql
CREATE TABLE IF NOT EXISTS ecos_tenant (
    id              VARCHAR(32) PRIMARY KEY,
    tenant_name     VARCHAR(64) NOT NULL,
    tenant_code     VARCHAR(32) UNIQUE,
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    max_users       INT DEFAULT 0,
    max_storage_mb  BIGINT DEFAULT 0,
    max_api_per_day BIGINT DEFAULT 0,
    isolation_mode  VARCHAR(16) DEFAULT 'ROW_FILTER',
    schema_name     VARCHAR(64),
    database_url    VARCHAR(256),
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

-- 从 td_tenant 迁移已有租户
INSERT INTO ecos_tenant (id, tenant_name, tenant_code, status, created_at, updated_at)
SELECT "TENANT_ID", "TENANT_NAME", "DOMAIN", "STATUS", "CREATED_TIME", "UPDATED_TIME"
FROM td_tenant WHERE "STATUS" != 'DELETED'
ON CONFLICT (id) DO NOTHING;
```

### TenantController 统一端点（迁移后）

```
GET    /api/v1/system/tenants           列表
GET    /api/v1/system/tenants/{id}      详情
POST   /api/v1/system/tenants           创建
PUT    /api/v1/system/tenants/{id}      更新
DELETE /api/v1/system/tenants/{id}      软删除
GET    /api/v1/system/tenants/{id}/quota        配额查询
PUT    /api/v1/system/tenants/{id}/quota        配额修改
GET    /api/v1/system/tenants/{id}/usage        用量统计
GET    /api/v1/system/tenants/{id}/invoice      模拟账单
```

### 验收

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.accessToken')

# 1. 创建租户
curl -s -X POST http://localhost:8080/api/v1/system/tenants \
  -H "Authorization: Bearer ***  -H "Content-Type: application/json" \
  -d '{"tenantName":"信科项目型","tenantCode":"xinke","maxUsers":100,"maxStorageMb":10240,"maxApiPerDay":10000}' \
  | jq '.data.tenantId'
# 期望: 返回新tenantId

# 2. 查看配额
curl -s http://localhost:8080/api/v1/system/tenants/xinke/quota \
  -H "Authorization: Bearer *** | jq '.data.maxUsers'
# 期望: 100

# 3. 更新配额
curl -s -X PUT http://localhost:8080/api/v1/system/tenants/xinke/quota \
  -H "Authorization: Bearer ***  -H "Content-Type: application/json" \
  -d '{"quota_type":"API_CALLS","daily_limit":5000}' | jq '.code'
# 期望: 200

# 4. 用量统计（含API_CALLS记录）
curl -s http://localhost:8080/api/v1/system/tenants/xinke/usage \
  -H "Authorization: Bearer *** | jq '.data.daily_usage | length'
# 期望: ≥0
```

---

## P0-2: JWT集成tenant_id（2天）

**目标**: 登录时把tenant_id写入JWT claims，`JwtAuthenticationFilter`解析后设置`TenantContextHolder`。安全闭环——不信任HTTP头。

### 任务

| ID | 任务 | 文件 | 工作量 |
|:--|------|------|:--:|
| P0-2.1 | AuthController登录时查tenant_id | `AuthServiceImpl.java` — 查`td_user`所属租户写入JWT | 0.5d |
| P0-2.2 | JWT filter解析tenant_id | `JwtAuthenticationFilter.java` — 在`doFilterInternal`尾部加 | 0.5d |
| P0-2.3 | QuotaFilter优先用JWT | `QuotaFilter.java` — `X-Tenant-Id`降级为fallback | 0.5d |
| P0-2.4 | curl验收 | E2E: 登录→JWT含tenant_id→API请求自动带tenant过滤 | 0.5d |

### 关键代码位置

`JwtAuthenticationFilter.doFilterInternal()`，在`SecurityContextHolder.setContext()`之后：
```java
// 从JWT claims提取tenant_id设置到上下文
String tenantId = claims.get("tenant_id", String.class);
if (tenantId != null && !tenantId.isBlank()) {
    TenantContextHolder.setTenantId(tenantId);
}
```

`QuotaFilter.doFilterInternal()`开头：
```java
// 优先从JWT获取（TenantContextHolder），HTTP头降级
String tenantId = TenantContextHolder.getTenantId();
if (tenantId == null || tenantId.isBlank()) {
    tenantId = request.getHeader("X-Tenant-Id");
}
```

### 验收

```bash
# 1. 登录后JWT包含tenant_id
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.accessToken')
# 解码JWT payload (base64中间段)
echo $TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | jq '.tenant_id'
# 期望: "admin-tenant" 或安装时分配的默认租户

# 2. API请求后tenant context已设置（验证方式：查看后续API的tenant过滤行为）
```

---

## P0-3: 行级租户过滤拦截器（3天，核心）

**目标**: 所有业务表查询自动注入 `WHERE tenant_id = ?`。不改每个Controller的SQL。

**策略**: 复用现有的 `RowLevelSecurityService` + `DataPermissionInterceptor`——它们已经有拦截器骨架和注解机制。追加一个 `@TenantFiltered` 注解标记需要租户隔离的Controller方法，拦截器自动追加WHERE子句。

### 任务

| ID | 任务 | 文件 | 工作量 |
|:--|------|------|:--:|
| P0-3.1 | `@TenantFiltered` 注解 | 新建 `com.chinacreator.gzcm.common.annotation.TenantFiltered.java` | 0.5d |
| P0-3.2 | TenantAspect切面 | 新建 `TenantFilterAspect.java` — AOP拦截注解方法，注入tenant过滤 | 1.5d |
| P0-3.3 | 6个Controller加注解 | ObjectController/ObjectQLController/DqDashboardController/PipelineController/WorkflowController/CausalController | 0.5d |
| P0-3.4 | curl验收 | 两个租户各创建Object→交叉验证隔离 | 0.5d |

### @TenantFiltered 注解

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantFiltered {
    /** 指定entity参数名（Controller方法参数中实体名称在哪里） */
    String entityParam() default "";
    /** 指定tenant_id的来源列名 */
    String tenantColumn() default "tenant_id";
}
```

### TenantFilterAspect 逻辑

```
1. 拦截标注 @TenantFiltered 的方法
2. 从 TenantContextHolder.getTenantId() 获取当前租户
3. 如果方法参数中有 Map<String,Object> body（POST/PUT场景）→ 自动注入 "tenant_id": xxx
4. 如果GET请求 → 在JdbcTemplate查询前置注入 WHERE tenant_id=? 到SQL
5. 如果tenantId为null（admin/system级操作）→ 放行不过滤
```

### MVP简化版（如果AOP复杂度过高）

不搞注解AOP——直接改 `JdbcTemplate` 包装类：

**改文件**: `databridge-common/.../TenantAwareJdbcTemplate.java` — 新建
```java
// 包装 Spring JdbcTemplate，在query/update执行前检测SQL是否包含需隔离的表名
// 如果是 → 自动追加 "AND tenant_id = ?" 并绑定 TenantContextHolder.getTenantId()
```

然后在 `SysManRuntimeConfig` 中替换默认 JdbcTemplate Bean为 TenantAwareJdbcTemplate。

### 验收

```bash
TOKEN_A=$(curl ... admin登录 tenant-a)
TOKEN_B=$(curl ... 运营登录 tenant-b)

# 1. tenant-a创建一个Object
curl -s -X POST http://localhost:8080/api/v1/ecos/objects/project \
  -H "Authorization: Bearer ***  -H "Content-Type: application/json" \
  -d '{"name":"浙北路桥","budget":830000000}' | jq '.data.id'
# 期望: 200 + objectId

# 2. tenant-b查询Object列表 → 应该看不到tenant-a的数据
curl -s http://localhost:8080/api/v1/ecos/objects/project \
  -H "Authorization: Bearer *** | jq '.data | length'
# 期望: 0（隔离生效）

# 3. admin跨租户查询（TenantContextHolder为空 → 放行）
curl -s -H "X-Tenant-Id: tenant-a" http://localhost:8080/api/v1/ecos/objects/project \
  -H "Authorization: Bearer *** | jq '.data | length'
# 期望: ≥1（admin通过HTTP头指定租户查看）
```

---

## P1-1: 业务表加tenant_id列（1天）

**目标**: 所有需隔离的业务表加 `tenant_id VARCHAR(32)` 列。Flyway迁移一次性完成。

### V38 DDL

```sql
-- 为已有业务表追加tenant_id列（可空，逐步迁移）
ALTER TABLE ecos_objects            ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
ALTER TABLE ecos_object_links       ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
ALTER TABLE ecos_dq_rule            ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
ALTER TABLE ecos_pipeline_definition ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
ALTER TABLE ecos_pipeline_execution ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
ALTER TABLE ecos_workflow_instance  ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
ALTER TABLE ecos_ontology_entity    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);
ALTER TABLE ecos_glossary_term      ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(32);

-- 已有数据填入默认租户
UPDATE ecos_objects SET tenant_id = 'tenant-a' WHERE tenant_id IS NULL;
UPDATE ecos_dq_rule SET tenant_id = 'tenant-a' WHERE tenant_id IS NULL;
-- ... 其他表同理

-- 建索引加速tenant过滤
CREATE INDEX IF NOT EXISTS idx_objects_tenant ON ecos_objects(tenant_id);
CREATE INDEX IF NOT EXISTS idx_dq_rule_tenant ON ecos_dq_rule(tenant_id);
```

### 验收

```bash
PGPASSWORD=root psql -h localhost -U root -d sys_man \
  -c "SELECT tenant_id, COUNT(*) FROM ecos_objects GROUP BY tenant_id"
# 期望: tenant-a | ≥1
```

---

## P1-2: 配额闭环（1天）

**目标**: 限制每个租户的最大用户数、最大存储量。不在`QuotaFilter`做（那是API限流），在对应Controller的创建方法中加check。

### 任务

| ID | 任务 | 文件 | 工作量 |
|:--|------|------|:--:|
| P1-2.1 | UserController创建时检查maxUsers | `UserController.java` — `POST`方法加配额查询 | 0.5d |
| P1-2.2 | ObjectController创建时检查maxStorage | `ObjectController.java` — 统计当前存储量 vs max_storage_mb | 0.5d |

### 验收

```bash
# 1. 设置maxUsers=2
curl -s -X PUT http://localhost:8080/api/v1/system/tenants/xinke/quota \
  -H "Authorization: Bearer *** ...

# 2. 创建2个用户 → 第3个应被拒绝
# 期望: 前2个201，第3个400 + "用户数已达租户配额上限"
```

---

## 资源与排期

| 轨道 | 内容 | 工时 | 依赖 |
|------|------|:--:|:--:|
| P0-1 | 统一数据表 | 2d | — |
| P0-2 | JWT集成tenant_id | 2d | 可与P0-1并行 |
| P0-3 | 行级过滤拦截器 | 3d | ⚠️ 需等P0-2（要用TenantContextHolder） |
| P1-1 | 业务表加列+索引 | 1d | 可与P0-3并行 |
| P1-2 | 配额闭环 | 1d | 需等P0-3 |

```
Week 1:  P0-1 (BE) ∥ P0-2 (BE) ∥ P1-1 (BE)     — 表统一 + JWT + DDL
Week 2:  P0-3 (BE) ∥ P1-2 (BE)                 — 行过滤核心 + 配额
```

**总工期**: 2周 | **交付物**: 7个commit + curl全部端点验证

---

## 验收方式

肖总亲自检查：
1. `git log --since="2026-06-29"` — 看提交节奏
2. 逐条执行上述curl验收命令
3. 浏览器验证: 
   - 创建tenant-a和tenant-b
   - tenant-a创建3个项目 → tenant-b登录看不到tenant-a的数据
   - admin用户不带X-Tenant-Id头 → 看到全量
   - 设置maxUsers=2 → 第3个用户创建被拒
