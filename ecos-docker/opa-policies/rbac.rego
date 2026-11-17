package ecos.rbac

# OPA 0.63.0 上 `some r in ...` 需要显式导入 future 关键字（Rego v1 之前不是默认语法）。
# 本轮加 is_scenario_admin 时才用到该表达式；若不加此行，容器会以
# rego_parse_error 崩溃重启（实测：load error 直接拒绝整个 bundle）。
import future.keywords.in

default allow = false

allow {
    input.action == "pipeline.execute"
    input.role == "system"
}

allow {
    input.action == "read"
    input.role != ""
}

allow {
    input.action == "list"
    input.role != ""
}

allow {
    input.action == "get"
    input.role != ""
}

# ═══ 场景状态机迁移动作（2026-11-17 D57 收口，用户授权批）═══
# 裁决链：ScenarioStatusMachine.assertTransition → SecurityEngineMigrationPermissionChecker.isAllowed
# → WorkspaceSecurityEngineClient.evaluate(permissionPoint=scenario:write|scenario:activate)
# → PolicyEngineController(/api/v1/security/policy-engine/evaluate, policy=rbac) → 本包。
# 载荷：WorkspaceSecurityEngineClient:284 固定送 {action:<permissionPoint>, permission:<同>,
#       subject:{userId, role:"user"}} —— 顶层无 role，故规则以 permission 字段为准，
#       并保留 action 判据供其它调用方复用。
#
# 为什么不是"补数据库权限行"：td_permission 里有 scenario:* 但状态机走的是 OPA evaluate，
# 而本包此前对 scenario:write/activate 零规则 ⇒ default allow=false ⇒ EVALUATED_DENY。
# 2026-11-17 实测：{"action":"scenario:write"} → allow:false；补本组规则后应转 allow:true。
#
# ── 2026-11-17 第二轮：从「已认证即放行」收紧到角色级 ──
# 首版四规则判据是 input.subject.userId != ""，实测 DEPT_USER 调 scenario:write 也返
# allow:true —— 等于「任何登录用户都能改场景状态」。用户裁决「收紧到角色级」。
#
# 配套改动（同批）：WorkspaceSecurityEngineClient.evaluate 此前把 subject 硬编码为
# {userId, role:"user"}，角色恒为字面量 "user"，OPA 侧根本判不出角色。现改为送真实
# 角色：role = 首个角色、roles = 完整列表（值取自 JWT claims，形如
# ["ROLE_SUPER_ADMIN","admin","SECURITY_AUDITOR"]）。
#
# 分级：
#   写类（write / activate / publish）→ 需管理角色
#   读类（read）                     → 已认证即可（读面不属于越权风险面）
# 管理角色同时认两套命名：既有规则里 input.role == "admin" 与 =="ROLE_SUPER_ADMIN" 并存。
is_scenario_admin {
    input.subject.role == "admin"
}
is_scenario_admin {
    input.subject.role == "ROLE_SUPER_ADMIN"
}
is_scenario_admin {
    some r in input.subject.roles
    r == "admin"
}
is_scenario_admin {
    some r in input.subject.roles
    r == "ROLE_SUPER_ADMIN"
}

allow {
    input.action == "scenario:write"
    is_scenario_admin
}

allow {
    input.action == "scenario:activate"
    is_scenario_admin
}

allow {
    input.action == "scenario:read"
    input.subject.userId != ""
}

allow {
    input.action == "scenario:publish"
    is_scenario_admin
}

# ═══ WAgent L3 特权动作（2026-11-11 用户 /goal 批 A 授权，A-α 最小面）═══
# 裁决链：WAgentCandidateController.publish / WAgentDecisionActionController.commit
# → AiSecurityEngineClient.evaluate(roles) → policy-engine/evaluate(policy=rbac)
# → 本包。input.role = 主体角色集首角色（buildEvaluateBody 归一，admin/ROLE_SUPER_ADMIN）。
# 仍未放行：ai:guardrail:input/output（Copilot 工具裁决面，属另一裁决通道 data_access，
# 待其专属裁决批处理，不在本次授权面内）。
allow {
    input.action == "candidate:publish"
    input.role == "admin"
}

allow {
    input.action == "candidate:publish"
    input.role == "ROLE_SUPER_ADMIN"
}

allow {
    input.action == "action:commit"
    input.role == "admin"
}

allow {
    input.action == "action:commit"
    input.role == "ROLE_SUPER_ADMIN"
}

# ═══ 内容护栏裁决面（2026-11-14 用户裁决：补策略后验证 V-COP-014）═══
# 裁决链：GuardrailsServiceImpl.validate → AiSecurityEngineClient.evaluateData
# → policy-engine/evaluate(policy=rbac, action=ai:guardrail:input|output)
# 本规则只表达「已认证主体可进入护栏裁决」——通过与否仍由 security-engine 的
# 规则引擎（脱敏义务/违规判定）决定，不是"一律放行内容"。
# 无此规则时 copilot 工具链在第一次护栏调用即 403，会话压根不会被创建（V-COP-014 BLOCKED 根因）。
#
# 注意载荷形态：护栏调用方（GuardrailsServiceImpl#evaluateBody）送的是
#   {action, type, stage, subject:{userId, roles:[...]}, content, ...}
# ——角色在 subject.roles 数组里，**没有**顶层 role。只判 input.role 会让规则恒 false
# （2026-11-14 实测：payload 带 subject.roles 时 allow=false）。
# 顶层 role 判据保留，供其它单角色调用方（buildEvaluateBody 归一形态）复用。
allow {
    startswith(input.action, "ai:guardrail:")
    input.role != ""
}

allow {
    startswith(input.action, "ai:guardrail:")
    count(input.subject.roles) > 0
}

# ═══ LLM 外发裁决面（2026-11-14 同批：V-COP-014 会话连续性前置）═══
# 裁决链：LLMGatewayImpl.abacVerdictForLlmCall → SecurityEngineBridge.evaluateVerdict(action="llm:chat")
# 载荷：{action:"llm:chat", provider, model, subject:{userId, tenantId, role:"llm-gateway-service"}}
# 无此规则时任何 copilot/agent 会话在第一次 LLM 调用即 403→UNEVALUATED→ECOS-AI-503，
# 会话消息永远写不进去（V-COP-014 第二层阻塞，2026-11-14 实测 policyState=EVALUATED_DENY）。
# 只放行网关内部服务主体；终端用户身份（userId=u-*）不在此规则面内。
allow {
    input.action == "llm:chat"
    input.subject.role == "llm-gateway-service"
}
