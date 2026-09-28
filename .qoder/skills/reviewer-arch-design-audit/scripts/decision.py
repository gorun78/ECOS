"""
综合决策器模块

根据问题统计做出审计判定
"""


def make_decision(categorized_issues):
    """
    根据问题统计做出综合判定
    
    Args:
        categorized_issues (dict): 分类后的问题和统计摘要
        
    Returns:
        dict: 判定结果
    """
    summary = categorized_issues["summary"]
    
    if summary["blocker"] >= 1:
        return {
            "decision": "REJECT",
            "reason": f"存在 {summary['blocker']} 个 BLOCKER 级问题，需打回重设计",
            "next_step": "通知 Arch 修复所有 BLOCKER 问题后重新提交"
        }
    elif summary["warning"] >= 1:
        return {
            "decision": "PASS_WITH_CONDITIONS",
            "reason": f"无 BLOCKER 问题，但存在 {summary['warning']} 个 WARNING 级问题",
            "next_step": "带条件通过，需在开发前修正 WARNING 问题"
        }
    else:
        return {
            "decision": "APPROVE",
            "reason": "仅有少量 SUGGESTION 或无问题",
            "next_step": "直接通过，进入开发阶段"
        }
