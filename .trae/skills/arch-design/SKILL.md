---
name: arch-design
description: "系统架构设计：从 PRD 和原型输出 ARCH_SPEC（架构方案文档）。覆盖模块边界、技术选型、部署架构、风险清单、PRD 功能追溯。2 阶段确认门禁。当用户说'设计架构'、'出架构方案'时触发。"
version: 2.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [architecture, system-design, arch-spec, module-boundary, deployment, gate, traceability]
    related_skills: [api-design, db-design, adr-writer]
    artifact_type: ARCH_SPEC
    workflow_modes: [L2, L3]
---

# Arch Design Skill (v2 — 2 阶段门禁 + PRD 追溯版)

## 核心原则

每个 Arch 制品（ARCH_SPEC / OpenAPI / DDL）必须有批准记录（APPROVAL_RECORD），所有模块必须有 PRD 功能来源追溯。架构产出分两阶段确认，第二门通过后分发 Fullstack。

## 关键机制

### 2 阶段确认门禁

| 门禁 | 阶段 | 停止条件 |
|------|------|---------|
| **第一门** | 模块划分 + 技术选型完成 | 用户确认"模块划分合理、技术选型合适" |
| **第二门** | 完整 ARCH_SPEC 完成 | 技术负责人批准（APPROVED），deliverable_allowed=true |

### PRD 追溯机制

每个模块必须指向 PRD 功能 ID：

```markdown
## 模块 → PRD 追溯表

| 模块 | 职责 | 技术选型 | 覆盖 PRD 功能 |
|------|------|---------|-------------|
| user-service | 用户管理 | Spring Boot + MySQL + Redis | F1-用户注册, F2-登录, F3-个人中心 |
| order-service | 订单管理 | Spring Boot + MySQL + Kafka | F4-下单, F5-支付, F6-取消退款 |
```

追溯链：`PRD 功能 → ARCH_SPEC 模块 → OpenAPI 接口 → DDL 表`

## 触发条件

- 用户说"设计架构"、"出架构方案"、"架构设计"
- PM 分发任务时指定 `role: arch`，工作流为 L2 或 L3
- PRD 必须 APPROVED（deliverable_allowed=true）

## 输入

- **必需**：已批准的 PRD（artifact_ref，状态 APPROVED）、产品原型、验收标准
- **可选**：UI 规范、现有系统架构、历史 ADR、技术约束
- **固定约束**：技术栈、安全规范、租户隔离规则、项目版本（来自 artifact_ref）

## 输出制品

- **ARCH_SPEC**：架构方案文档（artifact_type: ARCH_SPEC）
- **ARCH_SPEC_APPROVAL_RECORD**：架构批准记录（artifact_type: APPROVAL_RECORD）

## 执行步骤

### Step 0: 前置校验 — PRD 批准记录检查

```python
def validate_prd_for_arch(prd_ref):
    """Arch 开始前，必须校验 PRD 已 APPROVED"""
    approval_record = read_artifact_approval_record(prd_ref)
    if not approval_record:
        raise ValueError(f"PRD {prd_ref} 无批准记录，Arch 禁止开始")
    if approval_record["status"] != "APPROVED":
        raise ValueError(f"PRD 状态为 {approval_record['status']}，必须 APPROVED 才能开始架构设计")
    if not approval_record.get("deliverable_allowed"):
        raise ValueError("PRD deliverable_allowed=false，禁止开始架构设计")
    return {
        "prd_name": approval_record["name"],
        "prd_version": approval_record["version"],
        "prd_hash": approval_record["hash"],
        "prd_functions": extract_functions_from_prd(prd_ref)
    }
```

```markdown
## PRD 校验

收到架构设计请求，校验以下前提条件：

1. [ ] PRD 状态为 APPROVED ✅
2. [ ] PRD 有批准记录（PRD_APPROVAL_RECORD）✅
3. [ ] PRD 的 deliverable_allowed = true ✅

当前 PRD：
- 名称：{name}
- 版本：{version}
- Hash：{hash}
- 状态：APPROVED
- 功能数量：{n} 个（P0: {n}, P1: {n}, P2: {n}）

→ PRD 校验通过，可开始架构设计
```

---

## 第一阶段：模块划分 + 技术选型

### Step 1: 分析需求边界

1. 读取 PRD，确认：
   - 核心业务流程（追溯到 PRD 功能 ID）
   - 用户角色和权限
   - 数据敏感程度
   - 性能要求（并发、响应时间）
   - 集成需求（第三方 API、Webhook）

2. 读取原型，确认：
   - 页面数量和复杂度
   - 页面间数据流转
   - 前端状态管理需求

### Step 2: 划分模块边界（带 PRD 追溯）

按以下原则划分模块，每个模块必须能指向 PRD 功能：

```
原则：
1. 单一职责：每个模块只负责一个业务领域
2. 低耦合：模块间通过接口通信，不直接依赖对方实现
3. 高内聚：模块内的类/函数紧密相关，共同完成一个功能
4. PRD 覆盖：每个 PRD 功能必须落入某个模块（无遗漏）
```

输出模块划分表（含追溯）：

```markdown
## 模块边界 + PRD 追溯

| 模块 | 职责 | 技术栈 | 边界接口 | 覆盖 PRD 功能 |
|------|------|--------|---------|-------------|
| user-service | 用户管理 | Spring Boot | REST API | F1-用户注册, F2-登录, F3-个人中心 |
| order-service | 订单管理 | Spring Boot | REST API + 消息队列 | F4-下单, F5-支付, F6-取消退款 |
| product-service | 商品管理 | Spring Boot | REST API | F7-商品浏览, F8-商品搜索 |
| payment-service | 支付服务 | Spring Boot | REST API + 回调 | F5-支付 |
```

### Step 3: 技术选型

对每个模块，回答：

```
1. 前端框架：Vue3 / React / Angular？（考虑团队熟悉度 + 生态）
2. 后端框架：Spring Boot / FastAPI / NestJS？（考虑性能 + 团队熟悉度）
3. 数据库：MySQL / PostgreSQL / MongoDB？（考虑数据结构 + 一致性要求）
4. 缓存：Redis / Memcached？（考虑访问模式）
5. 消息队列：Kafka / RabbitMQ / RocketMQ？（考虑吞吐量 + 顺序性）
6. 搜索引擎：Elasticsearch？（考虑搜索复杂度）
7. 对象存储：OSS / S3？（考虑成本 + 地区）
```

选型理由格式：

```markdown
## 技术选型

### 前端框架
- **选择**：Vue3 + Vite + TypeScript
- **理由**：
  - 团队 Vue3 经验 2 年，上手快
  - Composition API 支持更好的逻辑复用
  - Vite 开发体验好，热更新快
  - TypeScript 类型安全，减少运行时错误
- **替代方案考虑**：
  - React：团队 React 经验少，学习成本高
  - Svelte：生态不如 Vue3 完善
```

### Step 4: 第一门禁确认

```markdown
---

**【第一门禁 — 模块划分 + 技术选型确认】**

请确认以下内容：
1. 模块划分是否覆盖了所有 PRD 功能（无遗漏）？
2. 每个模块的职责是否清晰，边界是否清晰？
3. 技术选型是否合适，团队是否有能力驾驭？
4. 模块间的接口和依赖关系是否合理？
5. PRD 功能 → 模块追溯关系是否准确？

**确认后输出**：第一门已确认，进入完整 ARCH_SPEC 编写阶段。
**未确认**：请指出需修改的内容，修订后重新确认。
```

第一门禁通过后，生成阶段记录：

```json
{
  "gate": "Gate-1",
  "name": "模块划分 + 技术选型确认",
  "status": "CONFIRMED",
  "timestamp": "{timestamp}",
  "confirmed_by": "用户",
  "modules_count": {n},
  "prd_coverage": "100%"
}
```

---

## 第二阶段：完整 ARCH_SPEC 编写

### Step 5: 设计部署架构

描述系统如何部署：

```markdown
## 部署架构

### L3 完整部署
```
                    ┌─────────────┐
                    │   CDN       │
                    │ (静态资源)  │
                    └──────┬──────┘
                           │
                    ┌──────▼──────┐
                    │  负载均衡    │
                    │ (Nginx/ELB) │
                    └──────┬──────┘
                           │
         ┌─────────────────┼─────────────────┐
         │                 │                 │
  ┌──────▼──────┐  ┌──────▼──────┐  ┌──────▼──────┐
  │  网关层      │  │  服务A       │  │  服务B       │
  │ (Spring     │  │ (user-       │  │ (order-      │
  │  Gateway)   │  │  service)    │  │  service)    │
  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘
         │                 │                 │
  ┌──────▼──────┐  ┌──────▼──────┐  ┌──────▼──────┐
  │  Redis      │  │  MySQL      │  │  Kafka      │
  │ (缓存)      │  │ (主库)       │  │ (消息队列)   │
  └─────────────┘  └─────────────┘  └─────────────┘
```

### L2 简化部署（单服务器）
```
Nginx → Spring Boot (JAR) → MySQL
      → Vue3 前端 (静态资源)
```

### 扩容策略
- 前端：CDN + 静态资源缓存
- 网关：水平扩展 + Nginx 负载均衡
- 后端服务：Kubernetes HPA 自动扩容
- 数据库：主从复制 + 读写分离
```

### Step 6: 风险识别

```markdown
## 风险清单

| 风险ID | 风险描述 | 概率 | 影响 | 缓解措施 | 关联模块 |
|--------|---------|------|------|---------|---------|
| R1 | 高并发下单库存超卖 | 中 | 高 | 乐观锁 + 库存预扣 | order-service |
| R2 | 第三方支付 API 不可用 | 低 | 高 | 异步回调 + 重试机制 | payment-service |
| R3 | 数据库单点故障 | 低 | 高 | 主从切换 + 异常告警 | 数据层 |
| R4 | 前端 SEO 不友好 | 中 | 中 | SSR 或预渲染 | 前端 |
```

### Step 7: 第二门禁 — 完整 ARCH_SPEC 确认

```markdown
---

**【第二门禁 — ARCH_SPEC 完整确认】**

请确认以下内容：
1. 部署架构是否覆盖了所有组件？
2. 模块间的接口契约是否清晰？
3. 风险清单是否完整，缓解措施是否可行？
4. 所有 PRD 功能都有模块覆盖？
5. 技术选型是否最终确定？

**审批人**：技术负责人（或产品负责人 + 架构师联合审批）

**批准结果**：
- APPROVED → 状态改为 APPROVED，生成 Hash，进入 OpenAPI/DDL 设计
- REJECTED → 打回修改，修订后重新提交审批
- CONDITIONAL_APPROVED → 列出条件清单
```

### Step 8: 生成 APPROVAL_RECORD

审批通过后，生成 `ARCH_SPEC_APPROVAL_RECORD`：

```json
{
  "artifact": "ARCH_SPEC",
  "name": "{系统名称}",
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
  "gates_passed": ["Gate-1", "Gate-2"],
  "deliverable_allowed": true,
  "prd_ref": "PRD@{prd_hash}",
  "modules": [
    {
      "name": "user-service",
      "covered_prd_functions": ["F1", "F2", "F3"]
    },
    {
      "name": "order-service",
      "covered_prd_functions": ["F4", "F5", "F6"]
    }
  ],
  "prev_version": null,
  "next_version": null
}
```

## 验证步骤

1. [ ] PRD 校验通过（APPROVED 状态 + APPROVAL_RECORD）
2. [ ] 每个模块有 PRD 功能来源追溯（covered_prd_functions 非空）
3. [ ] 第一门已确认（Gate-1 CONFIRMED）
4. [ ] 第二门已确认（Gate-2 APPROVED）
5. [ ] ARCH_SPEC_APPROVAL_RECORD 已生成，deliverable_allowed=true
6. [ ] 所有引用了 PRD 的地方标注版本哈希
7. [ ] 模块划分覆盖了所有 PRD 功能（无遗漏）
8. [ ] 风险清单的缓解措施与模块关联

## 保存位置

- L3：`docs/architecture/ARCH_SPEC_v{version}.md`
- L2：`docs/architecture/ARCH_SPEC_light_v{version}.md`
- 批准记录：`docs/architecture/ARCH_SPEC_v{version}-approval-record.json`

## 常见陷阱

1. **模块无 PRD 来源**：创建了无法指向 PRD 功能的任务
2. **跳过第一门**：模块划分还没确认就继续写详细设计
3. **技术选型跟风**：不要因为"流行"而选某个技术，要看团队能力和业务匹配度
4. **忽视非功能需求**：性能、安全、可维护性要在架构阶段考虑
5. **模块边界模糊**：如果一个模块的职责无法用一句话描述，说明边界不清

## 参考文档

- `references/collaboration-contract.md`：Arch 与 PM / Reviewer / Fullstack 的工作流适配（L1/L2/L3）、输入输出契约与跨 profile 协作协议（接收校验/状态上报/完成通知/异常上报/保留条款）。SOUL.md 瘦身为人格宪法后迁入此处。