#!/usr/bin/env python3
"""
OpenAPI API 提取脚本

从 OpenAPI 规范中提取 API 清单，生成标准格式的 apis 列表
"""

import json
import sys
from typing import List, Dict, Any


def extract_openapi_apis(openapi_spec: Dict[str, Any]) -> List[Dict[str, Any]]:
    """
    从 OpenAPI 规范中提取 API 清单
    
    Args:
        openapi_spec: OpenAPI 规范字典
        
    Returns:
        API 列表
    """
    apis = []
    
    paths = openapi_spec.get("paths", {})
    
    for path, methods in paths.items():
        for method, details in methods.items():
            if method.upper() in ["GET", "POST", "PUT", "DELETE", "PATCH"]:
                operation_id = details.get("operationId", f"{method.upper()}_{path.replace('/', '_')}")
                
                # 尝试从 tags 推断模块
                tags = details.get("tags", ["common"])
                module = tags[0] if tags else "common"
                
                apis.append({
                    "operationId": operation_id,
                    "method": method.upper(),
                    "path": path,
                    "module": module,
                    "summary": details.get("summary", ""),
                    "prd_ref": details.get("x-prd-ref", "")  # 自定义扩展字段
                })
    
    return apis


def generate_test_endpoints(apis: List[Dict]) -> List[Dict]:
    """生成测试端点列表"""
    test_endpoints = []
    
    for api in apis:
        test_endpoints.append({
            "test_id": f"IT-{api['operationId']}",
            "endpoint": api["path"],
            "method": api["method"],
            "operation_id": api["operationId"],
            "prd_ref": api.get("prd_ref", "")
        })
    
    return test_endpoints


def main():
    if len(sys.argv) < 2:
        # 示例
        sample_openapi = {
            "paths": {
                "/users": {
                    "post": {
                        "operationId": "createUser",
                        "summary": "创建用户",
                        "tags": ["user-service"],
                        "x-prd-ref": "F1.1"
                    }
                },
                "/users/login": {
                    "post": {
                        "operationId": "login",
                        "summary": "用户登录",
                        "tags": ["user-service"],
                        "x-prd-ref": "F2.1"
                    }
                },
                "/products": {
                    "get": {
                        "operationId": "listProducts",
                        "summary": "商品列表",
                        "tags": ["product-service"],
                        "x-prd-ref": "F3.1"
                    }
                }
            }
        }
        
        result = extract_openapi_apis(sample_openapi)
        print("=== APIs ===")
        print(json.dumps(result, ensure_ascii=False, indent=2))
        
        print("\n=== Test Endpoints ===")
        test_endpoints = generate_test_endpoints(result)
        print(json.dumps(test_endpoints, ensure_ascii=False, indent=2))
    else:
        with open(sys.argv[1], 'r', encoding='utf-8') as f:
            openapi_spec = json.load(f)
        
        result = extract_openapi_apis(openapi_spec)
        print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()