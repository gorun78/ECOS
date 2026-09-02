---
name: db-design
description: "数据库设计：从 OpenAPI 和 ARCH_SPEC 输出 DDL。必须追溯到 OpenAPI Schema 和 ARCH_SPEC 模块，带评审确认门。当用户说'设计数据库'、'出 DDL'、'数据库设计'时触发。"
version: 2.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [database-design, ddl, sql, schema, index, foreign-key, traceability, gate]
    related_skills: [arch-design, api-design]
    artifact_type: DDL
    workflow_modes: [L2, L3]
---

# DB Design Skill (v2 — API 追溯 + 评审确认门版)

## 核心原则

每个数据库表必须可追溯到 OpenAPI Schema（API 数据模型）和 ARCH_SPEC 模块。DDL 完成后必须经过评审确认门（Gate），技术负责人批准后才可分发给 Fullstack。

## 关键机制

### 表 → OpenAPI Schema → ARCH_SPEC 模块追溯

追溯链：`DDL 表 → OpenAPI Schema → ARCH_SPEC 模块 → PRD 功能`

```markdown
## 表 → API/Schema → 模块 → PRD 追溯表

| 表名 | 描述 | OpenAPI Schema | 对应模块 | 覆盖 PRD 功能 |
|------|------|---------------|---------|-------------|
| usr_users | 用户表 | User | user-service | F1-用户注册, F2-登录, F3-个人中心 |
| ord_orders | 订单表 | Order | order-service | F4-下单, F5-支付, F6-取消退款 |
| ord_order_items | 订单明细表 | OrderItem | order-service | F4-下单 |
```

### 评审确认门

| 门禁 | 阶段 | 停止条件 |
|------|------|---------|
| **Gate** | DDL 初稿完成 | 技术负责人审批（APPROVED），deliverable_allowed=true |

**硬门槛**：未经 Gate 确认，DDL 不得分发给 Fullstack。

## 触发条件

- 用户说"设计数据库"、"出 DDL"、"数据库设计"
- PM 分发任务时指定 `role: arch`，需要输出 DDL
- OpenAPI 和 ARCH_SPEC 必须 APPROVED

## 输入

- **必需**：OpenAPI 规范（已批准，artifact_ref，状态 APPROVED）
- **必需**：ARCH_SPEC（已批准，artifact_ref，状态 APPROVED）
- **可选**：现有数据库 Schema、历史 DDL
- **固定约束**：数据库类型（MySQL/PostgreSQL）、字符集、排序规则（来自 artifact_ref）

## 输出制品

- **DDL**：数据库定义脚本（artifact_type: DDL）
- **DDL_APPROVAL_RECORD**：DDL 批准记录（artifact_type: APPROVAL_RECORD）

## 执行步骤

### Step 0: 前置校验 — OpenAPI + ARCH_SPEC 批准记录检查

```python
def validate_inputs_for_ddl(openapi_ref, arch_ref):
    """DDL 设计前，必须校验 OpenAPI 和 ARCH_SPEC 已 APPROVED"""
    openapi_record = read_artifact_approval_record(openapi_ref)
    if not openapi_record or openapi_record["status"] != "APPROVED":
        raise ValueError(f"OpenAPI {openapi_ref} 未 APPROVED，DDL 设计禁止开始")

    arch_record = read_artifact_approval_record(arch_ref)
    if not arch_record or arch_record["status"] != "APPROVED":
        raise ValueError(f"ARCH_SPEC {arch_ref} 未 APPROVED，DDL 设计禁止开始")

    return {
        "openapi_version": openapi_record["version"],
        "openapi_hash": openapi_record["hash"],
        "arch_version": arch_record["version"],
        "arch_hash": arch_record["hash"],
        "modules": arch_record["modules"]
    }
```

```markdown
## 前置校验

收到 DDL 设计请求，校验以下前提条件：

1. [ ] OpenAPI 状态为 APPROVED ✅
2. [ ] ARCH_SPEC 状态为 APPROVED ✅
3. [ ] 两者的 deliverable_allowed = true ✅

当前输入：
- OpenAPI：v{version} @ {hash}
- ARCH_SPEC：v{version} @ {hash}

→ 校验通过，可开始 DDL 设计
```

---

### Step 1: 从 OpenAPI Schema 提取数据实体（带 API 追溯）

1. 读取 OpenAPI 的 Schema 定义，确认所有数据模型
2. 提取每个 Schema 的字段

```yaml
# 示例：User Schema（来自 OpenAPI）
User:
  id: uuid (PK)
  email: string (unique)
  name: string
  status: enum(active/inactive)
  createdAt: datetime
  updatedAt: datetime
```

3. 识别实体间关系：
   - 一对一：`user` ↔ `user_profile`
   - 一对多：`user` → `order`（一个用户多个订单）
   - 多对多：`order` ↔ `product`（通过中间表 `order_item`）

4. 建立追溯映射：

```python
def build_table_api_mapping(openapi, arch_spec):
    """
    建立 表 → OpenAPI Schema → 模块 → PRD 追溯映射
    """
    mapping = []
    schemas = openapi["components"]["schemas"]

    for table_name, schema in schemas.items():
        # 找到该 Schema 对应的 API（在哪个模块下定义）
        module = find_schema_module(openapi, table_name)
        # 找到该模块对应的 PRD 功能
        prd_functions = find_module_prd_functions(arch_spec, module)

        mapping.append({
            "table": table_name,
            "schema": schema,
            "module": module,
            "prd_functions": prd_functions
        })

    return mapping
```

输出追溯表：

```markdown
## 表 → API/Schema → 模块 → PRD 追溯表

| 表名 | 描述 | OpenAPI Schema | 对应模块 | 覆盖 PRD 功能 |
|------|------|---------------|---------|-------------|
| usr_users | 用户表 | User | user-service | F1-用户注册, F2-登录, F3-个人中心 |
| ord_orders | 订单表 | Order | order-service | F4-下单, F5-支付, F6-取消退款 |
| ord_order_items | 订单明细表 | OrderItem | order-service | F4-下单 |
| pdt_products | 商品表 | Product | product-service | F7-商品浏览, F8-商品搜索 |
```

---

### Step 2: 设计表结构（带追溯注释）

#### 命名规范

```
表命名：
- 小写 + 下划线：user_accounts 而非 UserAccounts
- 单词用复数：users 而非 user
- 加前缀区分模块：ord_orders（订单模块）, usr_users（用户模块）

字段命名：
- id：主键（user_id, order_id）
- created_at / updated_at / deleted_at：时间戳
- status / type / category：状态/类型字段
- is_deleted：软删除标记
- version：乐观锁版本号

外键命名：
- fk_{表名1}_{表名2}：fk_orders_users
```

#### 字段类型选择

| 场景 | MySQL 类型 | PostgreSQL 类型 |
|------|-----------|----------------|
| 短文本(≤255) | VARCHAR(255) | VARCHAR(255) |
| 长文本 | TEXT | TEXT |
| UUID | CHAR(36) | UUID |
| 整数 ID | BIGINT UNSIGNED | BIGINT |
| 金额 | DECIMAL(12,2) | DECIMAL(12,2) |
| 日期 | DATE | DATE |
| 时间 | DATETIME(3) | TIMESTAMP(3) |
| JSON | JSON | JSONB |
| 枚举 | ENUM(...) | VARCHAR 或自定义类型 |

#### 表结构模板（带追溯注释）

```sql
-- =============================================
-- 表名：usr_users
-- 描述：用户表
-- 作者：Arch Agent
-- 版本：v1.0.0
-- 追溯：OpenAPI Schema: User, 模块: user-service, PRD: F1/F2/F3
-- 关联制品：OPENAPI@{hash}, ARCH_SPEC@{hash}
-- =============================================

CREATE TABLE `usr_users` (
  `id`           CHAR(36)      NOT NULL  COMMENT '用户ID，UUID',
  `email`        VARCHAR(255)  NOT NULL  COMMENT '邮箱，唯一',
  `name`         VARCHAR(100)  NOT NULL  COMMENT '姓名',
  `password`     VARCHAR(255)  NOT NULL  COMMENT '密码哈希',
  `status`       TINYINT       NOT NULL  DEFAULT 1 COMMENT '状态：1-正常 2-禁用',
  `avatar`       VARCHAR(500)            COMMENT '头像URL',
  `last_login_at` DATETIME(3)            COMMENT '最后登录时间',
  `created_at`   DATETIME(3)  NOT NULL  DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`   DATETIME(3)  NOT NULL  DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `deleted_at`   DATETIME(3)            COMMENT '删除时间（软删除）',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email` (`email`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- =============================================
-- 表名：ord_orders
-- 描述：订单表
-- 追溯：OpenAPI Schema: Order, 模块: order-service, PRD: F4/F5/F6
-- =============================================

CREATE TABLE `ord_orders` (
  `id`              CHAR(36)      NOT NULL  COMMENT '订单ID，UUID',
  `user_id`         CHAR(36)      NOT NULL  COMMENT '用户ID',
  `order_no`        VARCHAR(32)   NOT NULL  COMMENT '订单号，唯一',
  `total_amount`    DECIMAL(12,2) NOT NULL  DEFAULT 0 COMMENT '订单总额',
  `status`          TINYINT       NOT NULL  DEFAULT 1 COMMENT '状态：1-待支付 2-已支付 3-已取消 4-已退款',
  `paid_at`         DATETIME(3)            COMMENT '支付时间',
  `created_at`      DATETIME(3)  NOT NULL  DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at`      DATETIME(3)  NOT NULL  DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`),

  CONSTRAINT `fk_orders_users` FOREIGN KEY (`user_id`) REFERENCES `usr_users` (`id`)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';
```

---

### Step 3: 设计索引

```markdown
## 索引设计原则

### 需要建索引的场景
1. 主键自动有索引
2. WHERE 条件中出现的字段：status, user_id, created_at
3. ORDER BY 字段：created_at, updated_at
4. JOIN 条件字段：user_id, order_id
5. UNIQUE 约束字段：email, order_no

### 不要建索引的场景
1. 字段基数很低（只有 0/1 两种值）：不适合建索引
2. 频繁更新的字段：维护成本高
3. 字符串长度超过 255：考虑前缀索引
```

```sql
-- L2 简化索引（单列索引为主）
CREATE INDEX idx_user_id ON ord_orders(user_id);
CREATE INDEX idx_status ON ord_orders(status);

-- L3 完整索引（复合索引）
CREATE INDEX idx_user_status_created ON ord_orders(user_id, status, created_at DESC);
```

---

### Step 4: 软删除 vs 硬删除

```sql
-- 软删除（推荐，用于关键业务数据）
-- deleted_at IS NULL 表示未删除
-- 查询时自动过滤：WHERE deleted_at IS NULL

-- 硬删除（仅用于无关紧要的数据，如日志、临时表）
-- DROP TABLE 或 DELETE FROM
```

---

### Step 5: 数据迁移策略（L3）

```sql
-- 增加字段（向后兼容）
ALTER TABLE usr_users ADD COLUMN `phone` VARCHAR(20) COMMENT '手机号' AFTER `email`;

-- 增加 NOT NULL 字段（需要先设默认值）
ALTER TABLE usr_users ADD COLUMN `source` TINYINT NOT NULL DEFAULT 1 COMMENT '来源：1-Web 2-App';

-- 创建索引（不锁表，MySQL 5.6+）
ALTER TABLE ord_orders ADD INDEX `idx_paid_at` (`paid_at`);
-- 或在线创建（不阻塞读写）
ALTER TABLE ord_orders ADD INDEX `idx_paid_at` (`paid_at`), ALGORITHM=INPLACE, LOCK=NONE;
```

---

### Step 6: 评审确认门

```markdown
---

**【DDL 设计评审确认门】**

请确认以下内容：
1. 每个表都有 OpenAPI Schema 来源追溯？
2. 每个表都有模块归属（对应 ARCH_SPEC 模块）？
3. 外键关系是否正确（无循环外键）？
4. 索引设计是否覆盖常见查询场景？
5. 命名规范是否统一？
6. DDL 语法是否正确（mysql -u <user> -p<pwd> < ddl.sql 验证）？

**审批人**：技术负责人（或 DBA）

**批准结果**：
- APPROVED → 状态改为 APPROVED，生成 Hash，分发给 Fullstack
- REJECTED → 打回修改
```

---

### Step 7: 生成 APPROVAL_RECORD

```json
{
  "artifact": "DDL",
  "name": "{系统名称} 数据库设计",
  "version": "v{version}",
  "hash": "{内容哈希}",
  "status": "APPROVED",
  "approvals": [
    {
      "role": "技术负责人",
      "result": "APPROVED",
      "timestamp": "{timestamp}",
      "conditions": []
    }
  ],
  "gates_passed": ["Gate-1"],
  "deliverable_allowed": true,
  "openapi_ref": "OPENAPI@{openapi_hash}",
  "arch_ref": "ARCH_SPEC@{arch_hash}",
  "prd_ref": "PRD@{prd_hash}",
  "table_counts": {n},
  "traceability": {
    "all_tables_have_schema_ref": true,
    "all_tables_have_module_ref": true,
    "orphan_tables": 0
  },
  "prev_version": null,
  "next_version": null
}
```

---

## 验证步骤

1. [ ] OpenAPI 和 ARCH_SPEC 均 APPROVED
2. [ ] 每个表有 `OpenAPI Schema` 来源追溯（表头注释）
3. [ ] 每个表有 `ARCH_SPEC 模块` 归属（表头注释）
4. [ ] DDL 语法验证通过
5. [ ] 评审确认门已通过（Gate-1 APPROVED）
6. [ ] DDL_APPROVAL_RECORD 已生成，deliverable_allowed=true
7. [ ] 所有 OpenAPI Schema 都有对应表（无遗漏）
8. [ ] 外键关系无循环

## 保存位置

- `docs/architecture/ddl_v{version}.sql`
- 批准记录：`docs/architecture/ddl_v{version}-approval-record.json`

## 常见陷阱

1. **表无 OpenAPI Schema 来源**：无法追溯该表对应哪个 API 的数据模型
2. **表无模块归属**：无法追溯该表对应 ARCH_SPEC 哪个模块
3. **外键滥用**：微服务架构避免跨库外键，用业务逻辑代替
4. **索引过多**：每个查询都加索引，写入性能下降
5. **忘记软删除**：关键数据不要硬删除，否则无法追溯
6. **跳过评审门**：DDL 未审批就分发给 Fullstack