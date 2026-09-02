#!/usr/bin/env python3
"""
UI Design Optimizer - Analysis Report Validator
==============================================

验证分析报告中的证据是否准确，支持两种报告格式：
1. 简单表格格式（包含 Priority, Adjustment, Design Spec, Current, Design Source, Code Source, Code Snippet）
2. 详细对比表格格式（包含 变量名, 设计稿规格, style.css, tailwind.config.js, 状态）

验证内容：
1. 设计稿文件和行号存在且包含声明的颜色值
2. 前端代码文件和行号存在且包含声明的颜色值
3. 代码片段与实际代码匹配
4. 识别报告中的遗漏或错误

Usage:
    python validate_report.py <report_file> [--design-dir <path>] [--code-dir <path>]

Example:
    python validate_report.py "docs/01需求分析/01-政务窗口助手/01-UI设计/UI设计规格分析-20260724.md"
"""

import re
import os
import sys
import argparse
from typing import List, Dict, Tuple

# 颜色值正则表达式
COLOR_REGEX = re.compile(r'#([0-9A-Fa-f]{3,8})|rgba?\([^)]+\)|oklch\([^)]+\)')

class ReportValidator:
    def __init__(self, report_file: str, design_dir: str = None, code_dir: str = None):
        self.report_file = report_file
        self.design_dir = design_dir or self._infer_design_dir()
        self.code_dir = code_dir or 'frontend/src'
        self.errors: List[str] = []
        self.warnings: List[str] = []
        self.validations: List[Dict] = []
        self.design_colors: Dict[str, List[str]] = {}  # 设计稿组件中实际使用的颜色
        self.theme_colors: Dict[str, str] = {}  # 主题变量定义的颜色
    
    def _infer_design_dir(self) -> str:
        """从报告文件路径推断设计稿目录"""
        return os.path.dirname(self.report_file)
    
    def search_design_components(self) -> Dict[str, List[str]]:
        """搜索设计稿组件中所有硬编码的颜色值"""
        if not self.design_dir or not os.path.exists(self.design_dir):
            return {}
        
        color_usage: Dict[str, List[str]] = {}
        
        # 搜索设计稿目录下的所有组件文件
        for root, dirs, files in os.walk(self.design_dir):
            for filename in files:
                if filename.endswith(('.tsx', '.vue', '.css')):
                    filepath = os.path.join(root, filename)
                    try:
                        with open(filepath, 'r', encoding='utf-8') as f:
                            content = f.read()
                            colors = COLOR_REGEX.findall(content)
                            
                            for color in colors:
                                normalized_color = color.lower().replace(' ', '')
                                if normalized_color not in color_usage:
                                    color_usage[normalized_color] = []
                                rel_path = os.path.relpath(filepath, self.design_dir)
                                if rel_path not in color_usage[normalized_color]:
                                    color_usage[normalized_color].append(rel_path)
                    except Exception as e:
                        self.warnings.append(f"⚠️ 无法读取设计文件: {filepath} ({e})")
        
        return color_usage
    
    def extract_theme_colors(self) -> Dict[str, str]:
        """从设计稿主题文件中提取颜色变量定义"""
        theme_colors = {}
        
        # 查找主题文件
        theme_files = ['src/styles/theme.css', 'src/styles/globals.css', 'src/styles/index.css']
        
        for theme_file in theme_files:
            filepath = os.path.join(self.design_dir, theme_file)
            if os.path.exists(filepath):
                try:
                    with open(filepath, 'r', encoding='utf-8') as f:
                        content = f.read()
                        
                        # 匹配 CSS 变量定义
                        var_pattern = re.compile(r'--([a-zA-Z0-9-]+):\s*([^;]+)')
                        matches = var_pattern.findall(content)
                        
                        for var_name, value in matches:
                            # 提取颜色值
                            colors = COLOR_REGEX.findall(value)
                            if colors:
                                theme_colors[var_name] = colors[0].lower().replace(' ', '')
                except Exception as e:
                    self.warnings.append(f"⚠️ 无法读取主题文件: {filepath} ({e})")
        
        return theme_colors
    
    def detect_color_conflicts(self) -> List[Dict]:
        """检测主题变量与组件实际使用的颜色冲突"""
        conflicts = []
        
        self.design_colors = self.search_design_components()
        self.theme_colors = self.extract_theme_colors()
        
        if not self.design_colors:
            self.warnings.append("⚠️ 未在设计稿组件中找到硬编码颜色值")
            return conflicts
        
        if not self.theme_colors:
            self.warnings.append("⚠️ 未从主题文件中提取到颜色变量")
            return conflicts
        
        # 分析主色冲突
        primary_theme = self.theme_colors.get('primary', '')
        if primary_theme:
            # 找到组件中使用最多的蓝色系颜色（通常是主色）
            blue_colors = [c for c in self.design_colors if c.startswith('#3370') or c.startswith('#4e83') or c.startswith('#3081')]
            
            for blue_color in blue_colors:
                if blue_color != primary_theme:
                    conflicts.append({
                        'type': 'primary_color_conflict',
                        'component_color': blue_color,
                        'theme_color': primary_theme,
                        'files': self.design_colors[blue_color],
                        'suggestion': f'使用组件颜色 {blue_color} 作为主色'
                    })
        
        # 分析边框色冲突
        border_theme = self.theme_colors.get('border', '')
        if border_theme:
            # 查找组件中使用的边框色
            border_colors = [c for c in self.design_colors if 'rgba(51' in c or 'rgba(48' in c]
            for bc in border_colors:
                if bc != border_theme:
                    conflicts.append({
                        'type': 'border_color_conflict',
                        'component_color': bc,
                        'theme_color': border_theme,
                        'files': self.design_colors[bc],
                        'suggestion': f'使用组件边框色 {bc}'
                    })
        
        return conflicts
    
    def parse_report(self) -> List[Dict]:
        """解析分析报告中的表格数据，支持多种格式"""
        results = []
        
        with open(self.report_file, 'r', encoding='utf-8') as f:
            content = f.read()
        
        lines = content.split('\n')
        
        # 检测报告格式
        if '| Priority |' in content and '| Adjustment |' in content:
            # 格式1: 简单表格格式
            results = self._parse_simple_format(lines)
        elif '| 变量名 |' in content and '| 设计稿规格 |' in content:
            # 格式2: 详细对比表格格式
            results = self._parse_detailed_format(lines)
        
        return results
    
    def _parse_simple_format(self, lines: List[str]) -> List[Dict]:
        """解析简单表格格式（包含 Priority, Adjustment, Design Spec 等）"""
        results = []
        in_table = False
        headers = []
        
        for line in lines:
            if '| Priority |' in line and '| Adjustment |' in line:
                in_table = True
                headers = [h.strip() for h in line.split('|') if h.strip()]
                continue
            
            if in_table and line.startswith('|'):
                if '|---' in line or '|----------' in line:
                    continue
                
                parts = [p.strip() for p in line.split('|') if p.strip()]
                if len(parts) >= 5:
                    row = dict(zip(headers, parts))
                    results.append(row)
        
        return results
    
    def _parse_detailed_format(self, lines: List[str]) -> List[Dict]:
        """解析详细对比表格格式（包含 变量名, 设计稿规格, style.css 等）"""
        results = []
        in_table = False
        headers = []
        
        for line in lines:
            if '| 变量名 |' in line and '| 设计稿规格 |' in line:
                in_table = True
                headers = [h.strip() for h in line.split('|') if h.strip()]
                continue
            
            if in_table and line.startswith('|'):
                if '|---' in line or '|----------' in line:
                    continue
                
                # 检查是否是表格结束（空行或新标题）
                if line.strip() == '|' or (line.startswith('##') and '|' not in line):
                    in_table = False
                    headers = []
                    continue
                
                parts = [p.strip() for p in line.split('|') if p.strip()]
                if len(parts) >= 3:
                    row = dict(zip(headers, parts))
                    # 转换为统一格式
                    unified_row = self._convert_to_unified_format(row)
                    if unified_row:
                        results.append(unified_row)
        
        return results
    
    def _convert_to_unified_format(self, row: Dict) -> Dict:
        """将详细格式转换为统一格式"""
        var_name = row.get('变量名', '')
        design_spec = row.get('设计稿规格', '')
        style_css = row.get('style.css', '')
        status = row.get('状态', '')
        
        # 从设计稿规格中提取颜色值
        design_color = ''
        if design_spec:
            colors = COLOR_REGEX.findall(design_spec)
            design_color = colors[0] if colors else ''
        
        # 从 style.css 中提取颜色值
        current_color = ''
        if style_css and style_css != '❌ 未定义':
            colors = COLOR_REGEX.findall(style_css)
            current_color = colors[0] if colors else ''
        
        # 判断优先级
        priority = 'P2'
        if status == '⚠️ 缺失':
            priority = 'P1'
        elif status == '❌' or '不一致' in status:
            priority = 'P0'
        elif status == '✅ 一致':
            priority = 'P2'
        
        return {
            'Priority': priority,
            'Adjustment': var_name,
            'Design Spec': design_color,
            'Current': current_color,
            'Difference': status,
            'Design Source': self._infer_design_source(var_name),
            'Code Source': self._infer_code_source(var_name),
            'Code Snippet': style_css,
            'OriginalStatus': status,
            'OriginalRow': row
        }
    
    def _infer_design_source(self, var_name: str) -> str:
        """推断设计稿来源（基于变量名）"""
        return f'src/styles/theme.css#L?'
    
    def _infer_code_source(self, var_name: str) -> str:
        """推断代码来源（基于变量名）"""
        return f'style.css#L?'
    
    def parse_source_ref(self, source_ref: str) -> Tuple[str, int]:
        """解析文件引用格式：filename.css#L11 -> (filename.css, 11)"""
        if '#' in source_ref:
            filename, line_str = source_ref.split('#L', 1)
            try:
                line_num = int(line_str)
                return filename, line_num
            except ValueError:
                return filename, None
        return source_ref, None
    
    def validate_file_exists(self, file_path: str, ref_type: str) -> bool:
        """验证文件是否存在"""
        if not os.path.exists(file_path):
            self.errors.append(f"❌ {ref_type}文件不存在: {file_path}")
            return False
        return True
    
    def validate_line_exists(self, file_path: str, line_num: int, ref_type: str) -> bool:
        """验证行号是否存在"""
        with open(file_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        if line_num is None:
            self.warnings.append(f"⚠️ {ref_type}未指定行号: {file_path}")
            return True
        
        if line_num < 1 or line_num > len(lines):
            self.errors.append(f"❌ {ref_type}行号超出范围 ({line_num}/{len(lines)}): {file_path}")
            return False
        
        return True
    
    def validate_color_in_line(self, file_path: str, line_num: int, expected_color: str, ref_type: str) -> bool:
        """验证指定行是否包含预期颜色值"""
        with open(file_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        if line_num is None:
            return True
        
        if line_num < 1 or line_num > len(lines):
            return False
        
        line_content = lines[line_num - 1]
        colors_in_line = COLOR_REGEX.findall(line_content)
        
        # 标准化颜色值进行比较
        normalized_expected = expected_color.lower().replace(' ', '')
        
        found = False
        for color in colors_in_line:
            normalized_color = color.lower().replace(' ', '')
            if normalized_color == normalized_expected:
                found = True
                break
        
        if not found:
            self.errors.append(f"❌ {ref_type}行{line_num}未找到颜色值 '{expected_color}'")
            self.errors.append(f"   实际内容: {line_content.strip()[:80]}")
            return False
        
        return True
    
    def validate_snippet(self, file_path: str, line_num: int, snippet: str) -> bool:
        """验证代码片段是否与实际代码匹配"""
        if not snippet or snippet.startswith('`'):
            snippet = snippet.strip('`')
        
        if not snippet:
            self.warnings.append(f"⚠️ 代码片段为空")
            return True
        
        with open(file_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        if line_num and (line_num < 1 or line_num > len(lines)):
            return False
        
        # 在附近几行搜索代码片段
        search_range = range(max(1, line_num - 2) if line_num else 0, 
                            min(len(lines), line_num + 2) if line_num else len(lines))
        
        found = False
        for i in search_range:
            if snippet in lines[i]:
                found = True
                break
        
        if not found:
            self.errors.append(f"❌ 代码片段 '{snippet}' 未在 {file_path} 附近找到")
            if line_num:
                context = '\n'.join(lines[max(0, line_num-3):min(len(lines), line_num+2)])
                self.errors.append(f"   上下文: {context.strip()[:150]}")
            return False
        
        return True
    
    def validate_color_in_codebase(self, expected_color: str, var_name: str) -> bool:
        """验证颜色值是否存在于代码库中（详细格式专用）"""
        if not expected_color:
            return True
        
        # 搜索 style.css
        style_css_path = os.path.join(self.code_dir, 'style.css')
        if os.path.exists(style_css_path):
            with open(style_css_path, 'r', encoding='utf-8') as f:
                content = f.read()
                colors_found = COLOR_REGEX.findall(content)
                normalized_expected = expected_color.lower().replace(' ', '')
                found = any(c.lower().replace(' ', '') == normalized_expected for c in colors_found)
                
                if not found:
                    self.errors.append(f"❌ 颜色值 '{expected_color}' 未在 {style_css_path} 中找到")
                    return False
        
        return True
    
    def validate_design_source(self, row: Dict) -> bool:
        """验证设计稿来源"""
        design_source = row.get('Design Source', '')
        if not design_source:
            self.warnings.append(f"⚠️ 设计稿来源为空: {row.get('Adjustment', 'N/A')}")
            return True
        
        filename, line_num = self.parse_source_ref(design_source)
        file_path = os.path.join(self.design_dir, filename)
        
        if not self.validate_file_exists(file_path, '设计稿'):
            return False
        
        if not self.validate_line_exists(file_path, line_num, '设计稿'):
            return False
        
        expected_color = row.get('Design Spec', '')
        if expected_color and line_num:
            return self.validate_color_in_line(file_path, line_num, expected_color, '设计稿')
        
        return True
    
    def validate_code_source(self, row: Dict) -> bool:
        """验证代码来源"""
        code_source = row.get('Code Source', '')
        if not code_source:
            self.warnings.append(f"⚠️ 代码来源为空: {row.get('Adjustment', 'N/A')}")
            return True
        
        filename, line_num = self.parse_source_ref(code_source)
        file_path = os.path.join(self.code_dir, filename)
        
        if not self.validate_file_exists(file_path, '代码'):
            return False
        
        if not self.validate_line_exists(file_path, line_num, '代码'):
            return False
        
        expected_color = row.get('Current', '')
        if expected_color and line_num:
            return self.validate_color_in_line(file_path, line_num, expected_color, '代码')
        
        return True
    
    def validate_code_snippet(self, row: Dict) -> bool:
        """验证代码片段"""
        snippet = row.get('Code Snippet', '')
        if not snippet or snippet == '❌ 未定义':
            return True
        
        code_source = row.get('Code Source', '')
        if not code_source:
            return True
        
        filename, line_num = self.parse_source_ref(code_source)
        file_path = os.path.join(self.code_dir, filename)
        
        if os.path.exists(file_path):
            return self.validate_snippet(file_path, line_num, snippet)
        
        return True
    
    def validate_row(self, row: Dict) -> Dict:
        """验证单行数据"""
        result = {
            'adjustment': row.get('Adjustment', row.get('变量名', 'N/A')),
            'priority': row.get('Priority', 'N/A'),
            'valid': True,
            'checks': []
        }
        
        checks = []
        original_status = row.get('OriginalStatus', '')
        
        # 如果状态是 ✅ 一致，跳过详细验证
        if original_status == '✅ 一致':
            checks.append({'name': '状态', 'status': '✅', 'detail': '已标记为一致'})
            result['checks'] = checks
            result['valid'] = True
            self.validations.append(result)
            return result
        
        # 验证设计稿来源（如果有）
        design_source = row.get('Design Source', '')
        if design_source and design_source != 'src/styles/theme.css#L?':
            design_ok = self.validate_design_source(row)
            checks.append({'name': '设计稿来源', 'status': '✅' if design_ok else '❌'})
            if not design_ok:
                result['valid'] = False
        else:
            checks.append({'name': '设计稿来源', 'status': '⚠️', 'detail': '自动推断'})
        
        # 验证代码来源（如果有）
        code_source = row.get('Code Source', '')
        if code_source and code_source != 'style.css#L?':
            code_ok = self.validate_code_source(row)
            checks.append({'name': '代码来源', 'status': '✅' if code_ok else '❌'})
            if not code_ok:
                result['valid'] = False
        else:
            checks.append({'name': '代码来源', 'status': '⚠️', 'detail': '自动推断'})
        
        # 验证代码片段
        snippet_ok = self.validate_code_snippet(row)
        checks.append({'name': '代码片段', 'status': '✅' if snippet_ok else '❌'})
        if not snippet_ok:
            result['valid'] = False
        
        # 验证颜色值在代码库中存在
        design_spec = row.get('Design Spec', '')
        current = row.get('Current', '')
        
        if design_spec:
            color_ok = self.validate_color_in_codebase(design_spec, row.get('Adjustment', ''))
            checks.append({'name': '颜色值验证', 'status': '✅' if color_ok else '❌'})
            if not color_ok:
                result['valid'] = False
        
        result['checks'] = checks
        self.validations.append(result)
        
        return result
    
    def run(self) -> Tuple[int, int, int]:
        """执行完整验证"""
        print(f"📄 分析报告: {self.report_file}")
        print(f"🎨 设计稿目录: {self.design_dir}")
        print(f"💻 代码目录: {self.code_dir}")
        print("-" * 60)
        
        # 执行颜色冲突检测（CRITICAL）
        print("🔍 执行设计稿颜色冲突检测...")
        conflicts = self.detect_color_conflicts()
        
        if conflicts:
            print("\n🚨 检测到颜色冲突:")
            for i, conflict in enumerate(conflicts, 1):
                print(f"\n   {i}. [{conflict['type'].replace('_', ' ')}]")
                print(f"      组件使用颜色: {conflict['component_color']}")
                print(f"      主题定义颜色: {conflict['theme_color']}")
                print(f"      涉及文件: {', '.join(conflict['files'])}")
                print(f"      建议: {conflict['suggestion']}")
                # 将冲突添加为错误
                self.errors.append(f"🚨 颜色冲突: 组件使用 {conflict['component_color']}，但主题定义为 {conflict['theme_color']}")
        else:
            print("✅ 未检测到颜色冲突")
        
        print("\n" + "-" * 60)
        
        rows = self.parse_report()
        
        if not rows:
            print("❌ 未找到分析报告表格")
            return 0, 0, 0
        
        print(f"📊 共找到 {len(rows)} 条分析记录")
        print()
        
        for row in rows:
            result = self.validate_row(row)
            status = "✅" if result['valid'] else "❌"
            print(f"{status} [{result['priority']}] {result['adjustment']}")
            for check in result['checks']:
                detail = f" ({check['detail']})" if 'detail' in check else ""
                print(f"   {check['status']} {check['name']}{detail}")
        
        print("-" * 60)
        
        # 输出错误和警告
        if self.errors:
            print("\n❌ 错误列表:")
            for i, error in enumerate(self.errors, 1):
                print(f"   {i}. {error}")
        
        if self.warnings:
            print("\n⚠️ 警告列表:")
            for i, warning in enumerate(self.warnings, 1):
                print(f"   {i}. {warning}")
        
        # 统计结果
        total = len(rows)
        valid = sum(1 for r in self.validations if r['valid'])
        invalid = total - valid
        
        print("\n" + "=" * 60)
        print(f"📈 验证结果: {valid}/{total} 条记录通过")
        
        if invalid > 0:
            print(f"❌ {invalid} 条记录存在问题，请检查错误列表")
        else:
            print("✅ 所有记录验证通过！")
        
        return total, valid, invalid

def main():
    parser = argparse.ArgumentParser(
        description="验证UI设计规格分析报告的准确性",
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument('report_file', help='分析报告文件路径')
    parser.add_argument('--design-dir', help='设计稿目录路径')
    parser.add_argument('--code-dir', default='frontend/src', help='前端代码目录路径')
    
    args = parser.parse_args()
    
    validator = ReportValidator(
        report_file=args.report_file,
        design_dir=args.design_dir,
        code_dir=args.code_dir
    )
    
    total, valid, invalid = validator.run()
    
    sys.exit(0 if invalid == 0 else 1)

if __name__ == '__main__':
    main()
