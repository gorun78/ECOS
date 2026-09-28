"""
架构设计审计脚本包

提供审计流程的核心执行逻辑
"""

from .parsers import parse_inputs
from .validators import (
    validate_requirement_coverage,
    validate_database_health,
    validate_api_contract,
    validate_security_and_performance,
    run_all_validators
)
from .categorizer import categorize_issues
from .decision import make_decision
from .reporter import generate_audit_report, generate_approval_record

__all__ = [
    "parse_inputs",
    "validate_requirement_coverage",
    "validate_database_health",
    "validate_api_contract",
    "validate_security_and_performance",
    "run_all_validators",
    "categorize_issues",
    "make_decision",
    "generate_audit_report",
    "generate_approval_record"
]
