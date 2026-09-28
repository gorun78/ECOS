#!/usr/bin/env python3
"""
PRD 批准状态校验脚本

测试计划前，必须校验 PRD 已 APPROVED
"""

import json
import sys
from typing import Dict, Any


def validate_prd_approved(prd_ref: str, approval_record: Dict[str, Any]) -> Dict[str, Any]:
    """
    校验 PRD 批准状态
    
    Args:
        prd_ref: PRD 引用（如 PRD@abc123）
        approval_record: PRD 批准记录
        
    Returns:
        校验结果字典
        
    Raises:
        ValueError: PRD 未 APPROVED 或 deliverable_allowed=false
    """
    if not approval_record:
        raise ValueError(f"PRD {prd_ref} 无批准记录")
    
    if approval_record.get("status") != "APPROVED":
        raise ValueError(f"PRD {prd_ref} 未 APPROVED，测试计划禁止开始")
    
    if not approval_record.get("deliverable_allowed"):
        raise ValueError("PRD deliverable_allowed=false，禁止开始测试计划")
    
    return {
        "prd_version": approval_record.get("version"),
        "prd_hash": approval_record.get("hash"),
        "functions": approval_record.get("functions", [])
    }


def main():
    if len(sys.argv) < 2:
        # 示例调用
        sample_record = {
            "status": "APPROVED",
            "version": "v1.0",
            "hash": "abc123",
            "deliverable_allowed": True,
            "functions": [
                {"id": "F1.1", "name": "用户注册-邮箱注册"},
                {"id": "F2.1", "name": "用户登录-密码登录"}
            ]
        }
        
        try:
            result = validate_prd_approved("PRD@abc123", sample_record)
            print(json.dumps(result, ensure_ascii=False, indent=2))
        except ValueError as e:
            print(f"错误: {e}", file=sys.stderr)
            sys.exit(1)
    else:
        # 从文件读取批准记录
        with open(sys.argv[1], 'r', encoding='utf-8') as f:
            approval_record = json.load(f)
        
        prd_ref = sys.argv[2] if len(sys.argv) > 2 else "PRD@unknown"
        
        try:
            result = validate_prd_approved(prd_ref, approval_record)
            print(json.dumps(result, ensure_ascii=False, indent=2))
        except ValueError as e:
            print(f"错误: {e}", file=sys.stderr)
            sys.exit(1)


if __name__ == "__main__":
    main()