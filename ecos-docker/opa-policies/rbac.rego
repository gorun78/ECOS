package ecos.rbac

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
