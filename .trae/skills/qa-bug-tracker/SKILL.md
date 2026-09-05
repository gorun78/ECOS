---
name: qa-bug-tracker
description: "QA 缺陷管理 Skill：识别、记录、追踪缺陷，每个缺陷必须可追溯到 PRD 功能来源。输出 BUG_LIST（带 PRD 追溯）和 TEST_FEEDBACK。当测试发现 bug 时触发，或用户说'记录缺陷'、'提 bug'时触发。"
version: 2.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [qa, bug-tracking, defect-management, bug-list, feedback, prd-trace]
    related_skills: [qa-test-planner, qa-test-executor]
    artifact_type: BUG_LIST
    workflow_modes: [L2, L3]
---

# QA Bug Tracker Skill (v2 — PRD 追溯版)

## 核心原则

每个缺陷必须能追溯到 PRD 功能来源，并明确对应的测试用例。每个 BUG_LIST 产出必须生成 `BUG_LIST_APPROVAL_RECORD`。P0 缺陷立即上报，阻断一切交付。

## 关键机制

### BUG → PRD 追溯表

```markdown
## BUG → PRD 追溯表

| Bug ID | 缺陷标题 | 优先级 | PRD 来源 | 对应测试用例 | 状态 |
|--------|---------|--------|---------|-------------|------|
| BUG-001 | 订单金额显示 NaN | P1 | F4.1-下单 | E2E-002 | OPEN |
| BUG-002 | 商品搜索排序错误 | P1 | F3.1-商品搜索 | IT-010 | OPEN |
| BUG-003 | 头像上传失败 | P2 | F6.1-个人信息 | E2E-003 | OPEN |
```

### 缺陷 → PRD 追溯验证

```python
def validate_bug_prd_traceability(bug, prd_functions):
    """
    每个缺陷必须能追溯到 PRD 功能
    """
    prd_ref = bug.get("prd_ref")
    if not prd_ref:
        raise ValueError(f"Bug {bug['id']} 缺少 PRD 追溯字段 prd_ref")

    # 验证 PRD 功能存在
    valid_ids = [f["id"] for f in prd_functions]
    if prd_ref not in valid_ids:
        raise ValueError(f"Bug {bug['id']} 的 prd_ref={prd_ref} 不在 PRD 功能清单中")

    return {
        "bug_id": bug["id"],
        "prd_ref": prd_ref,
        "prd_name": next(f["name"] for f in prd_functions if f["id"] == prd_ref),
        "test_case_ref": bug.get("test_case_ref"),
        "traceable": True
    }
```

## 触发条件

- QA Test Executor 发现测试失败时
- 用户说"记录缺陷"、"提 bug"、"缺陷报告"
- 需要输出 BUG_LIST 反馈给 Fullstack 修复时

## 输入

- **必需**：TEST_REPORT（测试执行报告）、失败的测试用例
- **必需**：PRD（已批准，artifact_ref，用于追溯）
- **可选**：源码、日志、截图/录屏
- **固定约束**：缺陷优先级定义、项目版本

## 输出制品

- **BUG_LIST**：缺陷清单
  - 缺陷详情（ID、标题、描述、步骤、预期/实际结果）
  - PRD 追溯（对应哪个 PRD 功能）
  - 优先级（P0/P1/P2/P3）
  - 状态（OPEN/IN_PROGRESS/RESOLVED/CLOSED）
  - 截图/日志附件
- **TEST_FEEDBACK**：测试反馈文件（供 PM 触发修复循环）
- **BUG_LIST_APPROVAL_RECORD**：缺陷清单批准记录

## 执行步骤

### Step 0: 读取 PRD 追溯上下文

```python
def load_prd_context(prd_ref):
    """加载 PRD 功能清单，用于缺陷追溯"""
    approval_record = read_artifact_approval_record(prd_ref)
    if not approval_record or approval_record["status"] != "APPROVED":
        raise ValueError(f"PRD {prd_ref} 未 APPROVED，无法进行缺陷追溯")

    prd_functions = extract_prd_functions(prd_ref)
    return {
        "prd_version": approval_record["version"],
        "prd_hash": approval_record["hash"],
        "functions": prd_functions
    }
```

---

### Step 1: 缺陷发现与初步分析

发现测试失败后，首先复现并分析：

```python
def analyze_failure(failed_test, prd_functions):
    """缺陷分析 + PRD 追溯"""
    # 1. 确定缺陷类型
    defect_types = {
        "功能缺陷": "功能未按需求实现",
        "界面缺陷": "UI 与设计稿不符",
        "性能缺陷": "响应时间超过阈值",
        "安全缺陷": "存在安全漏洞",
        "兼容性缺陷": "特定环境下行为异常",
    }

    # 2. 确定严重程度
    severity = {
        "P0": "系统崩溃、数据丢失、安全漏洞",
        "P1": "核心功能不可用、重要功能失效",
        "P2": "功能异常、用户体验受影响",
        "P3": "界面细节、体验优化建议",
    }

    # 3. PRD 追溯
    test_case_id = failed_test["test"]
    prd_ref = failed_test.get("prd_ref")  # ← 从测试用例继承 PRD 追溯

    # 验证 PRD 追溯有效
    valid_ids = [f["id"] for f in prd_functions]
    if prd_ref and prd_ref not in valid_ids:
        raise ValueError(f"测试用例 {test_case_id} 的 prd_ref={prd_ref} 不在 PRD 功能清单中")

    prd_name = next((f["name"] for f in prd_functions if f["id"] == prd_ref), "未知")

    return {
        "id": generate_bug_id(),
        "type": defect_types["功能缺陷"],
        "severity": severity["P1"],
        "title": f"缺陷标题（从失败信息提取）",
        "file": failed_test["file"],
        "line": failed_test["line"],
        "prd_ref": prd_ref,  # ← PRD 追溯
        "prd_name": prd_name,
        "test_case_ref": test_case_id,
    }
```

---

### Step 2: 填写缺陷详情（带 PRD 追溯）

#### 缺陷模板

```markdown
## BUG-001：订单创建接口返回数据格式错误

**严重程度**：P1
**缺陷类型**：功能缺陷
**PRD 来源**：PRD-F4.1（下单-创建订单）← PRD 追溯
**对应测试用例**：E2E-002（商品搜索到下单）← 测试用例追溯
**发现时间**：{timestamp}
**发现方式**：E2E 测试
**状态**：OPEN

---

### 缺陷描述

订单创建成功后，接口返回的订单对象中 `totalAmount` 字段格式为字符串，
但前端期望为数字类型，导致页面展示 NaN。

### 复现步骤

1. 打开商品列表页
2. 点击商品进入详情页
3. 点击"立即购买"按钮
4. 在确认订单页点击"提交订单"

### 预期结果

订单提交成功，页面跳转至支付页，订单金额正确显示为 `¥299.00`。

### 实际结果

订单提交成功，但订单金额显示为 `NaN`，控制台报错：
```
TypeError: Cannot read property 'toFixed' of undefined
```

### PRD 功能影响分析

**PRD 追溯**：BUG-001 影响 PRD-F4.1（下单-创建订单）的以下验收标准：
- [ ] 订单创建后返回正确的订单信息
- [ ] 订单金额格式正确，前端可计算

### 相关代码

**后端**（OrderServiceImpl.java:85）：
```java
// 实际代码
orderResponse.setTotalAmount(order.getTotalAmount().toString()); // 错误：转成字符串
```

**前端**（OrderConfirm.vue:23）：
```vue
<!-- 实际代码 -->
<span>¥{{ order.totalAmount.toFixed(2) }}</span>  <!-- 字符串无 toFixed 方法 -->
```

### 环境信息

- 浏览器：Chrome 120
- 操作系统：macOS 14.2
- 后端版本：1.2.3
- 前端版本：1.2.3

### 截图/日志

![订单金额显示 NaN](screenshots/bug-001-screenshot.png)

```
[Backend Log]
2024-01-15 14:30:25 INFO  OrderService - 创建订单: orderId=ord_abc123
2024-01-15 14:30:25 DEBUG OrderService - totalAmount type: class java.math.BigDecimal
```

### 根因分析

后端 OrderServiceImpl 在构造返回对象时，将 BigDecimal 类型的 totalAmount
转换为 String，导致前端 JSON 解析后类型为字符串，前端使用 toFixed() 报错。

**PRD 追溯**：此缺陷违反 PRD-F4.1 的"订单金额必须为数字类型"要求。

### 修复建议

**方案 A（推荐）**：后端保持类型，返回数字
```java
// OrderServiceImpl.java
orderResponse.setTotalAmount(order.getTotalAmount()); // 保持 BigDecimal，前端自动转为数字
```

**方案 B**：前端做类型转换
```typescript
// OrderConfirm.vue
const displayAmount = Number(order.totalAmount).toFixed(2)
```

建议采用方案 A，从源头保持数据类型正确。
```

---

### Step 3: 优先级判定

```markdown
## 缺陷优先级判定矩阵

| 缺陷影响 | 影响范围 | 判定 |
|---------|---------|------|
| 系统崩溃、核心功能不可用、数据丢失 | 全局/多用户 | P0 |
| 核心功能可用但有严重缺陷 | 单用户 | P1 |
| 功能异常但有替代方案 | 单用户 | P2 |
| UI 细节问题、体验优化 | 单用户 | P3 |
```

**P0 缺陷示例**：
- 支付接口返回 500，用户钱扣了但订单没创建
- 用户密码明文存储
- SQL 注入漏洞

**P1 缺陷示例**：
- 订单金额显示 NaN（本次发现的）→ PRD-F4.1
- 搜索结果排序错误 → PRD-F3.1
- 分页数据丢失 → PRD-F3.2

---

### Step 4: 生成 BUG → PRD 追溯表

```markdown
## BUG → PRD 追溯表

| Bug ID | 缺陷标题 | 优先级 | PRD 来源 | PRD 功能名称 | 对应测试用例 | 状态 |
|--------|---------|--------|---------|-------------|-------------|------|
| BUG-001 | 订单金额显示 NaN | P1 | F4.1 | 下单-创建订单 | E2E-002 | OPEN |
| BUG-002 | 商品搜索排序错误 | P1 | F3.1 | 商品搜索-关键词搜索 | IT-010 | OPEN |
| BUG-003 | 头像上传失败 | P2 | F6.1 | 个人信息-头像修改 | E2E-003 | OPEN |
```

---

### Step 5: 写入测试反馈文件

```markdown
# Test Feedback - {task_id}

## 缺陷汇总

| ID | 标题 | 优先级 | PRD 来源 | 状态 |
|----|------|--------|---------|------|
| BUG-001 | 订单金额显示 NaN | P1 | F4.1-下单 | OPEN |
| BUG-002 | 商品搜索排序错误 | P1 | F3.1-商品搜索 | OPEN |
| BUG-003 | 头像上传失败 | P2 | F6.1-个人信息 | OPEN |

---

## P0 / P1 缺陷（必须修复）

### BUG-001：订单金额显示 NaN

- **优先级**：P1
- **PRD 来源**：PRD-F4.1（下单-创建订单）
- **对应测试用例**：E2E-002
- **发现方式**：E2E 测试
- **复现路径**：E2E-002 商品搜索到下单流程
- **根因**：OrderServiceImpl.java:85 将 BigDecimal 转为 String
- **修复建议**：移除 .toString()，保持 BigDecimal 类型

### BUG-002：商品搜索排序错误

- **优先级**：P1
- **PRD 来源**：PRD-F3.1（商品搜索-关键词搜索）
- **对应测试用例**：IT-010
- **发现方式**：集成测试
- **复现路径**：GET /api/v1/products?sort=price_asc
- **根因**：排序字段映射错误，price_asc 实际按 id 排序
- **修复建议**：检查 ProductRepository 中的排序逻辑

---

## P2 / P3 缺陷（可选修复）

### BUG-003：用户头像上传失败

- **优先级**：P2
- **PRD 来源**：PRD-F6.1（个人信息-头像修改）
- **对应测试用例**：E2E-003
- **发现方式**：E2E 测试
- **复现路径**：E2E-003 个人信息修改
- **根因**：文件大小限制为 1MB，但头像压缩后仍 1.2MB
- **修复建议**：将限制调整为 2MB 或增加压缩率
```

---

### Step 6: 缺陷状态追踪（带 PRD 追溯）

```markdown
## 缺陷状态追踪

| ID | 标题 | P | PRD | 发现 | 开发确认 | 修复中 | 待验证 | 关闭 |
|----|------|---|-----|------|---------|--------|--------|------|
| BUG-001 | 订单格式错误 | P1 | F4.1 | ✅ | ✅ | 🔄 | ⏳ | ⏳ |
| BUG-002 | 排序错误 | P1 | F3.1 | ✅ | ❌ | ⏳ | ⏳ | ⏳ |
| BUG-003 | 头像上传 | P2 | F6.1 | ✅ | ⏳ | ⏳ | ⏳ | ⏳ |

### 状态变更记录

**BUG-001 状态变更**（PRD-F4.1 相关）：
- 2024-01-15 14:30 → OPEN（QA 发现）
- 2024-01-15 14:45 → IN_PROGRESS（Fullstack 确认）
- 2024-01-15 15:30 → RESOLVED（Fullstack 修复完成）
- 2024-01-15 16:00 → 待验证（QA 重新测试 F4.1 相关用例）
- 2024-01-15 16:30 → CLOSED（QA 验证通过 F4.1）

### PRD 残留缺陷追踪

| PRD 功能 | 残留 P0 | 残留 P1 | 状态 |
|---------|--------|---------|------|
| F1.1 用户注册 | 0 | 0 | ✅ 无缺陷 |
| F3.1 商品搜索 | 0 | 1 | ⚠️ BUG-002 |
| F4.1 下单 | 0 | 1 | ⚠️ BUG-001 |
| F6.1 个人信息 | 0 | 0 | ✅ 无缺陷 |

→ 每个 PRD 功能必须清零 P0/P1 缺陷才能关闭
```

---

### Step 7: 生成 BUG_LIST_APPROVAL_RECORD

```json
{
  "artifact": "BUG_LIST",
  "name": "{项目名称} 缺陷清单",
  "version": "v{version}",
  "hash": "{内容哈希}",
  "status": "APPROVED",
  "workflow_mode": "L3",
  "approvals": [
    {
      "role": "qa",
      "result": "APPROVED",
      "timestamp": "{timestamp}"
    }
  ],
  "defect_summary": {
    "total": 3,
    "p0": 0,
    "p1": 2,
    "p2": 1,
    "p3": 0
  },
  "prd_coverage": {
    "F4.1": {"p1_count": 1, "bugs": ["BUG-001"]},
    "F3.1": {"p1_count": 1, "bugs": ["BUG-002"]},
    "F6.1": {"p2_count": 1, "bugs": ["BUG-003"]}
  },
  "blocking_p0": false,
  "blocking_p1": true,
  "deliverable_allowed": false,
  "prd_ref": "PRD@{prd_hash}",
  "test_report_ref": "TEST_REPORT@{test_report_hash}",
  "prev_version": null,
  "next_version": null,
  "timestamp": "{ISO8601}"
}
```

---

### Step 8: 缺陷统计报告（带 PRD 分布）

```markdown
## 缺陷统计报告

**统计周期**：{start_date} ~ {end_date}
**测试版本**：v1.2.3

### 缺陷分布（按 PRD 功能）

| PRD 功能 | P0 | P1 | P2 | P3 | 状态 |
|---------|----|----|----|----|------|
| F1.1 用户注册-邮箱注册 | 0 | 0 | 0 | 0 | ✅ 无缺陷 |
| F2.1 用户登录-密码登录 | 0 | 0 | 0 | 0 | ✅ 无缺陷 |
| F3.1 商品搜索-关键词搜索 | 0 | 1 | 0 | 0 | ⚠️ BUG-002 |
| F4.1 下单-创建订单 | 0 | 1 | 0 | 0 | ⚠️ BUG-001 |
| F5.1 支付-在线支付 | 0 | 0 | 0 | 0 | ✅ 无缺陷 |
| F6.1 个人信息-头像修改 | 0 | 0 | 1 | 0 | ⚠️ BUG-003 |

### 缺陷类型分布

| 类型 | 数量 | 占比 |
|------|------|------|
| 功能缺陷 | 2 | 67% |
| 接口缺陷 | 1 | 33% |

### PRD 缺陷密度

| PRD 模块 | 缺陷数 | 功能数 | 密度 |
|---------|--------|--------|------|
| user-service | 0 | 2 | 0% |
| product-service | 1 | 2 | 50% |
| order-service | 1 | 1 | 100% |
| payment-service | 0 | 1 | 0% |

→ order-service 缺陷密度最高（100%），需重点关注

### 结论

- P0 缺陷：0 个 ✅
- P1 缺陷：2 个（影响 F3.1、F4.1）
- **阻断交付**：是（P1 缺陷存在）
- PRD 功能清零：5/6 个功能无 P0/P1 缺陷
```

---

## PM 回复模板

### 发现 P0 缺陷

```
🚨 P0 缺陷发现：{task_id}

缺陷：{title}
影响：{impact}
PRD 来源：PRD-{id}（{prd_name}）
状态：代码冻结，等待 Fullstack 修复

已通知：
- PM（代码冻结）
- Fullstack（立即修复）

测试反馈：docs/04测试阶段/04-03测试报告/{task_id}_feedback.md
```

### 测试反馈已生成

```
✅ 测试反馈已生成：{task_id}

缺陷清单：
  P0: 0 个 ✅
  P1: 2 个（需修复，影响 F3.1、F4.1）
  P2: 1 个（建议修复，影响 F6.1）
  P3: 0 个

**deliverable_allowed: false** ⛔
原因：存在 2 个 P1 缺陷

PRD 影响分析：
  - F3.1 商品搜索：1 个 P1 缺陷（BUG-002）
  - F4.1 下单：1 个 P1 缺陷（BUG-001）

反馈文件：docs/04测试阶段/04-03测试报告/{task_id}_feedback.md
Fullstack 请根据反馈修复后通知 QA 重新测试。
```
```

---

## 验证步骤

1. [ ] 每个缺陷有唯一的 ID
2. [ ] 每个缺陷有 PRD 追溯字段（prd_ref）
3. [ ] 缺陷描述清晰，可复现
4. [ ] 有预期结果和实际结果对比
5. [ ] 有截图/日志等证据
6. [ ] 优先级判定合理
7. [ ] BUG → PRD 追溯表已生成
8. [ ] 缺陷反馈文件格式正确
9. [ ] BUG_LIST_APPROVAL_RECORD 已生成
10. [ ] P0 缺陷立即上报 PM

## 常见陷阱

1. **缺陷无 PRD 追溯**：缺陷描述了但不知道对应哪个 PRD 功能
2. **缺陷描述模糊**："功能不正常"不是有效的缺陷描述
3. **优先级过高/过低**：所有 bug 都标 P0 或都标 P3
4. **根因不分析**：只记录现象不分析原因
5. **缺陷重复提交**：同一个问题提了 3 个 bug
6. **关闭不验证**：开发说修复了就直接关，没重新测试
7. **PRD 追溯字段缺失**：缺陷没有 prd_ref 字段，无法追踪到 PRD