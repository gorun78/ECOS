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
