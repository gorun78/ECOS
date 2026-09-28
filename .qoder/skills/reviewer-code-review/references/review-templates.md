# reviewer-code-review — 拆分出的模板/示例

> 由 SKILL.md 拆出，SKILL.md 对应位置留有指针。生成相关制品前先读本文件。

## 块 1（原 SKILL.md L209-265）

```python
def map_ocr_to_reviewer(ocr_findings):
    """
    OCR category + severity → Reviewer P0/P1/P2/P3 映射规则

    | OCR category  | OCR severity | Reviewer Priority |
    |--------------|-------------|-------------------|
    | bug          | critical    | P0               |
    | bug          | high        | P1               |
    | bug          | medium      | P2               |
    | bug          | low         | P3               |
    | security     | critical    | P0               |
    | security     | high        | P1               |
    | security     | medium      | P1（security medium 升为 P1） |
    | security     | low         | P2               |
    | performance  | critical    | P0               |
    | performance  | high        | P1               |
    | performance  | medium      | P2               |
    | performance  | low         | P3               |
    | maintainability | high     | P2               |
    | maintainability | medium/low | P3             |
    """

    mapping = {
        ("bug", "critical"): "P0",
        ("bug", "high"): "P1",
        ("bug", "medium"): "P2",
        ("bug", "low"): "P3",
        ("security", "critical"): "P0",
        ("security", "high"): "P1",
        ("security", "medium"): "P1",
        ("security", "low"): "P2",
        ("performance", "critical"): "P0",
        ("performance", "high"): "P1",
        ("performance", "medium"): "P2",
        ("performance", "low"): "P3",
        ("maintainability", "high"): "P2",
        ("maintainability", "medium"): "P3",
        ("maintainability", "low"): "P3",
    }

    results = {"P0": [], "P1": [], "P2": [], "P3": []}
    for f in ocr_findings:
        priority = mapping.get((f["category"], f["severity"]), "P3")
        results[priority].append({
            "id": f"OCR-{f['file'].split('/')[-1][:8]}-{f['start_line']}",
            "file": f["file"],
            "line": f["start_line"],
            "end_line": f["end_line"],
            "category": f["category"],
            "severity": f["severity"],
            "rule": f.get("rule", ""),
            "content": f["content"],
            "confidence": f.get("ai_confidence", 1.0),
        })
    return results
```

## 块 2（原 SKILL.md L329-378）

```python
def evaluate_review_gates(ocr_defects, arch_violations, security_violations):
    """
    质量门禁判定：每个门禁有明确的 PASS/FAIL 状态
    """
    p0_count = len([d for d in ocr_defects if d["priority"] == "P0"])
    p1_count = len([d for d in ocr_defects if d["priority"] == "P1"])
    p2_count = len([d for d in ocr_defects if d["priority"] == "P2"])
    p3_count = len([d for d in ocr_defects if d["priority"] == "P3"])

    critical_security = len([v for v in security_violations if v["severity"] == "CRITICAL"])
    high_security = len([v for v in security_violations if v["severity"] == "HIGH"])
    p0_arch = len([v for v in arch_violations if v["priority"] == "P0"])
    p1_arch = len([v for v in arch_violations if v["priority"] == "P1"])

    # P0_GATE
    p0_gate = "PASS" if p0_count == 0 else "FAIL"

    # P1_GATE
    p1_gate = "PASS" if p1_count <= 3 else "FAIL"

    # SECURITY_GATE
    security_gate = "PASS" if critical_security == 0 and high_security == 0 else "FAIL"

    # ARCH_GATE
    arch_gate = "PASS" if p0_arch == 0 and p1_arch == 0 else "FAIL"

    # FINAL 判定
    all_pass = all(g == "PASS" for g in [p0_gate, p1_gate, security_gate, arch_gate])
    final_status = "PASS" if all_pass else "FAIL"
    deliverable_allowed = all_pass

    return {
        "gates": {
            "P0_GATE": {"status": p0_gate, "p0_count": p0_count},
            "P1_GATE": {"status": p1_gate, "p1_count": p1_count, "threshold": 3},
            "SECURITY_GATE": {"status": security_gate, "critical": critical_security, "high": high_security},
            "ARCH_GATE": {"status": arch_gate, "p0_arch": p0_arch, "p1_arch": p1_arch}
        },
        "final_status": final_status,
        "deliverable_allowed": deliverable_allowed,
        "defect_summary": {
            "total": p0_count + p1_count + p2_count + p3_count,
            "P0": p0_count,
            "P1": p1_count,
            "P2": p2_count,
            "P3": p3_count
        }
    }
```

## 块 3（原 SKILL.md L384-451）

```markdown
# 代码审查报告

**任务 ID**: {task_id}
**执行时间**: {timestamp}
**审查引擎**: Open Code Review v1.x
**Session ID**: {session_id}
**deliverable_allowed**: **{true/false}**

---

## 质量门禁结果

| 门禁 | 状态 | 实际值 | 阈值 | 说明 |
|------|------|--------|------|------|
| P0_GATE | **PASS / FAIL** | {n} 个 P0 | 必须 0 | P0 缺陷数 |
| P1_GATE | **PASS / FAIL** | {n} 个 P1 | ≤ 3 | P1 缺陷数 |
| SECURITY_GATE | **PASS / FAIL** | {n} 临界 / {n} 高危 | 0 | 安全漏洞 |
| ARCH_GATE | **PASS / FAIL** | {n} P0 / {n} P1 | 0 | 架构违规 |

**最终判定**：**{PASS / FAIL}**
**deliverable_allowed**: **{true / false}**

---

## 缺陷汇总

| 优先级 | 数量 | 说明 |
|--------|------|------|
| P0 | {n} | 必须修复，代码冻结 |
| P1 | {n} | 必须修复，≤ 3 个可通过 |
| P2 | {n} | 建议修复 |
| P3 | {n} | 优化建议 |

---

## 缺陷 → PRD 追溯表

| 缺陷 ID | 缺陷描述 | 优先级 | PRD 来源 | PRD 功能名称 | 文件位置 |
|---------|---------|--------|---------|-------------|---------|
| C-001 | SQL 注入漏洞 | P0 | PRD-F1.1 | 用户注册-邮箱注册 | UserServiceImpl.java:42 |
| C-002 | IDOR 越权访问 | P1 | PRD-F4.1 | 下单-创建订单 | OrderController.java:42 |
| C-003 | JWT 无过期时间 | P1 | PRD-F2.1 | 用户登录-密码登录 | AuthService.java:58 |

---

## 审查结论

**质量评估**：**{PASS / FAIL}**

| 场景 | 判定结果 | 说明 |
|------|---------|------|
| deliverable_allowed = true | ✅ 可交付 | 所有质量门禁通过 |
| deliverable_allowed = false | ⛔ 阻断交付 | 存在 P0/P1 缺陷或安全漏洞 |

{if deliverable_allowed = false}
**阻断原因**：
- P0 缺陷：{n} 个
- P1 缺陷：{n} 个（超过阈值 3 个）
- 安全漏洞：{n} 个 CRITICAL/HIGH
- 架构违规：{n} 个 P0/P1

→ 修复后重新审查
{endif}

审查报告：docs/03开发阶段/{NN}-审查报告/{task_id}_review_report.md
审查意见：docs/03开发阶段/{NN}-审查报告/{task_id}_review_comments.md
```

## 块 4（原 SKILL.md L457-502）

```json
{
  "artifact": "REVIEW_REPORT",
  "name": "{项目名称} 代码审查报告",
  "version": "v{version}",
  "hash": "{内容哈希}",
  "status": "{AUTO_CLOSED / PENDING_HUMAN_REVIEW}",
  "human_review_required": false,
  "workflow_mode": "L3",
  "approvals": [
    {
      "role": "reviewer-code-review",
      "result": "{APPROVED / REJECTED}",
      "timestamp": "{timestamp}",
      "conditions": []
    }
  ],
  "gates": {
    "P0_GATE": {"status": "PASS", "p0_count": 0},
    "P1_GATE": {"status": "PASS", "p1_count": 2, "threshold": 3},
    "SECURITY_GATE": {"status": "PASS", "critical": 0, "high": 0},
    "ARCH_GATE": {"status": "PASS", "p0_arch": 0, "p1_arch": 0}
  },
  "defect_summary": {
    "total": 12,
    "P0": 0,
    "P1": 2,
    "P2": 5,
    "P3": 5
  },
  "ocr_session_id": "{session_id}",
  "ocr_findings_count": 23,
  "arch_consistency_issues": 0,
  "security_issues": 0,
  "prd_defect_density": {
    "PRD-F1.1": {"p1_count": 1, "bugs": ["C-001"]},
    "PRD-F4.1": {"p1_count": 1, "bugs": ["C-002"]}
  },
  "deliverable_allowed": true,
  "source_patch_ref": "SOURCE_PATCH@{source_hash}",
  "prd_ref": "PRD@{prd_hash}",
  "prev_version": null,
  "next_version": null,
  "timestamp": "{ISO8601}"
}
```
