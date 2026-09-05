#!/usr/bin/env python3
"""Fullstack End-to-End Skill 主入口脚本

执行完整的端到端开发流程：
1. 需求匹配与追溯
2. 后端开发（委托 backend-builder）
3. 前端开发（委托 vue3-frontend-builder）
4. 集成测试生成
5. 交付门禁验证
"""

import argparse
import json
import os
from typing import Dict, List, Any

from requirement_matcher import match_requirements
from traceability_recorder import record_traceability
from integration_test_generator import generate_integration_tests
from delivery_gate import run_delivery_gates
from utils import ensure_directory, generate_timestamp, save_json_file


def run_end_to_end_workflow(args: argparse.Namespace) -> Dict[str, Any]:
    """执行完整的端到端开发工作流"""
    workflow_id = f"E2E_{generate_timestamp().replace(':', '').replace('-', '').replace('.', '')}"
    
    print(f"🚀 启动 Fullstack End-to-End 工作流: {workflow_id}")
    print("=" * 70)
    
    # 阶段 0: 需求匹配与追溯记录
    print("\n📋 阶段 0: 需求匹配与追溯记录")
    print("-" * 70)
    
    match_result = match_requirements(
        prd_path=args.prd,
        prototype_path=args.prototype,
        output_path=os.path.join(args.output, 'traceability', 'match-result.json')
    )
    
    print(f"  PRD: {match_result['prd_path']}")
    print(f"  原型: {match_result['prototype_path']}")
    print(f"  需求数: {match_result['requirement_count']}")
    print(f"  页面数: {match_result['page_count']}")
    print(f"  匹配数: {match_result['match_count']}")
    print(f"  未匹配需求: {match_result['orphan_requirements']}")
    print(f"  未匹配页面: {match_result['orphan_pages']}")
    
    # 记录追溯信息
    traceability_result = record_traceability(
        match_result=match_result,
        output_dir=os.path.join(args.output, 'traceability')
    )
    
    print(f"  追溯记录: {traceability_result['file_path']}")
    print(f"  匹配率: {traceability_result['summary']['summary']['match_rate']}%")
    
    # 阶段 1: 后端开发（委托 backend-builder）
    print("\n🔧 阶段 1: 后端开发（委托 backend-builder）")
    print("-" * 70)
    
    backend_result = delegate_backend_development(
        openapi_ref=args.openapi,
        output_path=args.backend_output
    )
    
    # 阶段 2: 前端开发（委托 vue3-frontend-builder）
    print("\n🎨 阶段 2: 前端开发（委托 vue3-frontend-builder）")
    print("-" * 70)
    
    frontend_result = delegate_frontend_development(
        prd_ref=args.prd,
        prototype_ref=args.prototype,
        output_path=args.frontend_output
    )
    
    # 阶段 3: 集成测试生成
    print("\n🧪 阶段 3: 集成测试生成")
    print("-" * 70)
    
    api_endpoints = extract_api_endpoints(args.openapi)
    test_result = generate_integration_tests(
        api_endpoints=api_endpoints,
        output_dir=os.path.join(args.output, 'tests')
    )
    
    print(f"  前端集成测试: {test_result['summary']['frontend']['count']} 个")
    print(f"  后端集成测试: {test_result['summary']['backend']['count']} 个")
    print(f"  API 契约测试: {test_result['summary']['contract']['count']} 个")
    
    # 阶段 4: 交付门禁验证
    print("\n✅ 阶段 4: 交付门禁验证")
    print("-" * 70)
    
    delivery_result = run_delivery_gates(
        frontend_path=args.frontend_output,
        backend_path=args.backend_output,
        tests_path=os.path.join(args.output, 'tests'),
        traceability_path=os.path.join(args.output, 'traceability'),
        output_dir=os.path.join(args.output, 'delivery')
    )
    
    print(f"  交付报告: {delivery_result['markdown_report_path']}")
    print(f"  交付许可: {'✅ 允许' if delivery_result['result']['deliverable_allowed'] else '❌ 禁止'}")
    
    # 生成完整工作流报告
    workflow_report = {
        'workflow_id': workflow_id,
        'timestamp': generate_timestamp(),
        'inputs': {
            'prd': args.prd,
            'prototype': args.prototype,
            'openapi': args.openapi,
            'backend_output': args.backend_output,
            'frontend_output': args.frontend_output
        },
        'stages': {
            'traceability': traceability_result,
            'backend': backend_result,
            'frontend': frontend_result,
            'integration_tests': test_result,
            'delivery_gate': delivery_result['result']
        },
        'deliverable_allowed': delivery_result['result']['deliverable_allowed'],
        'summary': {
            'total_requirements': match_result['requirement_count'],
            'matched_requirements': match_result['match_count'],
            'match_rate': traceability_result['summary']['summary']['match_rate'],
            'frontend_tests': test_result['summary']['frontend']['count'],
            'backend_tests': test_result['summary']['backend']['count'],
            'contract_tests': test_result['summary']['contract']['count'],
            'gates_passed': delivery_result['result']['all_passed']
        }
    }
    
    # 保存工作流报告
    report_path = os.path.join(args.output, f'{workflow_id}_report.json')
    save_json_file(report_path, workflow_report)
    
    print("\n" + "=" * 70)
    print(f"📊 工作流完成: {workflow_id}")
    print(f"  报告: {report_path}")
    print(f"  交付许可: {'✅ 允许交付' if workflow_report['deliverable_allowed'] else '❌ 禁止交付'}")
    
    return workflow_report


def delegate_backend_development(openapi_ref: str, output_path: str) -> Dict[str, Any]:
    """委托 backend-builder 进行后端开发"""
    # 实际执行时会调用 backend-builder skill
    return {
        'status': 'DELEGATED',
        'skill': 'backend-builder',
        'openapi_ref': openapi_ref,
        'output_path': output_path,
        'message': '后端开发已委托给 backend-builder skill'
    }


def delegate_frontend_development(prd_ref: str, prototype_ref: str, output_path: str) -> Dict[str, Any]:
    """委托 vue3-frontend-builder 进行前端开发"""
    # 实际执行时会调用 vue3-frontend-builder skill
    return {
        'status': 'DELEGATED',
        'skill': 'vue3-frontend-builder',
        'prd_ref': prd_ref,
        'prototype_ref': prototype_ref,
        'output_path': output_path,
        'message': '前端开发已委托给 vue3-frontend-builder skill'
    }


def extract_api_endpoints(openapi_ref: str) -> List[Dict[str, Any]]:
    """从 OpenAPI 规范中提取 API 端点信息"""
    # 简化实现：解析 OpenAPI 文件提取端点
    if not os.path.exists(openapi_ref):
        return []
    
    try:
        with open(openapi_ref, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        endpoints = []
        paths = data.get('paths', {})
        
        for path, methods in paths.items():
            for method, spec in methods.items():
                if method in ['get', 'post', 'put', 'delete', 'patch']:
                    endpoints.append({
                        'method': method.upper(),
                        'path': path,
                        'operationId': spec.get('operationId', ''),
                        'controller': spec.get('x-controller', 'Controller'),
                        'requestBody': spec.get('requestBody', {}).get('content', {}).get('application/json', {}).get('schema', {}),
                        'response': spec.get('responses', {}).get('200', {}).get('content', {}).get('application/json', {}).get('schema', {})
                    })
        
        return endpoints
    except Exception:
        return []


def main():
    parser = argparse.ArgumentParser(description='Fullstack End-to-End Skill')
    parser.add_argument('--prd', required=True, help='PRD 文档路径')
    parser.add_argument('--prototype', required=True, help='原型系统路径')
    parser.add_argument('--openapi', required=True, help='OpenAPI 规范路径')
    parser.add_argument('--backend-output', required=True, help='后端输出路径')
    parser.add_argument('--frontend-output', required=True, help='前端输出路径')
    parser.add_argument('--output', default='./output', help='输出目录')
    
    args = parser.parse_args()
    
    # 确保输出目录存在
    ensure_directory(args.output)
    ensure_directory(args.backend_output)
    ensure_directory(args.frontend_output)
    
    # 执行工作流
    run_end_to_end_workflow(args)


if __name__ == '__main__':
    main()
