---
name: compliance-monitor
description: "Commander 流程合规性监控 Skill：监控各 Profile 是否遵循工作流规范（L1/L2/L3）、状态流转规则、人工审核节点触发。当需要检查流程合规性时触发。"
version: 1.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [commander, compliance, workflow-monitor, status-check, audit]
    related_skills: [artifact-verifier, workflow-auditor]
    workflow_modes: [L1, L2, L3]
---

# Compliance Monitor Skill

## 核心原则

持续监控各 Profile 是否按规则执行，发现违规行为立即通知 PM。监控内容包括：工作流规范执行、状态流转合法性、人工审核节点触发、门禁规则遵守。

## 监控规则

### 工作流强制规则

| 工作流 | 必检项 | 违规判定 |
|--------|--------|---------|
| L1 | Reviewer 快速检查必须执行 | REVIEW_REPORT 未产出 → flow_violation |
| L2 | Arch 轻量设计 → Fullstack → Reviewer 标准审查 | Arch 未产出 OPENAPI + DDL → flow_violation |
| L3 | Arch → Fullstack → Reviewer + QA 并行 → PM 汇合 | Reviewer 或 QA 任一未执行 → flow_violation |

### 状态流转规则

```
DISPATCHED → IN_PROGRESS → PARTIAL_DONE → DONE
                 ↓              ↓
              STALE          FAILED
                 ↓              ↓
             (重新分发)     (重试或升级)
```

| 当前状态 | 允许的下一状态 | 违规判定 |
|---------|--------------|---------|
| DISPATCHED | IN_PROGRESS | 直接跳到 DONE/FAILED → status_transition_violation |
| IN_PROGRESS | DONE, FAILED, BLOCKED, STALE | 直接跳到 DISPATCHED → status_transition_violation |
| STALE | IN_PROGRESS (重新执行) | 继续执行旧版本制品 → stale_violation |
| BLOCKED | IN_PROGRESS (审核通过后) | 未审核直接继续 → blocked_violation |
| FAILED | IN_PROGRESS (修复后) | 超过 2 次 FAILED 未升级 → failed_exceeded |

### 门禁强制规则

| 规则 | 违规判定 | 紧急程度 |
|------|---------|---------|
| Reviewer 审查不可跳过 | REVIEW_REPORT 未产出 | P1 |
| Reviewer deliverable_allowed | deliverable_allowed=false（无 P0，P1 ≤ 2 暂缓） | P0 |
| QA 通过率 L2 ≥ 95% / L3 ≥ 99% | 低于对应阈值 | P1 |
| QA 覆盖率 L2 ≥ 60% / L3 ≥ 75% | 低于对应阈值 | P1 |
| FAILED 超 2 次 | failed_count >= 2 | P0 |
| 人工审核超时 >2h | elapsed > 2h | P3 |

## 触发条件

- Commander 收到事件订阅（任务分发、状态变更、制品提交）
- Commander 定期轮询（每 5min）
- 用户说"检查流程合规性"、"监控任务状态"、"合规审计"

## 输入

- **必需**：任务状态数据（通过 Kanban MCP 获取）
- **必需**：工作流模式配置（L1/L2/L3）
- **可选**：审计历史记录、问题报告

## 输出制品

- **COMPLIANCE_CHECK_RESULT**：合规检查结果（artifact_type: COMPLIANCE_CHECK_RESULT）
- **ISSUE_NOTIFICATION**：问题通知（发送给 PM）

## 执行步骤

### Step 1: 获取任务状态

```python
def get_task_status(task_id):
    """
    通过 Kanban MCP 获取任务状态
    """
    return kanban.get_task_status(task_id)
```

### Step 2: 检查工作流合规性

```python
def check_workflow_compliance(task_id, workflow_mode):
    """
    检查任务是否遵循工作流规范
    """
    issues = []
    
    # 获取任务链
    task_chain = kanban.list_tasks(filter={"parent_id": task_id})
    
    if workflow_mode == "L1":
        # L1: PM → Fullstack → Reviewer → 交付
        reviewer_found = any(t["role"] == "reviewer" and t["status"] == "DONE" for t in task_chain)
        if not reviewer_found:
            issues.append({
                "type": "flow_violation",
                "severity": "P1",
                "message": "L1 模式下 Reviewer 审查未执行，禁止跳过",
                "task_id": task_id
            })
    
    elif workflow_mode == "L2":
        # L2: PM → Arch → Fullstack → Reviewer → 交付
        arch_found = any(t["role"] == "arch" for t in task_chain)
        reviewer_found = any(t["role"] == "reviewer" and t["status"] == "DONE" for t in task_chain)
        
        if not arch_found:
            issues.append({
                "type": "flow_violation",
                "severity": "P1",
                "message": "L2 模式下 Arch 架构设计未执行",
                "task_id": task_id
            })
        
        if not reviewer_found:
            issues.append({
                "type": "flow_violation",
                "severity": "P1",
                "message": "L2 模式下 Reviewer 审查未执行，禁止跳过",
                "task_id": task_id
            })
    
    elif workflow_mode == "L3":
        # L3: PM → Arch → Fullstack → Reviewer + QA → 交付
        arch_found = any(t["role"] == "arch" for t in task_chain)
        reviewer_found = any(t["role"] == "reviewer" and t["status"] == "DONE" for t in task_chain)
        qa_found = any(t["role"] == "qa" and t["status"] == "DONE" for t in task_chain)
        
        if not arch_found:
            issues.append({
                "type": "flow_violation",
                "severity": "P1",
                "message": "L3 模式下 Arch 架构设计未执行",
                "task_id": task_id
            })
        
        if not reviewer_found:
            issues.append({
                "type": "flow_violation",
                "severity": "P1",
                "message": "L3 模式下 Reviewer 审查未执行，禁止跳过",
                "task_id": task_id
            })
        
        if not qa_found:
            issues.append({
                "type": "flow_violation",
                "severity": "P1",
                "message": "L3 模式下 QA 测试未执行",
                "task_id": task_id
            })
    
    return issues
```

### Step 3: 检查状态流转

```python
def check_status_transition(task_id):
    """
    检查状态流转是否合法
    """
    issues = []
    task = kanban.get_task(task_id)
    status_history = task.get("status_history", [])
    
    if len(status_history) >= 2:
        current_status = status_history[-1]["status"]
        previous_status = status_history[-2]["status"]
        
        # 检查状态流转规则
        allowed_transitions = {
            "DISPATCHED": ["IN_PROGRESS"],
            "IN_PROGRESS": ["DONE", "FAILED", "BLOCKED", "STALE"],
            "STALE": ["IN_PROGRESS"],
            "BLOCKED": ["IN_PROGRESS"],
            "FAILED": ["IN_PROGRESS"]
        }
        
        if previous_status in allowed_transitions:
            if current_status not in allowed_transitions[previous_status]:
                issues.append({
                    "type": "status_transition_violation",
                    "severity": "P2",
                    "message": f"状态流转违规：{previous_status} → {current_status}，不允许的状态转换",
                    "task_id": task_id
                })
    
    # 检查 FAILURE 次数
    failed_count = sum(1 for s in status_history if s["status"] == "FAILED")
    if failed_count >= 2:
        issues.append({
            "type": "failed_exceeded",
            "severity": "P0",
            "message": f"FAILED 次数超过 2 次（当前 {failed_count} 次），需升级人工介入",
            "task_id": task_id
        })
    
    return issues
```

### Step 4: 检查人工审核节点

```python
def check_audit_nodes(task_id):
    """
    检查人工审核节点是否被正确触发
    """
    issues = []
    task = kanban.get_task(task_id)
    audit_nodes = task.get("audit_nodes", [])
    
    for node in audit_nodes:
        if node["required"] and not node.get("triggered"):
            issues.append({
                "type": "audit_node_violation",
                "severity": "P1",
                "message": f"必需的审核节点 {node['name']} 未触发",
                "task_id": task_id
            })
        
        # 检查超时
        if node.get("triggered") and node.get("timeout"):
            elapsed = calculate_elapsed(node["triggered_at"])
            if elapsed > 2 * 60 * 60:  # 2h
                issues.append({
                    "type": "timeout",
                    "severity": "P3",
                    "message": f"审核节点 {node['name']} 超时（已超过 2h）",
                    "task_id": task_id
                })
    
    return issues
```

### Step 5: 汇总结果并通知

```python
def generate_compliance_result(task_id, issues):
    """
    生成合规检查结果
    """
    result = {
        "task_id": task_id,
        "audit_time": get_current_time(),
        "status": "COMPLIANT" if len(issues) == 0 else "NON_COMPLIANT",
        "issues": issues,
        "issue_summary": {
            "P0": len([i for i in issues if i["severity"] == "P0"]),
            "P1": len([i for i in issues if i["severity"] == "P1"]),
            "P2": len([i for i in issues if i["severity"] == "P2"]),
            "P3": len([i for i in issues if i["severity"] == "P3"])
        }
    }
    
    # 如果有问题，通知 PM
    if len(issues) > 0:
        notify_pm(result)
    
    return result
```

## 输出格式

### 合规检查结果

```markdown
# 合规检查结果 - {task_id}

## 基本信息
- 任务 ID：{task_id}
- 工作流模式：{L1/L2/L3}
- 检查时间：{timestamp}
- 合规状态：{COMPLIANT/NON_COMPLIANT}

## 检查项

| 检查项 | 状态 | 说明 |
|--------|------|------|
| 工作流规范 | ✅/❌ | 符合/不符合工作流要求 |
| 状态流转 | ✅/❌ | 合法/违规 |
| 人工审核节点 | ✅/❌ | 已触发/未触发 |
| 门禁规则 | ✅/❌ | 已遵守/未遵守 |

## 发现的问题

### P0 问题（必须修复）
- [问题描述]

### P1 问题（应该修复）
- [问题描述]

### P2/P3 问题（建议优化）
- [问题描述]

## 结论

- 合规状态：{COMPLIANT/NON_COMPLIANT}
- 建议：{继续执行/通知 PM 修正}
```

### 问题通知

```
【问题通知】
问题Profile：[profile_name]
问题类型：[flow_violation | status_transition_violation | failed_exceeded | timeout]
问题描述：[具体问题描述]
影响范围：[影响范围说明]
建议修正：[建议的修正动作]
紧急程度：[P0/P1/P2/P3]
时间：[发现时间]
```

## 验证步骤

1. [ ] 成功获取任务状态
2. [ ] 工作流合规性检查覆盖 L1/L2/L3
3. [ ] 状态流转检查覆盖所有状态转换
4. [ ] FAILURE 次数检查正确（>=2 次触发 P0）
5. [ ] 人工审核节点超时检查正确（>2h 触发 P3）
6. [ ] 发现问题时正确通知 PM
7. [ ] 输出包含 issue_summary 统计

## 常见陷阱

1. **跳过 Reviewer 检查**：认为 L1/L2 模式不需要审查
2. **状态流转检查不完整**：遗漏某些状态转换的检查
3. **FAILURE 次数统计错误**：未正确统计历史失败次数
4. **超时计算错误**：时区处理不当导致超时判断错误