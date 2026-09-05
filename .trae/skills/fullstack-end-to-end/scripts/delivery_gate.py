import os
from typing import Dict, List, Any
from utils import (
    save_json_file,
    load_json_file,
    generate_timestamp,
    ensure_directory
)


class DeliveryGate:
    """交付门禁：验证前后端开发成果是否满足交付标准"""
    
    def __init__(self, output_dir: str = './delivery'):
        self.output_dir = output_dir
        ensure_directory(output_dir)
    
    def check_frontend_delivery(self, frontend_path: str) -> Dict[str, Any]:
        """检查前端交付物"""
        checks = []
        
        # 检查目录结构
        required_dirs = ['src/components', 'src/stores', 'src/router', 'src/types']
        for dir_name in required_dirs:
            dir_path = os.path.join(frontend_path, dir_name)
            exists = os.path.exists(dir_path)
            checks.append({
                'name': f'前端目录 {dir_name}',
                'path': dir_path,
                'passed': exists,
                'message': '存在' if exists else '缺失'
            })
        
        # 检查关键文件
        required_files = ['src/main.ts', 'src/App.vue', 'src/router/index.ts']
        for file_name in required_files:
            file_path = os.path.join(frontend_path, file_name)
            exists = os.path.exists(file_path)
            checks.append({
                'name': f'前端文件 {file_name}',
                'path': file_path,
                'passed': exists,
                'message': '存在' if exists else '缺失'
            })
        
        # 检查测试文件
        test_files = []
        if os.path.isdir(frontend_path):
            for root, dirs, files in os.walk(frontend_path):
                for file in files:
                    if file.endswith('.spec.ts'):
                        test_files.append(os.path.join(root, file))
        
        checks.append({
            'name': '前端单元测试',
            'path': frontend_path,
            'passed': len(test_files) > 0,
            'message': f'找到 {len(test_files)} 个测试文件'
        })
        
        all_passed = all(c['passed'] for c in checks)
        
        return {
            'gate': 'FRONTEND_DELIVERY',
            'passed': all_passed,
            'checks': checks,
            'test_count': len(test_files),
            'test_files': test_files
        }
    
    def check_backend_delivery(self, backend_path: str) -> Dict[str, Any]:
        """检查后端交付物"""
        checks = []
        
        # 检查目录结构
        required_dirs = ['src/main/java', 'src/main/resources', 'src/test/java']
        for dir_name in required_dirs:
            dir_path = os.path.join(backend_path, dir_name)
            exists = os.path.exists(dir_path)
            checks.append({
                'name': f'后端目录 {dir_name}',
                'path': dir_path,
                'passed': exists,
                'message': '存在' if exists else '缺失'
            })
        
        # 检查关键文件
        required_files = ['src/main/java/com/chinacreator/ai/nativex/factory/AiNativeFactoryApplication.java',
                         'src/main/resources/application.yml', 'pom.xml']
        for file_name in required_files:
            file_path = os.path.join(backend_path, file_name)
            exists = os.path.exists(file_path)
            checks.append({
                'name': f'后端文件 {file_name}',
                'path': file_path,
                'passed': exists,
                'message': '存在' if exists else '缺失'
            })
        
        # 检查测试文件
        test_files = []
        if os.path.isdir(backend_path):
            for root, dirs, files in os.walk(backend_path):
                for file in files:
                    if file.endswith('Test.java'):
                        test_files.append(os.path.join(root, file))
        
        checks.append({
            'name': '后端单元测试',
            'path': backend_path,
            'passed': len(test_files) > 0,
            'message': f'找到 {len(test_files)} 个测试文件'
        })
        
        all_passed = all(c['passed'] for c in checks)
        
        return {
            'gate': 'BACKEND_DELIVERY',
            'passed': all_passed,
            'checks': checks,
            'test_count': len(test_files),
            'test_files': test_files
        }
    
    def check_integration_tests(self, tests_path: str) -> Dict[str, Any]:
        """检查集成测试交付物"""
        checks = []
        
        # 检查测试目录
        required_dirs = ['frontend', 'backend', 'contract']
        for dir_name in required_dirs:
            dir_path = os.path.join(tests_path, dir_name)
            exists = os.path.exists(dir_path)
            checks.append({
                'name': f'测试目录 {dir_name}',
                'path': dir_path,
                'passed': exists,
                'message': '存在' if exists else '缺失'
            })
        
        # 检查测试文件数量
        frontend_tests = []
        backend_tests = []
        contract_tests = []
        
        if os.path.isdir(tests_path):
            for root, dirs, files in os.walk(tests_path):
                for file in files:
                    if 'frontend' in root and file.endswith('.spec.ts'):
                        frontend_tests.append(os.path.join(root, file))
                    elif 'backend' in root and file.endswith('Test.java'):
                        backend_tests.append(os.path.join(root, file))
                    elif 'contract' in root:
                        contract_tests.append(os.path.join(root, file))
        
        checks.append({
            'name': '前端集成测试',
            'path': os.path.join(tests_path, 'frontend'),
            'passed': len(frontend_tests) > 0,
            'message': f'找到 {len(frontend_tests)} 个测试文件'
        })
        
        checks.append({
            'name': '后端集成测试',
            'path': os.path.join(tests_path, 'backend'),
            'passed': len(backend_tests) > 0,
            'message': f'找到 {len(backend_tests)} 个测试文件'
        })
        
        checks.append({
            'name': 'API 契约测试',
            'path': os.path.join(tests_path, 'contract'),
            'passed': len(contract_tests) > 0,
            'message': f'找到 {len(contract_tests)} 个测试文件'
        })
        
        all_passed = all(c['passed'] for c in checks)
        
        return {
            'gate': 'INTEGRATION_TESTS',
            'passed': all_passed,
            'checks': checks,
            'frontend_test_count': len(frontend_tests),
            'backend_test_count': len(backend_tests),
            'contract_test_count': len(contract_tests),
            'total_test_count': len(frontend_tests) + len(backend_tests) + len(contract_tests)
        }
    
    def check_traceability(self, traceability_path: str) -> Dict[str, Any]:
        """检查追溯记录"""
        checks = []
        
        # 检查追溯记录目录
        exists = os.path.exists(traceability_path)
        checks.append({
            'name': '追溯记录目录',
            'path': traceability_path,
            'passed': exists,
            'message': '存在' if exists else '缺失'
        })
        
        # 检查追溯记录文件
        records = []
        if os.path.isdir(traceability_path):
            for file in os.listdir(traceability_path):
                if file.endswith('.json'):
                    file_path = os.path.join(traceability_path, file)
                    data = load_json_file(file_path)
                    records.append(data)
        
        checks.append({
            'name': '追溯记录文件',
            'path': traceability_path,
            'passed': len(records) > 0,
            'message': f'找到 {len(records)} 个追溯记录'
        })
        
        # 检查批准状态
        approved_records = [r for r in records if r.get('approved', False)]
        checks.append({
            'name': '追溯记录批准',
            'path': traceability_path,
            'passed': len(approved_records) == len(records),
            'message': f'{len(approved_records)}/{len(records)} 已批准'
        })
        
        all_passed = all(c['passed'] for c in checks)
        
        return {
            'gate': 'TRACEABILITY',
            'passed': all_passed,
            'checks': checks,
            'record_count': len(records),
            'approved_count': len(approved_records),
            'records': records
        }
    
    def run_all_gates(self, frontend_path: str, backend_path: str, 
                     tests_path: str, traceability_path: str) -> Dict[str, Any]:
        """运行所有交付门禁"""
        frontend_result = self.check_frontend_delivery(frontend_path)
        backend_result = self.check_backend_delivery(backend_path)
        tests_result = self.check_integration_tests(tests_path)
        traceability_result = self.check_traceability(traceability_path)
        
        all_passed = all([
            frontend_result['passed'],
            backend_result['passed'],
            tests_result['passed'],
            traceability_result['passed']
        ])
        
        result = {
            'timestamp': generate_timestamp(),
            'all_passed': all_passed,
            'gates': [
                frontend_result,
                backend_result,
                tests_result,
                traceability_result
            ],
            'summary': {
                'frontend_test_count': frontend_result.get('test_count', 0),
                'backend_test_count': backend_result.get('test_count', 0),
                'integration_test_count': tests_result.get('total_test_count', 0),
                'traceability_records': traceability_result.get('record_count', 0),
                'traceability_approved': traceability_result.get('approved_count', 0)
            },
            'deliverable_allowed': all_passed
        }
        
        # 保存交付报告
        report_path = os.path.join(self.output_dir, 'delivery-report.json')
        save_json_file(report_path, result)
        
        return {
            'result': result,
            'report_path': report_path
        }
    
    def generate_delivery_summary(self, result: Dict[str, Any]) -> str:
        """生成交付摘要报告（Markdown）"""
        gates = result.get('gates', [])
        summary = result.get('summary', {})
        
        markdown = f"""# 交付门禁报告

**生成时间**: {result.get('timestamp', '')}
**交付许可**: {'✅ 允许交付' if result.get('deliverable_allowed') else '❌ 禁止交付'}

---

## 门禁检查结果

| 门禁 | 状态 | 详情 |
|------|------|------|
"""
        
        for gate in gates:
            status = '✅ 通过' if gate['passed'] else '❌ 失败'
            details = '\n'.join(f"- {c['name']}: {c['message']}" for c in gate.get('checks', []))
            markdown += f"| {gate['gate']} | {status} | {details} |\n"
        
        markdown += """
---

## 交付物统计

| 类别 | 数量 |
|------|------|
| 前端单元测试 | {} |
| 后端单元测试 | {} |
| 集成测试总数 | {} |
| 追溯记录数 | {} |
| 已批准记录 | {} |

---

## 结论

{}

""".format(
    summary.get('frontend_test_count', 0),
    summary.get('backend_test_count', 0),
    summary.get('integration_test_count', 0),
    summary.get('traceability_records', 0),
    summary.get('traceability_approved', 0),
    '所有门禁通过，可分发 Reviewer/QA 审核' if result.get('deliverable_allowed') else '部分门禁未通过，请修复后重新验证'
)
        
        return markdown


def run_delivery_gates(frontend_path: str, backend_path: str, 
                      tests_path: str, traceability_path: str,
                      output_dir: str = './delivery') -> Dict[str, Any]:
    """运行交付门禁的入口函数"""
    gate = DeliveryGate(output_dir)
    result = gate.run_all_gates(frontend_path, backend_path, tests_path, traceability_path)
    
    # 生成 Markdown 报告
    markdown_report = gate.generate_delivery_summary(result['result'])
    report_path = os.path.join(output_dir, 'delivery-report.md')
    with open(report_path, 'w', encoding='utf-8') as f:
        f.write(markdown_report)
    
    return {
        'result': result['result'],
        'json_report_path': result['report_path'],
        'markdown_report_path': report_path
    }
