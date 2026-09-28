"""Fullstack End-to-End Skill 脚本模块

提供需求匹配、追溯记录、集成测试生成和交付门禁功能。
"""

from .utils import (
    calculate_hash,
    generate_timestamp,
    ensure_directory,
    load_json_file,
    save_json_file,
    extract_text_from_md,
    find_patterns_in_text,
    normalize_string,
    calculate_similarity,
    extract_function_ids,
    extract_page_names,
    get_file_hash,
    validate_file_exists
)

from .requirement_matcher import (
    RequirementMatcher,
    match_requirements
)

from .traceability_recorder import (
    TraceabilityRecorder,
    record_traceability
)

from .integration_test_generator import (
    IntegrationTestGenerator,
    generate_integration_tests
)

from .delivery_gate import (
    DeliveryGate,
    run_delivery_gates
)

__all__ = [
    # utils
    'calculate_hash',
    'generate_timestamp',
    'ensure_directory',
    'load_json_file',
    'save_json_file',
    'extract_text_from_md',
    'find_patterns_in_text',
    'normalize_string',
    'calculate_similarity',
    'extract_function_ids',
    'extract_page_names',
    'get_file_hash',
    'validate_file_exists',
    
    # requirement_matcher
    'RequirementMatcher',
    'match_requirements',
    
    # traceability_recorder
    'TraceabilityRecorder',
    'record_traceability',
    
    # integration_test_generator
    'IntegrationTestGenerator',
    'generate_integration_tests',
    
    # delivery_gate
    'DeliveryGate',
    'run_delivery_gates'
]
