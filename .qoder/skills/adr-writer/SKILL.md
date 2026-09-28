---
name: adr-writer
description: "架构决策记录（ADR）编写：记录重要技术决策及上下文、理由和后果。L3 工作流必选，带技术负责人审批门。当用户说'写 ADR'、'架构决策'、'记录技术决策'时触发。"
version: 2.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [adr, architecture-decision, decision-record, technical-choice, gate]
    related_skills: [arch-design, api-design, db-design]
    artifact_type: ADR
    workflow_modes: [L3]
---

# ADR Writer Skill (v2 — 技术负责人审批门版)

## 核心原则

ADR 是 L3 工作流的必选制品，每条 ADR 必须经过技术负责人审批门（Gate）后才视为正式决策记录。ADR 的质量检查表和格式已有良好基础，新增审批门确保决策被正式确认。

## 关键机制

### 审批门

| 门禁 | 阶段 | 停止条件 |
|------|------|---------|
| **Gate** | ADR 编写完成 | 技术负责人审批（APPROVED），deliverable_allowed=true |

**硬门槛**：未经 Gate 确认，ADR 不得作为 ARCH_SPEC 的输入。

## 触发条件

- 用户说"写 ADR"、"架构决策记录"、"记录技术决策"
- PM 分发任务时指定 `role: arch`，工作流为 L3（L2 可跳过）
- 重要技术选型需要正式记录时（如引入新框架、改变数据模型、调整部署架构）

## 输入

- **必需**：ARCH_SPEC、OpenAPI、DDL 的草稿或已批准版本
- **必需**：技术选型背景、团队约束、成本估算
- **可选**：历史 ADR、技术博客、社区讨论
- **固定约束**：项目版本（来自 artifact_ref）

## 输出制品

- **ADR**：架构决策记录（artifact_type: ADR）
  - 每条 ADR 独立文件
  - 统一格式，便于检索
- **ADR_APPROVAL_RECORD**：ADR 批准记录（artifact_type: APPROVAL_RECORD）

## ADR 格式（Markdown）

每条 ADR 格式：

```markdown
# ADR-{序号}: {决策标题}

**日期**: {YYYY-MM-DD}
**状态**: {提议中 | 已接受 | 已废弃 | 已替换}
**上下文**: {决策背景和需要解决的问题}
**决策**: {我们决定做什么}
**理由**: {为什么做这个决定}
**后果**: {这个决定带来的正面和负面影响}
**模块归属**: {ARCH_SPEC 模块名称}
**相关制品**: ARCH_SPEC@{hash}, OpenAPI@{hash}

---

## 详细说明

### 背景

{问题的详细描述，为什么这个问题需要解决}

### 选项对比

| 选项 | 优点 | 缺点 | 成本 | 风险 |
|------|------|------|------|------|
| 选项A | ... | ... | ... | ... |
| 选项B | ... | ... | ... | ... |
| 选项C（选择） | ... | ... | ... | ... |

### 决定

{详细的决策描述}

### 影响

**正面影响**：
- {列出正面影响}

**负面影响**：
- {列出负面影响}

### 弃用说明（仅当状态为"已废弃"或"已替换"时）

{说明为什么废弃，以及替代方案是什么}
```

## 执行步骤

### Step 1: 识别需要记录的决策

从 ARCH_SPEC 和技术选型中提取，以下场景必须记录 ADR：

```
必须写 ADR 的场景：
1. 引入新框架/库（Spring Boot vs FastAPI）
2. 数据库选型（MySQL vs PostgreSQL vs MongoDB）
3. 认证方案（JWT vs Session）
4. 缓存策略（Redis vs Memcached）
5. 消息队列选型（Kafka vs RabbitMQ）
6. 微服务 vs 单体
7. 第三方服务选型（支付/短信/推送）
8. 索引策略（数据库索引 vs 搜索引擎）
9. 文件存储方案（本地 vs OSS vs S3）
10. 日志方案（ELK vs Loki vs 云服务）
```

### Step 2: 填写 ADR 模板（带模块归属）

每条 ADR 必须包含：

```
1. 序号：从 001 开始，逐条递增
2. 标题：简洁，描述决策内容
3. 日期：决策日期
4. 状态变更历史
5. 上下文：清晰的背景描述
6. 决策：明确的选择
7. 理由：基于什么考虑
8. 后果：短期和长期影响
9. 模块归属：该决策影响哪个 ARCH_SPEC 模块
10. 相关制品引用：ARCH_SPEC@{hash}, OpenAPI@{hash}
```

### Step 3: 评估决策质量

检查 ADR 是否符合以下标准：

```
好的 ADR：
✓ 有清晰的上下文和背景
✓ 列举了多个选项并做了对比
✓ 有明确的理由支撑决策
✓ 列出了正面和负面影响
✓ 影响是可量化的
✓ 有模块归属和制品引用

差的 ADR：
✗ 只有结论，没有上下文
✗ 只列举了一个选项
✗ 理由模糊（"因为大家都这样用"）
✗ 没有列出负面影响
✗ 决策无法回滚
✗ 无模块归属
```

### Step 4: 编号与文件组织

```
ADR 文件命名：ADR-{序号}_{简短标题}.md
ADR-{001}_database-choice.md
ADR-{002}_authentication-strategy.md

ADR 汇总索引：docs/architecture/adr/ADR_INDEX.md
```

ADR_INDEX.md 格式：

```markdown
# ADR 索引

| 序号 | 标题 | 日期 | 状态 | 模块归属 |
|------|------|------|------|---------|
| ADR-001 | 数据库选型：MySQL 5.7 | 2024-01-15 | 已接受 | 数据层 |
| ADR-002 | 认证策略：JWT | 2024-01-16 | 已接受 | user-service |
| ADR-003 | 缓存方案：Redis Cluster | 2024-01-18 | 已接受 | 数据层 |

---

## 详细内容

- [ADR-001](./ADR-001_database-choice.md)
- [ADR-002](./ADR-002_authentication-strategy.md)
- [ADR-003](./ADR-003_cache-strategy.md)
```

### Step 5: 技术负责人审批门

```markdown
---

**【ADR 审批确认门】**

请确认以下内容：
1. ADR 格式是否完整（序号/标题/日期/状态/上下文/决策/理由/后果）？
2. 选项对比是否充分（至少 2 个选项）？
3. 决策理由是否有说服力（不是"社区流行"这种理由）？
4. 正面和负面影响是否都已列出？
5. 模块归属是否正确（影响哪个 ARCH_SPEC 模块）？
6. ADR 之间是否有引用关系（形成知识网络）？

**审批人**：技术负责人

**批准结果**：
- APPROVED → 状态改为"已接受"，生成 Hash，可作为 ARCH_SPEC 输入
- REJECTED → 打回修改
```

### Step 6: 生成 APPROVAL_RECORD

审批通过后，生成 `ADR_APPROVAL_RECORD`（按 ADR 文件）：

```json
{
  "artifact": "ADR",
  "name": "ADR-{序号}_{标题}",
  "version": "v{version}",
  "hash": "{内容哈希}",
  "status": "ACCEPTED",
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
  "module_ref": "{ARCH_SPEC 模块名称}",
  "related_artifacts": [
    "ARCH_SPEC@{arch_hash}",
    "OPENAPI@{openapi_hash}"
  ],
  "prev_version": null,
  "next_version": null
}
```

---

## 示例 ADR（带模块归属）

### ADR-001: 数据库选型 MySQL 5.7

```markdown
# ADR-001: 数据库选型 MySQL 5.7

**日期**: 2024-01-15
**状态**: 已接受
**上下文**: 订单系统需要存储用户、订单、商品数据，要求事务一致性，预期 QPS 1000，预计 3 年数据量 1000 万条。
**决策**: 使用 MySQL 5.7 + InnoDB 引擎，不使用读写分离和分库分表。
**理由**:
1. 团队 MySQL 经验 3 年，运维成本低
2. InnoDB 支持行级锁和事务，订单扣减库存需要强一致性
3. 单表 1000 万数据在加索引后查询仍可控制在 10ms 以内
4. MySQL 5.7 支持 JSON 类型，便于扩展字段
5. 成本：云厂商 RDS MySQL 最低配置 2000元/月
**后果**:
- 正面：开发周期短，技术风险低，一致性有保障
- 负面：单实例 QPS 上限约 5000，扩缩容需要手动迁移
- 负面：如果 QPS 超过 5000 需要引入 ProxySQL 做读写分离

**模块归属**: 数据层
**相关制品**: ARCH_SPEC@{hash}, OpenAPI@{hash}

---

## 详细说明

### 背景

订单系统是核心业务，对数据一致性要求高。初期预估 QPS 1000，但营销活动可能脉冲式上涨到 5000。

### 选项对比

| 选项 | 优点 | 缺点 | 成本 | 风险 |
|------|------|------|------|------|
| MySQL 单机 | 运维简单，一致性好 | QPS 上限 5000 | 2000元/月 | 中（需要监控） |
| MySQL 读写分离 | 可扩展读性能 | 延迟，主从一致性问题 | 4000元/月 | 中（延迟 1-5ms） |
| PostgreSQL | JSON 支持好，扩展性强 | 团队经验少 | 3000元/月 | 高（学习成本） |
| MongoDB | 文档型，扩展性好 | 事务支持弱 | 3500元/月 | 高（一致性问题） |

### 决定

选择 MySQL 5.7 单机，当前阶段不加读写分离。理由：
- 当前 QPS 1000，远未达到瓶颈
- 读写分离引入的主从延迟在促销场景可能影响用户体验
- 监控告警覆盖 QPS，当超过 3000 时提前预警，为迁移留出时间

### 影响

**正面影响**：
- 开发周期预计 2 周，比其他方案快 1 周
- 运维简单，团队可自主 DBA

**负面影响**：
- QPS 上限 5000，需要监控预警
- 扩容需要应用层改造，不能透明扩展
```

---

## 验证步骤

1. [ ] L3 工作流：ADR 必须执行（L2 可跳过）
2. [ ] 每条 ADR 有模块归属（指向 ARCH_SPEC 模块）
3. [ ] 每条 ADR 有相关制品引用（ARCH_SPEC, OpenAPI）
4. [ ] ADR 格式完整（上下文/决策/理由/后果）
5. [ ] 选项对比充分（至少 2 个选项）
6. [ ] 技术负责人审批门已通过（Gate-1 APPROVED）
7. [ ] ADR_APPROVAL_RECORD 已生成，deliverable_allowed=true
8. [ ] ADR 索引是最新的

## 保存位置

- 每条 ADR：`docs/architecture/adr/ADR-{nnn}_{title}.md`
- ADR 索引：`docs/architecture/adr/ADR_INDEX.md`
- 批准记录：`docs/architecture/adr/ADR-{nnn}_{title}-approval-record.json`

## 常见陷阱

1. **决策后补 ADR**：ADR 要在决策前写，用于记录决策过程，而非决策后补
2. **过于笼统**：ADR-001"使用微服务架构"太宽泛，应该分拆
3. **理由不充分**：仅写"社区流行"不够，需要结合团队情况
4. **不写负面影响**：每个决策都有代价，不写不代表没有
5. **ADR 孤岛**：ADR 之间应该有引用关系，形成知识网络
6. **跳过审批门**：ADR 未审批就作为 ARCH_SPEC 输入