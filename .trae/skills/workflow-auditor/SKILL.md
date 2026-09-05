---
name: workflow-auditor
description: "Commander 工作流审计 Skill：执行质量门禁检查、生成合规审计报告、验证修正效果。当任务完成时触发全面审计。"
version: 1.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [commander, workflow, audit, quality-gate, compliance-report]
    related_skills: [compliance-monitor, artifact-verifier]
    workflow_modes: [L1, L2, L3]
---

# Workflow Auditor Skill

## 核心原则

在任务完成阶段执行全面审计，验证所有质量门禁是否满足，生成合规审计报告。审计结果作为交付决策的依据。

## 审计规则

### 质量门禁规则

| 门禁项 | 阈值 | 实际值来源 | 违规判定 |
|--------|------|---------|---------|
| Reviewer deliverable_allowed | true（无 P0，P1 ≤ 2 为 CONDITIONAL_PASS 暂缓） | REVIEW_REPORT | deliverable_allowed=false → quality_gate_failure |
| QA 通过率 | L2 ≥ 95% / L3 ≥ 99% | TEST_REPORT | 低于对应阈值 → quality_gate_failure |
| QA 覆盖率 | L2 ≥ 60% / L3 ≥ 75% | TEST_REPORT | 低于对应阈值 → quality_gate_failure |
| 制品 Schema 校验 | 通过 | 制品内容 | 校验失败 → schema_validation_failure |
| artifact_ref 版本一致性 | 一致 | 文件系统 | 不一致 → version_conflict |

### 汇合门禁规则

| 条件 | 结果 |
|------|------|
| Reviewer deliverable_allowed=true 且 QA deliverable_allowed=true | 通过 |
| Reviewer 有 P0 缺陷（deliverable_allowed=false） | 不通过，代码冻结 |
| QA 通过率/覆盖率未达 L2/L3 阈值 | 不通过，打回修复 |
| L3 未同时收到 REVIEW_REPORT 与 TEST_REPORT | 不通过，暂缓交付 |
| 任一方 FAILED 2 次 | 升级人工介入 |

### 工作流阶段完整性

| 工作流 | 必需阶段 |
|--------|---------|
| L1 | PM → Fullstack → Reviewer |
| L2 | PM → Arch → Fullstack → Reviewer |
| L3 | PM → Arch → Fullstack → Reviewer + QA → PM 汇合 |

## 触发条件

- 任务完成时（TASK_DONE 事件）
- 用户说"执行审计"、"生成审计报告"、"质量评估"

## 输入

- **必需**：任务状态数据（通过 Kanban MCP 获取）
- **必需**：制品元数据（通过文件系统 MCP 获取）
- **必需**：工作流模式配置（L1/L2/L3）
- **可选**：审计历史记录、问题报告、修复反馈

## 输出制品

- **COMPLIANCE_REPORT**：合规审计报告（artifact_type: COMPLIANCE_REPORT）
- **AUDIT_LOG**：审计日志记录
- **VERIFICATION_RESULT**：修正验证结果

## 执行步骤

### Step 1: 获取任务信息

```python
def get_task_info(task_id):
    """
    获取任务的完整信息
    """
    return kanban.get_task(task_id)
```

### Step 2: 检查工作流阶段完整性

```python
def check_workflow_completeness(task_id, workflow_mode):
    """
    检查工作流阶段是否完整执行
    """
    issues = []
    task_chain = kanban.list_tasks(filter={"parent_id": task_id})
    
    if workflow_mode == "L1":
        stages = ["pm", "fullstack", "reviewer"]
    elif workflow_mode == "L2":
        stages = ["pm", "arch", "fullstack", "reviewer"]
    elif workflow_mode == "L3":
        stages = ["pm", "arch", "fullstack", "reviewer", "qa"]
    else:
        return [{"type": "invalid_workflow_mode", "severity": "P0", "message": f"未知工作流模式：{workflow_mode}"}]
    
    for stage in stages:
        stage_found = any(t["role"] == stage and t["status"] == "DONE" for t in task_chain)
        if not stage_found:
            issues.append({
                "type": "stage_missing",
                "severity": "P1",
                "message": f"工作流阶段 {stage} 未完成",
                "stage": stage
            })
    
    return issues
```

### Step 3: 检查质量门禁

```python
def check_quality_gates(task_id):
    """
    检查质量门禁是否满足
    """
    issues = []
    task = kanban.get_task(task_id)
    
    # 获取 REVIEW_REPORT
    review_report = get_artifact("REVIEW_REPORT", task["reviewer_task_id"])
    
    if review_report:
        deliverable_allowed = review_report.get("deliverable_allowed", False)
        if not deliverable_allowed:
            issues.append({
                "type": "quality_gate_failure",
                "severity": "P0",
                "message": "Reviewer deliverable_allowed=false，代码冻结",
                "gate": "REVIEWER_GATE",
                "actual": deliverable_allowed,
                "threshold": True
            })
    else:
        issues.append({
            "type": "missing_review_report",
            "severity": "P1",
            "message": "REVIEW_REPORT 缺失"
        })
    
    # 获取 TEST_REPORT（L2/L3）
    workflow_mode = task.get("workflow_mode", "L1")
    if workflow_mode in ["L2", "L3"]:
        test_report = get_artifact("TEST_REPORT", task["qa_task_id"])
        
        if test_report:
            pass_rate = test_report.get("pass_rate", 0)
            coverage = test_report.get("coverage", {})
            # L2 通过率 ≥ 95% / L3 ≥ 99%
            pass_threshold = 0.99 if workflow_mode == "L3" else 0.95
            # L2 覆盖率 ≥ 60% / L3 ≥ 75%
            cov_threshold = 0.75 if workflow_mode == "L3" else 0.60
            
            if pass_rate < pass_threshold:
                issues.append({
                    "type": "quality_gate_failure",
                    "severity": "P1",
                    "message": f"QA 通过率 {pass_rate*100:.1f}% < {pass_threshold*100:.0f}%（{workflow_mode}）",
                    "gate": "QA_PASS_RATE",
                    "actual": pass_rate,
                    "threshold": pass_threshold
                })
            
            frontend_cov = coverage.get("frontend_lines", 0)
            backend_cov = coverage.get("backend_lines", 0)
            if frontend_cov < cov_threshold or backend_cov < cov_threshold:
                issues.append({
                    "type": "quality_gate_failure",
                    "severity": "P1",
                    "message": f"QA 覆盖率 前端 {frontend_cov*100:.1f}% / 后端 {backend_cov*100:.1f}% < {cov_threshold*100:.0f}%（{workflow_mode}）",
                    "gate": "COVERAGE_GATE",
                    "actual": {"frontend": frontend_cov, "backend": backend_cov},
                    "threshold": cov_threshold
                })
        else:
            issues.append({
                "type": "missing_test_report",
                "severity": "P1",
                "message": "TEST_REPORT 缺失"
            })
    
    return issues
```

### Step 4: 验证制品完整性

```python
def verify_artifact_completeness(task_id):
    """
    验证制品是否完整
    """
    issues = []
    task = kanban.get_task(task_id)
    workflow_mode = task.get("workflow_mode", "L1")
    
    required_artifacts = {
        "L1": ["PRD", "SOURCE_PATCH", "REVIEW_REPORT"],
        "L2": ["PRD", "ARCH_SPEC", "OPENAPI", "DDL", "SOURCE_PATCH", "REVIEW_REPORT"],
        "L3": ["PRD", "ARCH_SPEC", "OPENAPI", "DDL", "SOURCE_PATCH", "REVIEW_REPORT", "TEST_REPORT"]
    }
    
    for artifact_type in required_artifacts.get(workflow_mode, []):
        artifact = get_artifact(artifact_type, task_id)
        if not artifact:
            issues.append({
                "type": "missing_artifact",
                "severity": "P1",
                "message": f"必需制品 {artifact_type} 缺失",
                "artifact_type": artifact_type
            })
        else:
            # 验证 artifact_ref 一致性
            verification = validate_hash_consistency(artifact["artifact_ref"], artifact["file_path"])
            if not verification["valid"]:
                issues.append({
                    "type": "hash_mismatch",
                    "severity": "P2",
                    "message": verification["error"],
                    "artifact_type": artifact_type
                })
    
    return issues
```

### Step 5: 生成审计报告

```python
def generate_compliance_report(task_id, workflow_completeness_issues, quality_gate_issues, artifact_issues):
    """
    生成合规审计报告
    """
    all_issues = workflow_completeness_issues + quality_gate_issues + artifact_issues
    
    p0_count = len([i for i in all_issues if i["severity"] == "P0"])
    p1_count = len([i for i in all_issues if i["severity"] == "P1"])
    
    # 判定合规状态
    if p0_count > 0:
        compliance_status = "FAIL"
    elif p1_count > 0:
        compliance_status = "PARTIAL"
    else:
        compliance_status = "PASS"
    
    report = {
        "task_id": task_id,
        "audit_time": get_current_time(),
        "workflow_mode": kanban.get_task(task_id).get("workflow_mode", "L1"),
        "compliance_status": compliance_status,
        "issues": all_issues,
        "issue_summary": {
            "P0": p0_count,
            "P1": p1_count,
            "P2": len([i for i in all_issues if i["severity"] == "P2"]),
            "P3": len([i for i in all_issues if i["severity"] == "P3"])
        },
        "workflow_completeness": {
            "status": "PASS" if len(workflow_completeness_issues) == 0 else "FAIL",
            "issues": workflow_completeness_issues
        },
        "quality_gates": {
            "status": "PASS" if len(quality_gate_issues) == 0 else "FAIL",
            "issues": quality_gate_issues
        },
        "artifact_completeness": {
            "status": "PASS" if len(artifact_issues) == 0 else "FAIL",
            "issues": artifact_issues
        },
        "recommendation": get_recommendation(compliance_status, all_issues)
    }
    
    # 保存审计报告
    save_report(report)
    
    # 通知 PM
    notify_pm(report)
    
    return report
```

### Step 6: 验证修正效果

```python
def verify_fix_effect(task_id, fix_info):
    """
    验证 PM 的修复效果
    """
    # 重新执行相关检查项
    issues = []
    
    # 根据修复信息确定需要重新检查的项
    for fix_item in fix_info.get("fixed_items", []):
        if fix_item["type"] == "reviewer_defect":
            # 重新检查 REVIEW_REPORT
            review_report = get_artifact("REVIEW_REPORT", task_id)
            p0_count = review_report.get("defect_summary", {}).get("P0", 0)
            if p0_count > 0:
                issues.append({
                    "type": "fix_not_verified",
                    "severity": "P0",
                    "message": f"修复后仍有 {p0_count} 个 P0 缺陷"
                })
        
        elif fix_item["type"] == "qa_pass_rate":
            # 重新检查 TEST_REPORT
            test_report = get_artifact("TEST_REPORT", task_id)
            pass_rate = test_report.get("pass_rate", 0)
            workflow_mode = task.get("workflow_mode", "L2")
            pass_threshold = 0.99 if workflow_mode == "L3" else 0.95
            if pass_rate < pass_threshold:
                issues.append({
                    "type": "fix_not_verified",
                    "severity": "P1",
                    "message": f"修复后 QA 通过率 {pass_rate*100:.1f}% < {pass_threshold*100:.0f}%"
                })
    
    # 更新审计报告状态
    if len(issues) == 0:
        update_report_status(task_id, "VERIFIED")
        notify_pm({"task_id": task_id, "status": "VERIFIED", "message": "修复效果验证通过"})
    else:
        update_report_status(task_id, "REVIEW_REQUIRED")
        notify_pm({"task_id": task_id, "status": "REVIEW_REQUIRED", "issues": issues})
    
    return issues
```

### Step 7: 获取推荐意见

```python
def get_recommendation(compliance_status, issues):
    """
    根据合规状态和问题生成推荐意见
    """
    if compliance_status == "PASS":
        return "可交付"
    
    elif compliance_status == "PARTIAL":
        p1_issues = [i for i in issues if i["severity"] == "P1"]
        issue_types = ", ".join(set(i["type"] for i in p1_issues))
        return f"需修复后交付（{issue_types}）"
    
    else:  # FAIL
        p0_issues = [i for i in issues if i["severity"] == "P0"]
        issue_types = ", ".join(set(i["type"] for i in p0_issues))
        return f"升级人工介入（{issue_types}）"
```

## 输出格式

### 合规审计报告

```markdown
# 合规审计报告 - {task_id}

## 基本信息
- 任务 ID：{task_id}
- 工作流模式：{L1/L2/L3}
- 审计时间：{timestamp}
- 合规状态：{PASS/PARTIAL/FAIL}

## 执行合规性

| 检查项 | 状态 | 说明 |
|--------|------|------|
| PM 需求分析 | ✅/❌ | 已执行/未执行 |
| Arch 架构设计 | ✅/❌/⚠️ | 已执行/未执行/简化执行 |
| Fullstack 开发 | ✅/❌ | 已执行/未执行 |
| Reviewer 审查 | ✅/❌ | 已执行/未执行 |
| QA 测试 | ✅/❌/⚠️ | 已执行/未执行/简化执行 |
| 人工审核节点 | ✅/❌ | 已触发/未触发 |

## 质量门禁

| 门禁项 | 阈值 | 实际值 | 状态 |
|--------|------|--------|------|
| Reviewer deliverable_allowed | true（无 P0，P1 ≤ 2 暂缓） | {bool} | ✅/❌ |
| QA deliverable_allowed | true（通过率 L2≥95%/L3≥99%，覆盖率 L2≥60%/L3≥75%） | {bool} | ✅/❌ |
| 制品 Schema 校验 | 通过 | 通过/失败 | ✅/❌ |
| artifact_ref 版本一致性 | 一致 | 一致/不一致 | ✅/❌ |

## 发现的问题

### P0 问题（必须修复）
- [问题描述] - 建议修正：[动作]

### P1 问题（应该修复）
- [问题描述] - 建议修正：[动作]

### P2/P3 问题（建议优化）
- [问题描述] - 建议修正：[动作]

## 结论

- 合规状态：{PASS/PARTIAL/FAIL}
- 建议：{可交付/需修复后交付/升级人工介入}
- 下一步：{继续执行/打回修复/人工审核}
```

### 审计日志

```json
{
  "task_id": "{task_id}",
  "audit_time": "{timestamp}",
  "workflow_mode": "{L1/L2/L3}",
  "compliance_status": "{PASS/PARTIAL/FAIL}",
  "issues_found": {
    "P0": {n},
    "P1": {n},
    "P2": {n},
    "P3": {n}
  },
  "audit_items": [
    {"item": "workflow_completeness", "status": "PASS/FAIL"},
    {"item": "quality_gates", "status": "PASS/FAIL"},
    {"item": "artifact_completeness", "status": "PASS/FAIL"}
  ],
  "recommendation": "{推荐意见}",
  "timestamp": "{ISO8601}"
}
```

## 验证步骤

1. [ ] 成功获取任务信息
2. [ ] 工作流阶段完整性检查覆盖 L1/L2/L3
3. [ ] 质量门禁检查覆盖所有门禁项
4. [ ] 制品完整性检查覆盖所有必需制品
5. [ ] 审计报告包含所有必要字段
6. [ ] 修复效果验证正确执行
7. [ ] 审计报告正确保存和通知
8. [ ] issue_summary 统计正确

## 常见陷阱

1. **审计范围不完整**：遗漏某些工作流模式的检查
2. **质量门禁阈值错误**：QA 通过率阈值设置错误
3. **修复验证不执行**：只检查一次，不验证修复效果
4. **审计报告格式不一致**：报告内容与模板不一致