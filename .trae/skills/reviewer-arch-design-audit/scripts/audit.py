#!/usr/bin/env python3
"""
架构设计审计主入口脚本

执行完整的审计流程：解析 -> 校验 -> 分类 -> 判定 -> 报告
"""

import sys
import os
import json
import hashlib
from datetime import datetime

# 添加当前目录到 Python 路径
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from parsers import parse_inputs
from validators import run_all_validators
from categorizer import categorize_issues
from decision import make_decision
from reporter import generate_audit_report, generate_approval_record
from utils import resolve_review_dir


def run_audit(task_id, prd_ref, arch_spec_ref, openapi_ref, ddl_ref):
    """
    执行完整的架构设计审计流程
    
    Args:
        task_id (str): 任务 ID
        prd_ref (str): PRD 制品引用
        arch_spec_ref (str): 架构规格书制品引用
        openapi_ref (str): OpenAPI 规范制品引用
        ddl_ref (str): DDL 脚本制品引用
        
    Returns:
        tuple: (审计报告, 判定结果, 问题统计)
    """
    timestamp = datetime.now().isoformat()
    
    # Step 1: 依赖解析与对齐
    print("[Step 1/5] 依赖解析与对齐...")
    parsed_inputs = parse_inputs(prd_ref, arch_spec_ref, openapi_ref, ddl_ref)
    
    # Step 2: 规则链式校验
    print("[Step 2/5] 规则链式校验...")
    all_issues = run_all_validators(parsed_inputs)
    
    # Step 3: 问题分类与评级
    print("[Step 3/5] 问题分类与评级...")
    categorized_issues = categorize_issues(all_issues)
    
    # Step 4: 综合决策判定
    print("[Step 4/5] 综合决策判定...")
    decision_result = make_decision(categorized_issues)
    
    # Step 5: 结构化报告输出
    print("[Step 5/5] 生成审计报告...")
    
    # 构建追溯矩阵
    traceability_matrix = []
    prd = parsed_inputs["prd"]
    arch = parsed_inputs["arch"]
    api = parsed_inputs["api"]
    db = parsed_inputs["db"]
    
    for entity in prd.get("entities", []):
        entity_name = entity.get("name", "")
        # 查找对应的表
        table_name = "-"
        for table in db.get("tables", []):
            if table["name"] == entity_name or table["name"] == entity_name.lower():
                table_name = table["name"]
                break
        
        traceability_matrix.append({
            "requirement": f"业务实体: {entity_name}",
            "module": "-",
            "api": "-",
            "table": table_name,
            "status": "✅" if table_name != "-" else "❌"
        })
    
    for flow in prd.get("flows", []):
        flow_name = flow.get("name", "")
        for action in flow.get("actions", []):
            action_name = action.get("name", "")
            api_name = action.get("api_operation", "-")
            traceability_matrix.append({
                "requirement": f"业务流程: {flow_name} -> {action_name}",
                "module": "-",
                "api": api_name,
                "table": "-",
                "status": "✅" if api_name != "-" else "❌"
            })
    
    report = generate_audit_report(
        task_id=task_id,
        timestamp=timestamp,
        categorized_issues=categorized_issues,
        decision_result=decision_result,
        traceability_matrix=traceability_matrix
    )
    
    print(f"\n审计完成！结果: {decision_result['decision']}")
    print(f"BLOCKER: {categorized_issues['summary']['blocker']}")
    print(f"WARNING: {categorized_issues['summary']['warning']}")
    print(f"SUGGESTION: {categorized_issues['summary']['suggestion']}")
    
    return report, decision_result, categorized_issues


if __name__ == "__main__":
    if len(sys.argv) < 6:
        print("用法: python audit.py <task_id> <prd_ref> <arch_spec_ref> <openapi_ref> <ddl_ref>")
        sys.exit(1)
    
    task_id = sys.argv[1]
    prd_ref = sys.argv[2]
    arch_spec_ref = sys.argv[3]
    openapi_ref = sys.argv[4]
    ddl_ref = sys.argv[5]

    report, decision, issues = run_audit(task_id, prd_ref, arch_spec_ref, openapi_ref, ddl_ref)

    # 动态解析审查报告目录（按 docs/AGENTS.md「审查报告子目录」，不硬编码序号）
    REPORT_DIR = resolve_review_dir(os.path.join("docs", "02设计阶段"), "02")

    # 报告哈希（用于批准记录追溯）
    report_hash = hashlib.sha256(report.encode("utf-8")).hexdigest()[:16]

    # 1. 审计报告落盘
    report_file = os.path.join(REPORT_DIR, f"{task_id}_arch_design_audit_report.md")
    with open(report_file, "w", encoding="utf-8") as f:
        f.write(report)

    # 2. 批准记录落盘（APPROVAL_RECORD 必须 JSON 持久化）
    approval_record = generate_approval_record(
        task_id=task_id,
        project_name=task_id,
        version=report_hash,
        report_hash=report_hash,
        status=decision["decision"],
        workflow_mode="L3",
        categorized_issues=issues,
        prd_ref=prd_ref,
        arch_spec_ref=arch_spec_ref,
        openapi_ref=openapi_ref,
        ddl_ref=ddl_ref,
    )
    approval_file = os.path.join(REPORT_DIR, f"{task_id}_arch_design_audit_approval_record.json")
    with open(approval_file, "w", encoding="utf-8") as f:
        json.dump(approval_record, f, ensure_ascii=False, indent=2)

    print(f"\n审计报告已保存到: {report_file}")
    print(f"批准记录已保存到: {approval_file}")
