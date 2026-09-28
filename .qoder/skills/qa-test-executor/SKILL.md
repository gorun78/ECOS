---
name: qa-test-executor
description: "QA 测试执行 Skill：执行单元测试/集成测试/E2E 测试，输出带门禁判定的 TEST_REPORT 和 TEST_REPORT_APPROVAL_RECORD（含 deliverable_allowed）。当 QA 收到 PM 的执行指令时触发，或 fullstack 完成构建后自动触发。"
version: 2.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [qa, test-execution, unit-test, integration-test, e2e, playwright, approval-record, quality-gate]
    related_skills: [qa-test-planner, qa-bug-tracker, dogfood]
    artifact_type: TEST_REPORT
    workflow_modes: [L2, L3]
---

# QA Test Executor Skill (v2 — 交付门禁版)

## 核心原则

TEST_REPORT 产出后必须生成 `TEST_REPORT_APPROVAL_RECORD`，含明确的 `deliverable_allowed` 判定。`deliverable_allowed=false` 时阻断交付，PM 必须等待修复后才能做质量裁定。测试质量门禁有明确的 PASS/FAIL 状态，不是描述性的"基本通过"。

## 关键机制

### 质量门禁判定（明确 PASS/FAIL）

| 门禁 | L2 判定条件 | L3 判定条件 | 阻断条件 |
|------|-----------|-----------|---------|
| **PASS_GATE** | 通过率 ≥ 95% | 通过率 ≥ 99% | 失败数 > 阈值 |
| **P0_GATE** | P0 通过率 100% | P0 通过率 100% | 任何 P0 失败 |
| **COVERAGE_GATE** | 覆盖率 ≥ 60% | 覆盖率 ≥ 75% | 覆盖率未达标 |
| **E2E_GATE** | 跳过 | 核心 E2E 必须通过 | E2E 失败 |

### deliverable_allowed 判定

```
FINAL_GATE 判定：

deliverable_allowed = true 条件：
  PASS_GATE = PASS
  P0_GATE = PASS
  COVERAGE_GATE = PASS
  (L3: E2E_GATE = PASS)

deliverable_allowed = false 条件：
  任一 P0 缺陷 → FAIL，阻断交付
  PASS_GATE 或 COVERAGE_GATE 未通过 → FAIL，阻断交付
  P1 缺陷 > 2 个 → CONDITIONAL_PASS，暂缓交付

CONDITIONAL_PASS（P1 缺陷 ≤ 2）：
  - deliverable_allowed = true
  - 条件：P1 缺陷必须在下个版本修复
  - PM 可决定是否接受暂缓交付
```

## 触发条件

- PM 向 QA 分发执行任务（`role: qa`，`phase: test-execution`）
- Fullstack 完成构建后自动触发（`delivery: qa`）
- 用户说"执行测试"、"跑测试"、"测试报告"

## 输入

- **必需**：TEST_CASES（测试用例集，artifact_ref）、BUILD_ARTIFACT（构建产物，artifact_ref）
- **必需**：PRD（已批准，artifact_ref，用于追溯）
- **可选**：OpenAPI 规范、源代码
- **固定约束**：测试环境、覆盖率阈值（L2 ≥ 60%，L3 ≥ 75%）

## 输出制品

- **TEST_REPORT**：测试执行报告（含明确门禁判定）
- **TEST_REPORT_APPROVAL_RECORD**：测试批准记录（artifact_type: APPROVAL_RECORD）
- **BUG_LIST**：缺陷清单（发现 bug 时输出）

## 执行步骤

### Step 0: 前置校验 — BUILD_ARTIFACT 批准记录检查

```python
def validate_build_artifact(build_ref):
    """QA 执行前，必须校验 BUILD_ARTIFACT 已 APPROVED"""
    approval_record = read_artifact_approval_record(build_ref)
    if not approval_record or approval_record["status"] != "APPROVED":
        raise ValueError(f"BUILD_ARTIFACT {build_ref} 未 APPROVED，QA 测试禁止开始")
    if not approval_record.get("deliverable_allowed"):
        raise ValueError("BUILD_ARTIFACT deliverable_allowed=false，禁止开始测试")
    return {
        "build_version": approval_record["version"],
        "build_hash": approval_record["hash"],
        "artifacts": approval_record["artifacts"]
    }
```

```markdown
## BUILD_ARTIFACT 校验

收到 QA 测试请求，校验以下前提条件：

1. [ ] BUILD_ARTIFACT 状态为 APPROVED ✅
2. [ ] BUILD_ARTIFACT 有批准记录 ✅
3. [ ] BUILD_ARTIFACT 的 deliverable_allowed = true ✅

当前 BUILD_ARTIFACT：
- 版本：{version}
- Hash：{hash}
- 状态：APPROVED

→ BUILD_ARTIFACT 校验通过，可开始 QA 测试
```

---

### Step 1: 环境准备

```bash
# 检查测试环境
node --version  # >= 18
java --version  # >= 17
npm --version

# 安装依赖
cd tests
npm install

# 验证 Playwright 可用
npx playwright --version

# 启动被测服务（集成测试/E2E）
cd backend
java -jar build/libs/app.jar &
BACKEND_PID=$!

# 等待服务就绪
until curl -s http://localhost:8080/actuator/health; do
  sleep 2
done

# 启动前端 dev server
cd frontend
npm run dev &
FRONTEND_PID=$!

sleep 5
```

---

### Step 2: 执行单元测试

#### 前端单元测试（Vitest）

```bash
cd frontend

# 运行单元测试
npm run test:unit

# 覆盖率报告
npm run test:coverage
# 输出：coverage/lcov-report/index.html
```

#### 后端单元测试（JUnit 5 + JaCoCo）

```bash
cd backend

# 运行单元测试
./gradlew test

# 覆盖率报告
./gradlew jacocoTestReport
```

---

### Step 3: 执行集成测试

```bash
# API 集成测试

# 测试用户注册接口
curl -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","name":"Test","password":"password123"}' \
  -w "\nHTTP Status: %{http_code}\n"
```

---

### Step 4: 执行 E2E 测试（L3 必须）

```bash
cd tests

# 启动 Playwright
npx playwright test

# 输出示例：
#  E2E-001: 用户注册登录登出流程
#    ✓ 注册成功
#    ✓ 登录跳转 dashboard
#    ✓ 登出跳转 login
#
#  E2E-002: 商品搜索到下单流程
#    ✗ 下单失败 - 库存不足
#
#  2 passed, 1 failed
```

---

### Step 5: 解析测试结果

> 区块模板/示例见 references（原 SKILL.md L214-262），生成对应制品前必须读取。

---

### Step 6: 质量门禁判定

> 区块模板/示例见 references（原 SKILL.md L268-340），生成对应制品前必须读取。

---

### Step 7: 生成 TEST_REPORT（带明确门禁判定）

> 区块模板/示例见 references（原 SKILL.md L346-431），生成对应制品前必须读取。

---

### Step 8: 生成 TEST_REPORT_APPROVAL_RECORD

> 区块模板/示例见 references（原 SKILL.md L437-482），生成对应制品前必须读取。

---

## PM 回复模板

### 测试完成 + deliverable_allowed = true

```
✅ 测试执行完成：{task_id}

质量门禁结果：
  PASS_GATE：✅ PASS（99.4% ≥ 99%）
  P0_GATE：✅ PASS（0 个 P0 失败）
  COVERAGE_GATE：✅ PASS（前端 78.5% / 后端 81.2%）
  E2E_GATE：✅ PASS（3/3 通过）

**deliverable_allowed: true** ✅

执行结果：
  单元测试：311/312 通过（99.7%）
  集成测试：24/24 通过（100%）
  E2E 测试：3/3 通过（100%）

覆盖率：前端 78.5% / 后端 81.2% ✅

**质量裁定：通过** — 可进入交付环节
```

### 测试完成 + deliverable_allowed = false

```
⚠️ 测试执行完成：{task_id}

质量门禁结果：
  PASS_GATE：⚠️ PASS（99.4% ≥ 99%）
  P0_GATE：✅ PASS（0 个 P0 失败）
  COVERAGE_GATE：✅ PASS（前端 78.5% / 后端 81.2%）
  E2E_GATE：❌ FAIL（1 个 E2E 失败）

**deliverable_allowed: false** ⛔

失败详情：
  - E2E-002：库存不足（P1）→ F4-下单

**质量裁定：阻断** — E2E 测试失败，暂缓交付

已通知：
  - PM（质量裁定阻断）
  - Fullstack（立即修复 E2E-002）

测试反馈：docs/04测试阶段/04-03测试报告/{task_id}_feedback.md
```

---

## 验证步骤

1. [ ] BUILD_ARTIFACT 校验通过（APPROVED 状态）
2. [ ] 所有测试用例执行完成
3. [ ] 每个质量门禁有明确的 PASS/FAIL 状态（非描述性）
4. [ ] TEST_REPORT 含 `deliverable_allowed` 字段
5. [ ] TEST_REPORT_APPROVAL_RECORD 已生成
6. [ ] 失败用例有 PRD 来源追溯（对应哪个 PRD 功能）
7. [ ] P0 缺陷 0 个（P0_GATE 必须 PASS）
8. [ ] 覆盖率数据准确（前端 + 后端）

## 常见陷阱

1. **质量门禁描述模糊**：用"基本通过"而非明确的 PASS/FAIL
2. **deliverable_allowed 判定错误**：任何 P0 缺陷都应阻断，不是"基本通过"就行
3. **失败用例无 PRD 追溯**：无法知道失败的功能对应哪个 PRD
4. **E2E 失败但判定通过**：L3 模式下 E2E 失败必须 FAIL
5. **覆盖率数据不准确**：实际没达标但报告写达标