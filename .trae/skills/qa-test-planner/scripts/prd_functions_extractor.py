#!/usr/bin/env python3
"""
PRD 功能提取脚本

从 PRD 文档中提取功能清单，生成标准格式的 prd_functions 列表
"""

import json
import sys
import re
from typing import List, Dict, Any


def extract_prd_functions(prd_content: str) -> List[Dict[str, Any]]:
    """
    从 PRD 文档内容中提取功能清单
    
    Args:
        prd_content: PRD 文档内容（Markdown 格式）
        
    Returns:
        PRD 功能列表
    """
    functions = []
    
    # 匹配功能 ID 和名称的模式
    # 例如: F1.1 用户注册-邮箱注册 或 ## F1.1 用户注册
    patterns = [
        r'(?:^|[**])\s*([FF]\d+\.\d+)\s+([^\n*]+)',  # F1.1 用户注册-邮箱注册
        r'##\s*([FF]\d+\.\d+)\s+([^\n#]+)',          # ## F1.1 用户注册
        r'\[([FF]\d+\.\d+)\]\s*[-：:]\s*([^\n]+)',    # [F1.1] - 用户注册
    ]
    
    for pattern in patterns:
        for match in re.finditer(pattern, prd_content, re.MULTILINE):
            func_id = match.group(1)
            func_name = match.group(2).strip()
            
            # 避免重复
            if not any(f["id"] == func_id for f in functions):
                # 判断优先级
                priority = "P0" if "注册" in func_name or "登录" in func_name or "核心" in func_name else "P1"
                
                functions.append({
                    "id": func_id,
                    "name": func_name,
                    "priority": priority,
                    "module": infer_module(func_name)
                })
    
    return functions


def infer_module(func_name: str) -> str:
    """根据功能名称推断所属模块"""
    if "用户" in func_name or "注册" in func_name or "登录" in func_name:
        return "user-service"
    elif "商品" in func_name or "搜索" in func_name:
        return "product-service"
    elif "订单" in func_name or "下单" in func_name:
        return "order-service"
    elif "支付" in func_name:
        return "payment-service"
    else:
        return "common"


def main():
    if len(sys.argv) < 2:
        # 示例
        sample_prd = """
        # AI-Native 软件开发工厂 需求说明书
        
        ## F1.1 用户注册-邮箱注册
        
        ## F1.2 用户注册-验证码注册
        
        ## F2.1 用户登录-密码登录
        
        ## F3.1 商品搜索-关键词搜索
        """
        
        result = extract_prd_functions(sample_prd)
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        with open(sys.argv[1], 'r', encoding='utf-8') as f:
            prd_content = f.read()
        
        result = extract_prd_functions(prd_content)
        print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()