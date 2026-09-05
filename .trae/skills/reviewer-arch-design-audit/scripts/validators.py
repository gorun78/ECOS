"""
规则校验器模块

包含需求覆盖校验器、数据库合规校验器、API契约校验器和安全性能校验器
"""

from .utils import to_snake_case, is_snake_case, is_reserved_word, calculate_dependency_depth


def validate_requirement_coverage(parsed_inputs):
    """
    校验需求闭环规则：架构设计必须完全覆盖 PRD 中的核心业务流
    
    Args:
        parsed_inputs (dict): 结构化的输入数据
        
    Returns:
        list: 问题列表
    """
    issues = []
    prd = parsed_inputs["prd"]
    arch = parsed_inputs["arch"]
    api = parsed_inputs["api"]
    db = parsed_inputs["db"]
    
    # 检查业务实体是否有对应的数据库表
    for entity in prd["entities"]:
        entity_name = entity.get("name", "")
        table_exists = any(
            table["name"] == entity_name or table["name"] == to_snake_case(entity_name)
            for table in db["tables"]
        )
        if not table_exists:
            issues.append({
                "id": f"RC-{len(issues)+1:03d}",
                "type": "requirement_closure",
                "severity": "BLOCKER",
                "entity": entity_name,
                "message": f"PRD 定义了业务实体 '{entity_name}'，但数据库中没有对应的表",
                "rule": "规则 1：需求闭环规则",
                "suggestion": f"为 '{entity_name}' 创建数据库表"
            })
    
    # 检查业务流程是否有对应的 API
    for flow in prd["flows"]:
        flow_name = flow.get("name", "")
        flow_actions = flow.get("actions", [])
        for action in flow_actions:
            action_name = action.get("name", "")
            api_operation = action.get("api_operation", "")
            api_exists = any(
                endpoint.get("operationId") == api_operation
                for endpoint in api["endpoints"]
            )
            if not api_exists:
                issues.append({
                    "id": f"RC-{len(issues)+1:03d}",
                    "type": "requirement_closure",
                    "severity": "BLOCKER",
                    "flow": flow_name,
                    "action": action_name,
                    "message": f"业务流程 '{flow_name}' 的动作 '{action_name}' 没有对应的 API",
                    "rule": "规则 1：需求闭环规则",
                    "suggestion": f"为 '{action_name}' 创建 API 接口"
                })
    
    # 检查冗余设计：无需求却设计了复杂模块
    for module in arch["modules"]:
        module_name = module.get("name", "")
        module_used = any(
            flow.get("module") == module_name
            for flow in prd["flows"]
        )
        if not module_used:
            issues.append({
                "id": f"RC-{len(issues)+1:03d}",
                "type": "redundant_design",
                "severity": "SUGGESTION",
                "module": module_name,
                "message": f"模块 '{module_name}' 未被任何业务流程引用，可能是冗余设计",
                "rule": "规则 1：需求闭环规则",
                "suggestion": "确认是否需要该模块，如不需要建议移除"
            })
    
    return issues


def validate_database_health(parsed_inputs):
    """
    校验数据库设计健康红线：命名规范、主键与索引、数据完整性
    
    Args:
        parsed_inputs (dict): 结构化的输入数据
        
    Returns:
        list: 问题列表
    """
    issues = []
    db = parsed_inputs["db"]
    
    # 识别核心业务表
    core_tables = {"order", "orders", "user", "users", "payment", "payments",
                   "transaction", "transactions", "account", "accounts"}
    
    for table in db["tables"]:
        table_name = table.get("name", "")
        
        # 规则 2.1：命名规范
        if table_name and not is_snake_case(table_name):
            issues.append({
                "id": f"DB-{len(issues)+1:03d}",
                "type": "naming_convention",
                "severity": "WARNING",
                "table": table_name,
                "message": f"表名 '{table_name}' 不符合小写蛇形命名规范",
                "rule": "规则 2：数据库设计健康红线 - 命名规范",
                "suggestion": f"建议重命名为 '{to_snake_case(table_name)}'"
            })
        
        if table_name and is_reserved_word(table_name):
            issues.append({
                "id": f"DB-{len(issues)+1:03d}",
                "type": "reserved_word",
                "severity": "BLOCKER",
                "table": table_name,
                "message": f"表名 '{table_name}' 是数据库保留字",
                "rule": "规则 2：数据库设计健康红线 - 命名规范",
                "suggestion": "建议重命名表名"
            })
        
        # 规则 2.2：主键与索引
        if not table.get("primary_key"):
            issues.append({
                "id": f"DB-{len(issues)+1:03d}",
                "type": "missing_primary_key",
                "severity": "BLOCKER",
                "table": table_name,
                "message": f"表 '{table_name}' 没有定义主键",
                "rule": "规则 2：数据库设计健康红线 - 主键与索引",
                "suggestion": "为表添加主键"
            })
        
        # 检查高频查询字段是否有索引
        indexed_columns = set()
        for idx in db["indexes"]:
            if idx.get("table") == table_name:
                indexed_columns.update(idx.get("columns", []))
        
        # 外键字段应有索引
        for fk in table.get("foreign_keys", []):
            fk_column = fk.get("column", "")
            if fk_column and fk_column not in indexed_columns:
                issues.append({
                    "id": f"DB-{len(issues)+1:03d}",
                    "type": "missing_index",
                    "severity": "WARNING",
                    "table": table_name,
                    "column": fk_column,
                    "message": f"表 '{table_name}' 的外键字段 '{fk_column}' 没有索引",
                    "rule": "规则 2：数据库设计健康红线 - 主键与索引",
                    "suggestion": f"为 '{fk_column}' 添加索引"
                })
        
        # 规则 2.3：数据完整性（核心业务表必须有时间字段）
        if table_name and any(core in table_name.lower() for core in core_tables):
            columns = table.get("columns", [])
            has_create_time = any(
                col["name"] == "create_time" or col["name"] == "created_at"
                for col in columns
            )
            has_update_time = any(
                col["name"] == "update_time" or col["name"] == "updated_at"
                for col in columns
            )
            
            if not has_create_time:
                issues.append({
                    "id": f"DB-{len(issues)+1:03d}",
                    "type": "missing_create_time",
                    "severity": "WARNING",
                    "table": table_name,
                    "message": f"核心业务表 '{table_name}' 缺少创建时间字段 (create_time/created_at)",
                    "rule": "规则 2：数据库设计健康红线 - 数据完整性",
                    "suggestion": "添加 create_time 字段"
                })
            
            if not has_update_time:
                issues.append({
                    "id": f"DB-{len(issues)+1:03d}",
                    "type": "missing_update_time",
                    "severity": "WARNING",
                    "table": table_name,
                    "message": f"核心业务表 '{table_name}' 缺少更新时间字段 (update_time/updated_at)",
                    "rule": "规则 2：数据库设计健康红线 - 数据完整性",
                    "suggestion": "添加 update_time 字段"
                })
        
        # 检查字段命名和类型
        for column in table.get("columns", []):
            col_name = column.get("name", "")
            if col_name and not is_snake_case(col_name):
                issues.append({
                    "id": f"DB-{len(issues)+1:03d}",
                    "type": "column_naming",
                    "severity": "SUGGESTION",
                    "table": table_name,
                    "column": col_name,
                    "message": f"字段 '{col_name}' 不符合小写蛇形命名规范",
                    "rule": "规则 2：数据库设计健康红线 - 命名规范",
                    "suggestion": f"建议重命名为 '{to_snake_case(col_name)}'"
                })
    
    return issues


def validate_api_contract(parsed_inputs):
    """
    校验 API 规范化与安全红线：REST 规范、幂等性、异常处理
    
    Args:
        parsed_inputs (dict): 结构化的输入数据
        
    Returns:
        list: 问题列表
    """
    issues = []
    api = parsed_inputs["api"]
    
    for endpoint in api["endpoints"]:
        method = endpoint.get("method", "").upper()
        path = endpoint.get("path", "")
        operation_id = endpoint.get("operationId", "")
        
        # 规则 3.1：REST 规范
        if method == "POST" and path.startswith("/api/"):
            # 判断是否应该使用 GET/PUT/PATCH/DELETE
            if path.endswith("/search") or path.endswith("/query") or "list" in path.lower():
                issues.append({
                    "id": f"API-{len(issues)+1:03d}",
                    "type": "rest_violation",
                    "severity": "WARNING",
                    "api": f"{method} {path}",
                    "message": f"查询类接口使用了 POST，建议使用 GET",
                    "rule": "规则 3：API 规范化与安全红线 - REST 规范",
                    "suggestion": "改为 GET 方法"
                })
        
        if method and method not in ["GET", "POST", "PUT", "PATCH", "DELETE"]:
            issues.append({
                "id": f"API-{len(issues)+1:03d}",
                "type": "invalid_http_method",
                "severity": "BLOCKER",
                "api": f"{method} {path}",
                "message": f"HTTP 方法 '{method}' 不是标准 REST 方法",
                "rule": "规则 3：API 规范化与安全红线 - REST 规范",
                "suggestion": "使用标准 REST 方法 (GET/POST/PUT/PATCH/DELETE)"
            })
        
        # 规则 3.2：幂等性与安全
        if method in ["POST", "PUT", "PATCH", "DELETE"]:
            if method in ["PUT", "PATCH", "DELETE"] and not endpoint.get("idempotent"):
                issues.append({
                    "id": f"API-{len(issues)+1:03d}",
                    "type": "missing_idempotency",
                    "severity": "WARNING",
                    "api": f"{method} {path}",
                    "message": f"修改/删除类接口未设计幂等性",
                    "rule": "规则 3：API 规范化与安全红线 - 幂等性与安全",
                    "suggestion": "添加幂等性设计（如幂等键）"
                })
            
            # 敏感接口检查
            sensitive_keywords = ["password", "reset", "payment", "pay", "transaction", "transfer"]
            if any(keyword in path.lower() for keyword in sensitive_keywords):
                if not endpoint.get("security"):
                    issues.append({
                        "id": f"API-{len(issues)+1:03d}",
                        "type": "missing_security",
                        "severity": "BLOCKER",
                        "api": f"{method} {path}",
                        "message": f"敏感数据接口 '{path}' 缺少安全防范机制描述",
                        "rule": "规则 3：API 规范化与安全红线 - 幂等性与安全",
                        "suggestion": "添加鉴权、加密等安全机制"
                    })
        
        # 规则 3.3：异常处理
        responses = endpoint.get("responses", {})
        if "default" not in responses and not any(
            str(code).startswith("5") for code in responses.keys()
        ):
            issues.append({
                "id": f"API-{len(issues)+1:03d}",
                "type": "missing_error_response",
                "severity": "WARNING",
                "api": f"{method} {path}",
                "message": f"API 未定义统一的错误响应格式",
                "rule": "规则 3：API 规范化与安全红线 - 异常处理",
                "suggestion": "定义包含 code、message、data 的标准错误响应格式"
            })
        
        # 检查分页参数
        if method == "GET" and ("list" in path.lower() or "search" in path.lower()):
            params = endpoint.get("parameters", [])
            has_pagination = any(
                p.get("name") in ["page", "page_size", "limit", "offset"]
                for p in params
            )
            if not has_pagination:
                issues.append({
                    "id": f"API-{len(issues)+1:03d}",
                    "type": "missing_pagination",
                    "severity": "SUGGESTION",
                    "api": f"{method} {path}",
                    "message": f"列表查询接口缺少分页参数",
                    "rule": "规则 3：API 规范化与安全红线",
                    "suggestion": "添加 page 和 page_size 参数"
                })
    
    return issues


def validate_security_and_performance(parsed_inputs):
    """
    校验架构解耦与非功能性约束：循环依赖、性能考量
    
    Args:
        parsed_inputs (dict): 结构化的输入数据
        
    Returns:
        list: 问题列表
    """
    issues = []
    arch = parsed_inputs["arch"]
    api = parsed_inputs["api"]
    
    # 规则 4.1：无循环依赖
    dependencies = arch["dependencies"]
    modules = {m.get("name") for m in arch["modules"] if m.get("name")}
    
    # 检测直接循环依赖
    for module_name in modules:
        dependents = [d["from"] for d in dependencies if d.get("to") == module_name]
        for dependent in dependents:
            # 检查 dependent 是否依赖 module_name
            has_reverse = any(
                d.get("from") == module_name and d.get("to") == dependent
                for d in dependencies
            )
            if has_reverse:
                issues.append({
                    "id": f"SP-{len(issues)+1:03d}",
                    "type": "circular_dependency",
                    "severity": "BLOCKER",
                    "modules": [module_name, dependent],
                    "message": f"模块 '{module_name}' 与 '{dependent}' 存在循环依赖",
                    "rule": "规则 4：架构解耦与非功能性约束 - 无循环依赖",
                    "suggestion": "重构模块依赖关系，引入中间层或事件驱动"
                })
    
    # 检测深度依赖链
    for module_name in modules:
        depth = calculate_dependency_depth(module_name, dependencies)
        if depth > 5:
            issues.append({
                "id": f"SP-{len(issues)+1:03d}",
                "type": "deep_dependency",
                "severity": "WARNING",
                "module": module_name,
                "depth": depth,
                "message": f"模块 '{module_name}' 的依赖链深度为 {depth}，超过建议阈值 5",
                "rule": "规则 4：架构解耦与非功能性约束",
                "suggestion": "考虑扁平化依赖结构"
            })
    
    # 规则 4.2：非功能性考量（高频 API 的缓存/流控）
    high_freq_keywords = ["list", "search", "query", "feed", "timeline"]
    for endpoint in api["endpoints"]:
        path = endpoint.get("path", "")
        if any(keyword in path.lower() for keyword in high_freq_keywords):
            if not endpoint.get("caching") and not endpoint.get("rate_limit"):
                issues.append({
                    "id": f"SP-{len(issues)+1:03d}",
                    "type": "missing_non_functional",
                    "severity": "SUGGESTION",
                    "api": f"{endpoint.get('method', '')} {path}",
                    "message": f"高频访问 API 未提及缓存或流控机制",
                    "rule": "规则 4：架构解耦与非功能性约束 - 非功能性考量",
                    "suggestion": "添加 Redis 缓存或流控机制"
                })
    
    return issues


def run_all_validators(parsed_inputs):
    """
    运行所有校验器，返回合并后的问题列表
    
    Args:
        parsed_inputs (dict): 结构化的输入数据
        
    Returns:
        list: 所有校验器产生的问题列表
    """
    all_issues = []
    all_issues.extend(validate_requirement_coverage(parsed_inputs))
    all_issues.extend(validate_database_health(parsed_inputs))
    all_issues.extend(validate_api_contract(parsed_inputs))
    all_issues.extend(validate_security_and_performance(parsed_inputs))
    return all_issues
