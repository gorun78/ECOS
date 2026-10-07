package ecos.abac

import future.keywords.in

# B3 / R1.7（详细设计-01 W47 · 2026-10-07 裁决 C）：ABAC 通道入口。
#
#   裁决 C：简版策略体——subject.role ∈ {admin, operator} 时 allow；其余一律 fail-closed
#   deny。roles 名单后续由 W-OPA 分册策略补全。
#
# 通路接线：
#   SecurityPolicyController.evaluate(...) → OpaPolicyService.evaluate("abac", input)
#   → GET /v1/data/ecos/abac/allow (policy="abac" 经 opaPath 拼接 "/v1/data/ecos/" + policy)
#   → doc 顶层键 {allow, policy_id}（{@code obligations} 键缺省时 OpaPolicyService 走空
#      List 兜底，Future W-OPA 补 obligations 后再扩容本文件即可，Java 侧零改动）。
#
# 与 rbac.rego（既有：action-based 兜底）并存；abac 是 subject-aware 策略通道，二者按
# policy 名称走独立 package，OPA 内部无冲突。

default allow := false

default policy_id := "abac.v1"

# 特权角色白名单——admin/operator 放行所有 action（含写）；其他角色 0 放行（fail-closed）
privileged_roles := ["admin", "operator"]

allow {
    input.subject.role in privileged_roles
}
