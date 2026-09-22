"""
输入解析器模块

负责解析 PRD、架构规格书、OpenAPI 规范和 DDL 脚本
"""

from .utils import read_artifact, parse_prd, parse_arch_spec, parse_openapi, parse_ddl


def parse_inputs(prd_ref, arch_spec_ref, openapi_ref, ddl_ref):
    """
    解析所有输入制品，构建内部结构化表示
    
    Args:
        prd_ref (str): PRD 制品引用
        arch_spec_ref (str): 架构规格书制品引用
        openapi_ref (str): OpenAPI 规范制品引用
        ddl_ref (str): DDL 脚本制品引用
        
    Returns:
        dict: 结构化的输入数据
    """
    # 解析 PRD
    prd = parse_prd(read_artifact(prd_ref))
    requirements = prd.get("requirements", [])
    business_entities = prd.get("business_entities", [])
    business_flows = prd.get("business_flows", [])
    
    # 解析 ARCH_SPEC
    arch_spec = parse_arch_spec(read_artifact(arch_spec_ref))
    modules = arch_spec.get("modules", [])
    module_dependencies = arch_spec.get("dependencies", [])
    tech_stack = arch_spec.get("tech_stack", "")
    
    # 解析 OpenAPI
    openapi = parse_openapi(read_artifact(openapi_ref))
    endpoints = openapi.get("endpoints", [])
    schemas = openapi.get("schemas", {})
    
    # 解析 DDL
    ddl = parse_ddl(read_artifact(ddl_ref))
    tables = ddl.get("tables", [])
    indexes = ddl.get("indexes", [])
    constraints = ddl.get("constraints", [])
    
    return {
        "prd": {
            "requirements": requirements,
            "entities": business_entities,
            "flows": business_flows
        },
        "arch": {
            "modules": modules,
            "dependencies": module_dependencies,
            "tech_stack": tech_stack
        },
        "api": {
            "endpoints": endpoints,
            "schemas": schemas
        },
        "db": {
            "tables": tables,
            "indexes": indexes,
            "constraints": constraints
        }
    }
