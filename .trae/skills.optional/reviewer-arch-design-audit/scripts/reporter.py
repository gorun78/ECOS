"""
报告生成器模块

负责生成架构设计审计报告
"""


def generate_audit_report(task_id, timestamp, categorized_issues, decision_result, traceability_matrix):
    """
    生成架构设计审计报告
    
    Args:
        task_id (str): 任务 ID
        timestamp (str): 执行时间
        categorized_issues (dict): 分类后的问题和统计摘要
        decision_result (dict): 判定结果
        traceability_matrix (list): 追溯矩阵数据
        
    Returns:
        str: Markdown 格式的审计报告
    """
    summary = categorized_issues["summary"]
    blocker_issues = categorized_issues["blocker"]
    warning_issues = categorized_issues["warning"]
    suggestion_issues = categorized_issues["suggestion"]
    
    report = f"""# 架构与设计审计报告

**任务 ID**: {task_id} | **执行时间**: {timestamp}

## 审计概述

| 项目 | 状态 |
|------|------|
| 审计结果 | {decision_result['decision']} |
| BLOCKER | {summary['blocker']} 个 |
| WARNING | {summary['warning']} 个 |
| SUGGESTION | {summary['suggestion']} 个 |

## 问题详情清单

"""
    
    # BLOCKER 问题
    if blocker_issues:
        report += """### BLOCKER（阻断级）

| 问题 ID | 类型 | 位置 | 违反规则 | 问题描述 | 修正建议 |
|---------|------|------|---------|---------|---------|
"""
        for issue in blocker_issues:
            location = issue.get("entity", "") or issue.get("table", "") or issue.get("api", "") or issue.get("module", "") or "-"
            report += f"| {issue['id']} | {issue['type']} | {location} | {issue['rule']} | {issue['message']} | {issue['suggestion']} |\n"
        report += "\n"
    
    # WARNING 问题
    if warning_issues:
        report += """### WARNING（警告级）

| 问题 ID | 类型 | 位置 | 违反规则 | 问题描述 | 修正建议 |
|---------|------|------|---------|---------|---------|
"""
        for issue in warning_issues:
            location = issue.get("table", "") or issue.get("api", "") or issue.get("module", "") or "-"
            report += f"| {issue['id']} | {issue['type']} | {location} | {issue['rule']} | {issue['message']} | {issue['suggestion']} |\n"
        report += "\n"
    
    # SUGGESTION 问题
    if suggestion_issues:
        report += """### SUGGESTION（建议级）

| 问题 ID | 类型 | 位置 | 违反规则 | 问题描述 | 修正建议 |
|---------|------|------|---------|---------|---------|
"""
        for issue in suggestion_issues:
            location = issue.get("module", "") or issue.get("api", "") or issue.get("table", "") or "-"
            report += f"| {issue['id']} | {issue['type']} | {location} | {issue['rule']} | {issue['message']} | {issue['suggestion']} |\n"
        report += "\n"
    
    # 追溯矩阵
    report += """## 追溯矩阵

| PRD 需求 | 架构模块 | API 接口 | 数据库表 | 覆盖状态 |
|---------|---------|---------|---------|---------|
"""
    for row in traceability_matrix:
        report += f"| {row.get('requirement', '-')} | {row.get('module', '-')} | {row.get('api', '-')} | {row.get('table', '-')} | {row.get('status', '-')} |\n"
    
    # 结论
    report += f"""
## 结论
**判定结果**: {decision_result['decision']}
**下一步建议**: {decision_result['next_step']}

---
**分发**: 抄送 PM、Arch、Commander
"""
    
    return report


def generate_approval_record(task_id, project_name, version, report_hash, 
                            status, workflow_mode, categorized_issues,
                            prd_ref, arch_spec_ref, openapi_ref, ddl_ref):
    """
    生成审计批准记录
    
    Args:
        task_id (str): 任务 ID
        project_name (str): 项目名称
        version (str): 版本号
        report_hash (str): 报告内容哈希
        status (str): 审计状态
        workflow_mode (str): 工作流模式
        categorized_issues (dict): 分类后的问题和统计摘要
        prd_ref (str): PRD 制品引用
        arch_spec_ref (str): 架构规格书制品引用
        openapi_ref (str): OpenAPI 规范制品引用
        ddl_ref (str): DDL 脚本制品引用
        
    Returns:
        dict: 批准记录
    """
    import json
    from datetime import datetime
    
    summary = categorized_issues["summary"]
    
    approval_result = "REJECTED"
    if status == "APPROVE":
        approval_result = "APPROVED"
    elif status == "PASS_WITH_CONDITIONS":
        approval_result = "CONDITIONAL_APPROVED"
    
    record = {
        "artifact": "ARCH_DESIGN_AUDIT_REPORT",
        "name": f"{project_name} 架构设计审计报告",
        "version": f"v{version}",
        "hash": report_hash,
        "status": status,
        "workflow_mode": workflow_mode,
        "approvals": [
            {
                "role": "reviewer-arch-design-audit",
                "result": approval_result,
                "timestamp": datetime.now().isoformat(),
                "conditions": []
            }
        ],
        "issue_summary": {
            "total": summary["total"],
            "blocker": summary["blocker"],
            "warning": summary["warning"],
            "suggestion": summary["suggestion"]
        },
        "deliverable_allowed": status != "REJECT",
        "prd_ref": prd_ref,
        "arch_spec_ref": arch_spec_ref,
        "openapi_ref": openapi_ref,
        "ddl_ref": ddl_ref,
        "timestamp": datetime.now().isoformat()
    }
    
    return record
