import os
import re
import json
import hashlib
from datetime import datetime
from typing import Dict, List, Any, Optional


def calculate_hash(content: str) -> str:
    """计算内容的 SHA-256 哈希值"""
    return hashlib.sha256(content.encode('utf-8')).hexdigest()


def generate_timestamp() -> str:
    """生成 ISO 格式的时间戳"""
    return datetime.now().isoformat()


def ensure_directory(path: str) -> None:
    """确保目录存在"""
    os.makedirs(path, exist_ok=True)


def load_json_file(file_path: str) -> Dict[str, Any]:
    """加载 JSON 文件"""
    if not os.path.exists(file_path):
        return {}
    with open(file_path, 'r', encoding='utf-8') as f:
        return json.load(f)


def save_json_file(file_path: str, data: Dict[str, Any], indent: int = 2) -> None:
    """保存 JSON 文件"""
    ensure_directory(os.path.dirname(file_path))
    with open(file_path, 'w', encoding='utf-8') as f:
        json.dump(data, f, ensure_ascii=False, indent=indent)


def extract_text_from_md(md_content: str) -> str:
    """从 Markdown 中提取纯文本"""
    # 移除代码块
    md_content = re.sub(r'```[\s\S]*?```', '', md_content)
    # 移除行内代码
    md_content = re.sub(r'`[^`]+`', '', md_content)
    # 移除标题符号
    md_content = re.sub(r'^#+\s', '', md_content, flags=re.MULTILINE)
    # 移除链接
    md_content = re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', md_content)
    # 移除图片
    md_content = re.sub(r'!\[([^\]]*)\]\([^)]+\)', '', md_content)
    # 移除表格
    md_content = re.sub(r'\|.*\|', '', md_content)
    # 移除特殊字符
    md_content = re.sub(r'[*_~>=-]+', '', md_content)
    # 移除多余空白
    md_content = re.sub(r'\s+', ' ', md_content).strip()
    return md_content


def find_patterns_in_text(text: str, patterns: List[str]) -> List[str]:
    """在文本中查找匹配的模式"""
    found = []
    text_lower = text.lower()
    for pattern in patterns:
        pattern_lower = pattern.lower()
        if pattern_lower in text_lower:
            found.append(pattern)
    return found


def normalize_string(s: str) -> str:
    """标准化字符串用于比较"""
    s = s.strip().lower()
    s = re.sub(r'[^a-zA-Z0-9\u4e00-\u9fff]', '', s)
    return s


def calculate_similarity(str1: str, str2: str) -> float:
    """计算两个字符串的相似度（Jaccard）"""
    str1_norm = normalize_string(str1)
    str2_norm = normalize_string(str2)
    
    if not str1_norm or not str2_norm:
        return 0.0
    
    set1 = set(str1_norm)
    set2 = set(str2_norm)
    
    intersection = set1 & set2
    union = set1 | set2
    
    return len(intersection) / len(union) if union else 0.0


def extract_function_ids(text: str) -> List[str]:
    """从 PRD 文本中提取功能点 ID（如 F1、F2-xxx）"""
    pattern = r'F\d+[-_][a-zA-Z0-9\u4e00-\u9fff]+'
    return re.findall(pattern, text)


def extract_page_names(text: str) -> List[str]:
    """从文本中提取页面名称"""
    # 匹配常见的页面命名模式
    patterns = [
        r'页面[\u4e00-\u9fff]+',
        r'[\u4e00-\u9fff]+页面',
        r'列表页',
        r'详情页',
        r'表单页',
        r'首页',
        r'登录页',
        r'注册页',
    ]
    results = []
    for pattern in patterns:
        matches = re.findall(pattern, text)
        results.extend(matches)
    return list(set(results))


def get_file_hash(file_path: str) -> str:
    """获取文件的哈希值"""
    if not os.path.exists(file_path):
        return ''
    with open(file_path, 'rb') as f:
        return hashlib.sha256(f.read()).hexdigest()


def validate_file_exists(file_path: str, error_msg: str = None) -> bool:
    """验证文件是否存在"""
    if not os.path.exists(file_path):
        if error_msg:
            raise FileNotFoundError(error_msg)
        return False
    return True
