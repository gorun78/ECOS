import os
import re
from typing import Dict, List, Any, Tuple, Optional
from utils import (
    extract_text_from_md,
    calculate_similarity,
    extract_function_ids,
    extract_page_names,
    find_patterns_in_text,
    load_json_file,
    save_json_file,
    generate_timestamp,
    calculate_hash
)


class RequirementMatcher:
    """需求匹配器：从需求文档中找到对应的描述和原型系统中的页面"""
    
    def __init__(self, prd_path: str, prototype_path: str):
        self.prd_path = prd_path
        self.prototype_path = prototype_path
        self.prd_content = ""
        self.prototype_pages = []
        self.matches = []
    
    def load_prd(self) -> str:
        """加载 PRD 文档内容"""
        if not os.path.exists(self.prd_path):
            raise FileNotFoundError(f"PRD 文档不存在: {self.prd_path}")
        
        with open(self.prd_path, 'r', encoding='utf-8') as f:
            self.prd_content = f.read()
        
        return self.prd_content
    
    def load_prototype_pages(self) -> List[Dict[str, Any]]:
        """加载原型系统中的页面列表"""
        pages = []
        
        if os.path.isdir(self.prototype_path):
            for root, dirs, files in os.walk(self.prototype_path):
                for file in files:
                    if file.endswith(('.md', '.json', '.html')):
                        file_path = os.path.join(root, file)
                        relative_path = os.path.relpath(file_path, self.prototype_path)
                        pages.append({
                            'name': os.path.splitext(file)[0],
                            'path': file_path,
                            'relative_path': relative_path,
                            'type': file.split('.')[-1]
                        })
        elif os.path.isfile(self.prototype_path):
            # 如果是 JSON 文件，解析页面列表
            if self.prototype_path.endswith('.json'):
                data = load_json_file(self.prototype_path)
                if isinstance(data, dict) and 'pages' in data:
                    pages = data['pages']
                elif isinstance(data, list):
                    pages = data
        
        self.prototype_pages = pages
        return pages
    
    def extract_requirements(self) -> List[Dict[str, Any]]:
        """从 PRD 中提取需求条目"""
        requirements = []
        text = extract_text_from_md(self.prd_content)
        
        # 提取功能点 ID
        func_ids = extract_function_ids(self.prd_content)
        
        # 提取页面名称
        page_names = extract_page_names(text)
        
        # 按章节分割需求
        chapters = re.split(r'(#{1,3}\s+[^\n]+)', self.prd_content)
        for i in range(0, len(chapters), 2):
            title = chapters[i].strip() if i < len(chapters) else ""
            content = chapters[i+1].strip() if i+1 < len(chapters) else ""
            
            if title:
                requirements.append({
                    'id': f"REQ_{len(requirements)+1:03d}",
                    'title': title.strip('# '),
                    'content': content,
                    'function_ids': find_patterns_in_text(title + content, func_ids),
                    'page_names': find_patterns_in_text(title + content, page_names),
                    'hash': calculate_hash(title + content)
                })
        
        return requirements
    
    def match_requirements_to_pages(self, requirements: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """将需求条目与原型页面进行匹配"""
        matches = []
        
        for req in requirements:
            req_text = req['title'] + ' ' + req['content']
            matched_pages = []
            
            for page in self.prototype_pages:
                # 读取页面内容
                page_content = ""
                if os.path.exists(page['path']):
                    with open(page['path'], 'r', encoding='utf-8') as f:
                        page_content = f.read()
                
                page_text = extract_text_from_md(page_content)
                page_name = page.get('name', '') + ' ' + page.get('title', '')
                
                # 计算相似度
                similarity_title = calculate_similarity(req['title'], page_name)
                similarity_content = calculate_similarity(req_text, page_text)
                
                # 综合相似度
                combined_similarity = (similarity_title * 0.6) + (similarity_content * 0.4)
                
                if combined_similarity > 0.3:
                    matched_pages.append({
                        'page_name': page_name,
                        'page_path': page['path'],
                        'relative_path': page.get('relative_path', ''),
                        'similarity': round(combined_similarity, 4),
                        'similarity_title': round(similarity_title, 4),
                        'similarity_content': round(similarity_content, 4),
                        'matched_keywords': find_patterns_in_text(page_text, req['page_names'])
                    })
            
            # 按相似度排序
            matched_pages.sort(key=lambda x: x['similarity'], reverse=True)
            
            matches.append({
                'requirement': req,
                'matched_pages': matched_pages[:5],  # 最多保留前 5 个匹配
                'match_count': len(matched_pages),
                'best_match': matched_pages[0] if matched_pages else None,
                'timestamp': generate_timestamp()
            })
        
        self.matches = matches
        return matches
    
    def find_orphan_requirements(self) -> List[Dict[str, Any]]:
        """找出未匹配到任何页面的需求"""
        return [m for m in self.matches if m['match_count'] == 0]
    
    def find_orphan_pages(self) -> List[Dict[str, Any]]:
        """找出未被任何需求匹配的页面"""
        matched_page_paths = set()
        for match in self.matches:
            for page in match['matched_pages']:
                matched_page_paths.add(page['page_path'])
        
        return [p for p in self.prototype_pages if p['path'] not in matched_page_paths]
    
    def run(self) -> Dict[str, Any]:
        """执行完整的需求匹配流程"""
        self.load_prd()
        self.load_prototype_pages()
        requirements = self.extract_requirements()
        matches = self.match_requirements_to_pages(requirements)
        
        orphan_reqs = self.find_orphan_requirements()
        orphan_pages = self.find_orphan_pages()
        
        return {
            'prd_path': self.prd_path,
            'prototype_path': self.prototype_path,
            'requirement_count': len(requirements),
            'page_count': len(self.prototype_pages),
            'match_count': len([m for m in matches if m['match_count'] > 0]),
            'orphan_requirements': len(orphan_reqs),
            'orphan_pages': len(orphan_pages),
            'matches': matches,
            'orphan_requirements_detail': orphan_reqs,
            'orphan_pages_detail': orphan_pages,
            'timestamp': generate_timestamp()
        }


def match_requirements(prd_path: str, prototype_path: str, output_path: str = None) -> Dict[str, Any]:
    """匹配需求与原型页面的入口函数"""
    matcher = RequirementMatcher(prd_path, prototype_path)
    result = matcher.run()
    
    if output_path:
        save_json_file(output_path, result)
    
    return result
