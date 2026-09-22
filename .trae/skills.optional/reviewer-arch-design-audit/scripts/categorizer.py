"""
问题分类器模块

负责对校验出的问题进行级别判定和统计
"""


def categorize_issues(all_issues):
    """
    对问题进行分类和评级统计
    
    Args:
        all_issues (list): 所有校验器产生的问题列表
        
    Returns:
        dict: 分类后的问题和统计摘要
    """
    blocker = [i for i in all_issues if i.get("severity") == "BLOCKER"]
    warning = [i for i in all_issues if i.get("severity") == "WARNING"]
    suggestion = [i for i in all_issues if i.get("severity") == "SUGGESTION"]
    
    return {
        "blocker": blocker,
        "warning": warning,
        "suggestion": suggestion,
        "summary": {
            "total": len(all_issues),
            "blocker": len(blocker),
            "warning": len(warning),
            "suggestion": len(suggestion)
        }
    }
