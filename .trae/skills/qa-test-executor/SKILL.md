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

```python
test_results = {
    "unit_tests": {
        "frontend": {
            "total": 156,
            "passed": 155,
            "failed": 1,
            "skipped": 0,
            "duration": "45s",
            "failures": [
                {
                    "test": "tests/unit/orderApi.test.ts::createOrder",
                    "error": "Error: expect(received).toBe(expected)",
                    "severity": "P1",
                    "prd_ref": "F4-下单"
                }
            ]
        },
        "backend": {
            "total": 156,
            "passed": 156,
            "failed": 0,
            "skipped": 0,
            "duration": "60s"
        }
    },
    "integration_tests": {
        "total": 24,
        "passed": 24,
        "failed": 0,
        "duration": "30s"
    },
    "e2e_tests": {
        "total": 3,
        "passed": 2,
        "failed": 1,
        "duration": "120s",
        "failures": [
            {
                "test": "E2E-002: 商品搜索到下单流程",
                "error": "库存不足",
                "severity": "P1",
                "prd_ref": "F4-下单",
                "screenshot": "e2e-results/e2e-002-failed.png"
            }
        ]
    }
}
```

---

### Step 6: 质量门禁判定

```python
def evaluate_quality_gates(test_results, workflow_mode):
    """
    质量门禁判定：每个门禁有明确的 PASS/FAIL 状态
    """
    L2 = workflow_mode == "L2"
    threshold_pass_rate = 0.95 if L2 else 0.99
    threshold_coverage = 0.60 if L2 else 0.75

    total_passed = sum_layer_passed(test_results)
    total_failed = sum_layer_failed(test_results)
    total = total_passed + total_failed
    pass_rate = total_passed / total if total > 0 else 0

    p0_failures = count_p0_failures(test_results)
    p1_failures = count_p1_failures(test_results)
    coverage = test_results.get("coverage", {})

    # PASS_GATE
    if pass_rate >= threshold_pass_rate:
        pass_gate = "PASS"
    else:
        pass_gate = "FAIL"

    # P0_GATE
    p0_gate = "PASS" if p0_failures == 0 else "FAIL"

    # COVERAGE_GATE
    frontend_coverage = coverage.get("frontend_lines", 0)
    backend_coverage = coverage.get("backend_lines", 0)
    if frontend_coverage >= threshold_coverage and backend_coverage >= threshold_coverage:
        coverage_gate = "PASS"
    else:
        coverage_gate = "FAIL"

    # E2E_GATE（仅 L3）
    e2e_gate = None
    if not L2:
        e2e_failed = test_results["e2e_tests"]["failed"]
        e2e_gate = "PASS" if e2e_failed == 0 else "FAIL"

    # FINAL 判定
    all_critical_pass = (pass_gate == "PASS" and p0_gate == "PASS" and coverage_gate == "PASS")
    if e2e_gate:
        all_critical_pass = all_critical_pass and e2e_gate == "PASS"

    if p0_failures > 0:
        final_status = "FAIL"
        deliverable_allowed = False
    elif not all_critical_pass:
        final_status = "FAIL"
        deliverable_allowed = False
    elif p1_failures > 2:
        final_status = "CONDITIONAL_PASS"
        deliverable_allowed = True  # P1 缺陷 ≤ 2，暂缓交付
    else:
        final_status = "PASS"
        deliverable_allowed = True

    return {
        "gates": {
            "PASS_GATE": {"status": pass_gate, "actual": f"{pass_rate*100:.1f}%", "threshold": f"{threshold_pass_rate*100:.0f}%"},
            "P0_GATE": {"status": p0_gate, "p0_failures": p0_failures},
            "COVERAGE_GATE": {"status": coverage_gate, "frontend": f"{frontend_coverage*100:.1f}%", "backend": f"{backend_coverage*100:.1f}%", "threshold": f"{threshold_coverage*100:.0f}%"},
            "E2E_GATE": {"status": e2e_gate} if e2e_gate else None
        },
        "final_status": final_status,
        "deliverable_allowed": deliverable_allowed,
        "p0_failures": p0_failures,
        "p1_failures": p1_failures,
        "pass_rate": pass_rate
    }
```

---

### Step 7: 生成 TEST_REPORT（带明确门禁判定）

```markdown
# 测试执行报告

**任务 ID**: {task_id}
**执行时间**: {timestamp}
**工作流模式**: L3
**deliverable_allowed**: **{true/false}**

---

## 质量门禁结果

| 门禁 | 状态 | 实际值 | 阈值 | 说明 |
|------|------|--------|------|------|
| PASS_GATE | **PASS / FAIL** | 99.4% | ≥ 99% (L3) | 测试通过率 |
| P0_GATE | **PASS / FAIL** | 0 个失败 | 必须 0 | P0 缺陷数 |
| COVERAGE_GATE | **PASS / FAIL** | 前端 78.5% / 后端 81.2% | ≥ 75% (L3) | 覆盖率 |
| E2E_GATE | **PASS / FAIL** | 2/3 通过 | 必须通过 (L3) | E2E 测试 |

**最终判定**：**{PASS / FAIL / CONDITIONAL_PASS}**
**deliverable_allowed**: **{true / false}**

---

## 执行汇总

| 类型 | 总数 | 通过 | 失败 | 通过率 |
|------|------|------|------|--------|
| 单元测试 | 312 | 311 | 1 | 99.7% |
| 集成测试 | 24 | 24 | 0 | 100% |
| E2E 测试 | 3 | 2 | 1 | 66.7% |
| **合计** | **339** | **337** | **2** | **99.4%** |

---

## 覆盖率报告

### 前端覆盖率

| 指标 | 覆盖率 | 阈值（L3） | 状态 |
|------|--------|-----------|------|
| Statements | 78.5% | ≥ 75% | ✅ |
| Branches | 72.3% | ≥ 70% | ✅ |
| Functions | 85.0% | ≥ 75% | ✅ |
| Lines | 78.5% | ≥ 75% | ✅ |

### 后端覆盖率

| 指标 | 覆盖率 | 阈值（L3） | 状态 |
|------|--------|-----------|------|
| Instructions | 82.3% | ≥ 75% | ✅ |
| Branches | 76.5% | ≥ 70% | ✅ |
| Lines | 81.2% | ≥ 75% | ✅ |
| Methods | 90.1% | ≥ 80% | ✅ |

---

## 失败详情

### 单元测试失败

| 文件 | 用例 | 优先级 | PRD 来源 | 错误 |
|------|------|--------|---------|------|
| orderApi.test.ts | createOrder | **P1** | F4-下单 | 库存字段类型不匹配 |

### E2E 测试失败

| 用例 | 优先级 | PRD 来源 | 错误 | 截图 |
|------|--------|---------|------|------|
| E2E-002 | **P1** | F4-下单 | 库存不足 | e2e-002-failed.png |

---

## 结论

**质量状态**：**{PASS / FAIL / CONDITIONAL_PASS}**

| 场景 | 判定结果 | 说明 |
|------|---------|------|
| deliverable_allowed = true | ✅ 可交付 | 所有质量门禁通过，无 P0 缺陷 |
| deliverable_allowed = false | ⛔ 阻断交付 | 存在 P0/P1 缺陷超标，需要修复 |
| CONDITIONAL_PASS | ⚠️ 暂缓交付 | P1 缺陷 ≤ 2 个，PM 可决定是否接受 |

**缺陷清单**：docs/04测试阶段/04-03测试报告/{task_id}_bug_list.md
**测试反馈**：docs/04测试阶段/04-03测试报告/{task_id}_feedback.md（供 Fullstack 修复）
```

---

### Step 8: 生成 TEST_REPORT_APPROVAL_RECORD

```json
{
  "artifact": "TEST_REPORT",
  "name": "{项目名称} 测试报告",
  "version": "v{version}",
  "hash": "{内容哈希}",
  "status": "{PASS / CONDITIONAL_PASS / FAIL}",
  "workflow_mode": "L3",
  "approvals": [
    {
      "role": "qa-test-executor",
      "result": "{APPROVED / CONDITIONAL_APPROVED / REJECTED}",
      "timestamp": "{timestamp}",
      "conditions": []
    }
  ],
  "gates": {
    "PASS_GATE": {"status": "PASS", "actual": "99.4%", "threshold": "99%"},
    "P0_GATE": {"status": "PASS", "p0_failures": 0},
    "COVERAGE_GATE": {"status": "PASS", "frontend": "78.5%", "backend": "81.2%", "threshold": "75%"},
    "E2E_GATE": {"status": "FAIL", "failed": 1, "total": 3}
  },
  "test_summary": {
    "total": 339,
    "passed": 337,
    "failed": 2,
    "pass_rate": 0.994
  },
  "coverage": {
    "frontend_lines": 0.785,
    "backend_lines": 0.812
  },
  "defects": {
    "p0": 0,
    "p1": 2,
    "p2": 0,
    "p3": 0
  },
  "deliverable_allowed": false,
  "reason": "E2E_GATE 失败，存在 1 个 E2E 测试失败（P1 缺陷）",
  "build_ref": "BUILD_ARTIFACT@{build_hash}",
  "prd_ref": "PRD@{prd_hash}",
  "prev_version": null,
  "next_version": null
}
```

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