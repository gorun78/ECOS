import os
from typing import Dict, List, Any
from utils import (
    save_json_file,
    load_json_file,
    generate_timestamp,
    calculate_hash,
    get_file_hash,
    ensure_directory
)


class TraceabilityRecorder:
    """追溯记录器：保存需求与页面的匹配记录"""
    
    def __init__(self, output_dir: str = './traceability'):
        self.output_dir = output_dir
        ensure_directory(output_dir)
    
    def create_traceability_record(self, match_result: Dict[str, Any]) -> Dict[str, Any]:
        """创建追溯记录"""
        record = {
            'id': f"TRC_{generate_timestamp().replace(':', '').replace('-', '').replace('.', '')}",
            'version': '1.0.0',
            'timestamp': generate_timestamp(),
            'prd': {
                'path': match_result.get('prd_path', ''),
                'hash': get_file_hash(match_result.get('prd_path', '')),
                'requirement_count': match_result.get('requirement_count', 0)
            },
            'prototype': {
                'path': match_result.get('prototype_path', ''),
                'page_count': match_result.get('page_count', 0)
            },
            'match_summary': {
                'total_matched': match_result.get('match_count', 0),
                'orphan_requirements': match_result.get('orphan_requirements', 0),
                'orphan_pages': match_result.get('orphan_pages', 0),
                'match_rate': round(
                    match_result.get('match_count', 0) / match_result.get('requirement_count', 1) * 100,
                    2
                )
            },
            'traceability_items': [],
            'status': 'DRAFT',
            'reviewer': None,
            'review_date': None,
            'approved': False
        }
        
        # 构建追溯条目
        for match in match_result.get('matches', []):
            req = match['requirement']
            items = []
            
            for page in match['matched_pages']:
                items.append({
                    'requirement_id': req['id'],
                    'requirement_title': req['title'],
                    'requirement_hash': req['hash'],
                    'page_name': page['page_name'],
                    'page_path': page['page_path'],
                    'relative_path': page['relative_path'],
                    'similarity': page['similarity'],
                    'confidence': self._calculate_confidence(page['similarity']),
                    'matched_keywords': page.get('matched_keywords', []),
                    'status': 'MATCHED'
                })
            
            # 如果没有匹配的页面
            if not match['matched_pages']:
                items.append({
                    'requirement_id': req['id'],
                    'requirement_title': req['title'],
                    'requirement_hash': req['hash'],
                    'page_name': None,
                    'page_path': None,
                    'relative_path': None,
                    'similarity': 0,
                    'confidence': 'LOW',
                    'matched_keywords': [],
                    'status': 'UNMATCHED'
                })
            
            record['traceability_items'].extend(items)
        
        return record
    
    def _calculate_confidence(self, similarity: float) -> str:
        """根据相似度计算置信度"""
        if similarity >= 0.7:
            return 'HIGH'
        elif similarity >= 0.5:
            return 'MEDIUM'
        elif similarity >= 0.3:
            return 'LOW'
        else:
            return 'NONE'
    
    def save_record(self, record: Dict[str, Any], filename: str = None) -> str:
        """保存追溯记录"""
        if not filename:
            filename = f"{record['id']}.json"
        
        file_path = os.path.join(self.output_dir, filename)
        save_json_file(file_path, record)
        return file_path
    
    def load_record(self, record_id: str) -> Dict[str, Any]:
        """加载追溯记录"""
        file_path = os.path.join(self.output_dir, f"{record_id}.json")
        return load_json_file(file_path)
    
    def list_records(self) -> List[Dict[str, Any]]:
        """列出所有追溯记录"""
        records = []
        if os.path.isdir(self.output_dir):
            for file in os.listdir(self.output_dir):
                if file.endswith('.json'):
                    file_path = os.path.join(self.output_dir, file)
                    data = load_json_file(file_path)
                    records.append(data)
        return records
    
    def approve_record(self, record_id: str, reviewer: str) -> Dict[str, Any]:
        """批准追溯记录"""
        record = self.load_record(record_id)
        if record:
            record['status'] = 'APPROVED'
            record['reviewer'] = reviewer
            record['review_date'] = generate_timestamp()
            record['approved'] = True
            self.save_record(record)
        return record
    
    def reject_record(self, record_id: str, reviewer: str, reason: str) -> Dict[str, Any]:
        """拒绝追溯记录"""
        record = self.load_record(record_id)
        if record:
            record['status'] = 'REJECTED'
            record['reviewer'] = reviewer
            record['review_date'] = generate_timestamp()
            record['approved'] = False
            record['rejection_reason'] = reason
            self.save_record(record)
        return record
    
    def generate_traceability_matrix(self, record: Dict[str, Any]) -> List[Dict[str, Any]]:
        """生成追溯矩阵"""
        matrix = []
        
        for item in record.get('traceability_items', []):
            matrix.append({
                'requirement_id': item['requirement_id'],
                'requirement_title': item['requirement_title'],
                'page_name': item['page_name'],
                'page_path': item['page_path'],
                'confidence': item['confidence'],
                'status': item['status'],
                'similarity': item['similarity']
            })
        
        return matrix
    
    def generate_summary_report(self, record: Dict[str, Any]) -> Dict[str, Any]:
        """生成摘要报告"""
        items = record.get('traceability_items', [])
        
        matched_items = [i for i in items if i['status'] == 'MATCHED']
        unmatched_items = [i for i in items if i['status'] == 'UNMATCHED']
        
        confidence_stats = {}
        for item in matched_items:
            conf = item['confidence']
            confidence_stats[conf] = confidence_stats.get(conf, 0) + 1
        
        return {
            'record_id': record['id'],
            'version': record['version'],
            'timestamp': record['timestamp'],
            'status': record['status'],
            'approved': record['approved'],
            'reviewer': record.get('reviewer'),
            'prd_path': record['prd']['path'],
            'prototype_path': record['prototype']['path'],
            'summary': {
                'total_requirements': record['prd']['requirement_count'],
                'total_pages': record['prototype']['page_count'],
                'matched_requirements': len(set(i['requirement_id'] for i in matched_items)),
                'unmatched_requirements': len(set(i['requirement_id'] for i in unmatched_items)),
                'match_rate': record['match_summary']['match_rate'],
                'confidence_distribution': confidence_stats
            },
            'unmatched_requirements': [{
                'id': i['requirement_id'],
                'title': i['requirement_title']
            } for i in unmatched_items]
        }


def record_traceability(match_result: Dict[str, Any], output_dir: str = './traceability') -> Dict[str, Any]:
    """记录追溯信息的入口函数"""
    recorder = TraceabilityRecorder(output_dir)
    record = recorder.create_traceability_record(match_result)
    file_path = recorder.save_record(record)
    
    return {
        'record': record,
        'file_path': file_path,
        'summary': recorder.generate_summary_report(record)
    }
