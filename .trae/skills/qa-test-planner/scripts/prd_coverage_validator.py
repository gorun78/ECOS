#!/usr/bin/env python3
"""
PRD 覆盖率验证脚本

验证每个 PRD 功能都有至少一个测试用例覆盖
"""

import json
import sys
from typing import Dict, List, Any


def validate_prd_coverage(
    prd_functions: List[Dict],
    test_cases: List[Dict]
) -> Dict[str, Any]:
    """
    PRD 覆盖率验证
    
    Args:
        prd_functions: PRD 功能清单
        test_cases: 测试用例列表（每个用例需有 prd_ref 字段）
        
    Returns:
        覆盖率验证结果
    """
    covered_map = {}
    for tc in test_cases:
        prd_ref = tc.get("prd_ref", "")
        if prd_ref not in covered_map:
            covered_map[prd_ref] = []
        covered_map[prd_ref].append(tc["id"])

    rows = []
    all_covered = True
    for func in prd_functions:
        covered_tcs = covered_map.get(func["id"], [])
        is_covered = len(covered_tcs) > 0
        if not is_covered:
            all_covered = False
        rows.append({
            "prd_id": func["id"],
            "prd_name": func["name"],
            "priority": func.get("priority", "P1"),
            "covered": "✅" if is_covered else "❌",
            "test_cases": ", ".join(covered_tcs) if covered_tcs else "无",
        })

    coverage_rate = len([r for r in rows if r["covered"] == "✅"]) / len(rows) if rows else 0

    return {
        "rows": rows,
        "total": len(rows),
        "covered": len([r for r in rows if r["covered"] == "✅"]),
        "uncovered": len([r for r in rows if r["covered"] == "❌"]),
        "coverage_rate": coverage_rate,
        "all_covered": all_covered,
        "pass": all_covered
    }


def generate_markdown_report(result: Dict[str, Any]) -> str:
    """生成 Markdown 格式覆盖率报告"""
    lines = [
        "## PRD 功能覆盖率报告",
        "",
        f"| PRD ID | 功能名称 | 优先级 | 覆盖状态 | 对应测试用例 |",
        f"|--------|---------|--------|---------|-------------|"
    ]
    
    for row in result["rows"]:
        lines.append(
            f"| {row['prd_id']} | {row['prd_name']} | {row['priority']} | "
            f"{row['covered']} | {row['test_cases']} |"
        )
    
    lines.extend([
        "",
        f"**PRD 功能覆盖率：{result['covered']}/{result['total']} = {result['coverage_rate']*100:.1f}%**"
    ])
    
    if not result["all_covered"]:
        uncovered = [r for r in result["rows"] if r["covered"] == "❌"]
        lines.append("")
        lines.append("**未覆盖功能：**")
        for r in uncovered:
            lines.append(f"- {r['prd_id']} {r['prd_name']}")
        lines.append("")
        lines.append("⚠️ **阻断条件**：PRD 功能覆盖率必须 100%，请补充测试用例后才可进入执行阶段")
    
    return "\n".join(lines)


def main():
    if len(sys.argv) < 2:
        # 示例数据
        prd_functions = [
            {"id": "F1.1", "name": "用户注册-邮箱注册", "priority": "P0"},
            {"id": "F1.2", "name": "用户注册-验证码注册", "priority": "P1"},
            {"id": "F2.1", "name": "用户登录-密码登录", "priority": "P0"},
        ]
        
        test_cases = [
            {"id": "UT-001", "prd_ref": "F1.1"},
            {"id": "IT-001", "prd_ref": "F1.1"},
            {"id": "UT-002", "prd_ref": "F2.1"},
        ]
        
        result = validate_prd_coverage(prd_functions, test_cases)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        print("\n--- Markdown Report ---")
        print(generate_markdown_report(result))
    else:
        # 从文件读取
        with open(sys.argv[1], 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        result = validate_prd_coverage(
            data.get("prd_functions", []),
            data.get("test_cases", [])
        )
        
        if "--markdown" in sys.argv:
            print(generate_markdown_report(result))
        else:
            print(json.dumps(result, ensure_ascii=False, indent=2))
        
        if not result["pass"]:
            sys.exit(1)


if __name__ == "__main__":
    main()