# qa-test-executor — 拆分出的模板/示例

> 由 SKILL.md 拆出，SKILL.md 对应位置留有指针。生成相关制品前先读本文件。

## 块 1（原 SKILL.md L214-262）

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

## 块 2（原 SKILL.md L268-340）

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

## 块 3（原 SKILL.md L346-431）

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

## 块 4（原 SKILL.md L437-482）

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
