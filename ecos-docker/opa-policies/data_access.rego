# =============================================================================
# ecos.data_access —— 详设-01 §4.7.2「角色 × 通道验收矩阵」护栏（OPA）面唯一策略源
#
# 批次：#142/B4-3c（差距文档 D-B4-14/D-B4-18/D-B4-20）。
#
# 为什么文件名/包名是下划线而不是点号：
#   {@code OpaPolicyService.SAFE_NAME = ^[a-zA-Z0-9_-]{1,64}$} 不允许点号，
#   而裁决入口此前用 {@code "data.access"} 调 evaluate ⇒ 校验在 try 之外抛出，
#   **OPA 从未被咨询**，每次都落异常兜底（allow=false + OPA_ERROR_FAIL_CLOSED）。
#   本策略的存在就是为了让护栏真正被咨询，名字合规是前提，不是风格问题。
#
# 统一输入契约（两个入口同形态，字段出处见各行注释）：
#   input.purpose                       page|export|ai（/decision 显式给；
#                                        AI 工具裁决载荷缺段时由 environment.channel
#                                        =="AGENT" 判为 ai，其余判为 page）
#   input.subject.roles[]               主体角色集 —— /decision 由 SecurityDecisionService
#                                        从 DecisionContext.roles() 注入；AI 通道由
#                                        ToolAdjudicationRequest.Subject(roles) 注入
#   input.subject.department            服务端派生的组织域（客户端 X-Org-Id 已被剥离）
#   input.subject.userId / input.asset  仅用于义务文本的可追溯性
#   input.resource.dataScope.*          AI 通道显式声明的请求域（projectIds/departmentId）
#
# 角色集与 V246 策略种子同源（rls-finance-all / rls-project-owner / rls-dept-scope /
# cls-non-finance-hide）；新增角色须先扩 §4.7.2 矩阵再改本表（矩阵纪律：禁先实现后补格）。
# 缺上下文一律 **不放行**（fail-closed）：角色读不到、部门读不到、AI 请求不声明数据域，
# 都是拒绝而不是默认允许。
# =============================================================================

package ecos.data_access

default allow = false

# ── 输入归一 ────────────────────────────────────────────────────────────────

# 主体角色集（接受 /decision 的顶层 roles 与 AI 载荷的 subject.roles 两种书写）
subject_roles := s {
	all := array.concat(
		object.get(input, "roles", []),
		object.get(object.get(input, "subject", {}), "roles", []),
	)
	s := {r | r := all[_]}
}

subject_id := object.get(object.get(input, "subject", {}), "userId", "")

subject_department := object.get(object.get(input, "subject", {}), "department", "")

requested_purpose := object.get(input, "purpose", "")

agent_channel {
	object.get(object.get(input, "environment", {}), "channel", "") == "AGENT"
}

# 三通道同形态进入本策略：purpose 显式优先，缺失时按通道特征推断
purpose := requested_purpose {
	requested_purpose != ""
}

purpose := "ai" {
	requested_purpose == ""
	agent_channel
}

purpose := "page" {
	requested_purpose == ""
	not agent_channel
}

asset := object.get(input, "asset", "")

data_scope := object.get(object.get(input, "resource", {}), "dataScope", {})

requested_project_ids := object.get(data_scope, "projectIds", [])

requested_department := object.get(data_scope, "departmentId", "")

# ── 角色矩阵（§4.7.2 行）────────────────────────────────────────────────────

# 允许进入数据面的角色；无权用户（无此集成员）落 NO_DATA_ACCESS_ROLE
data_roles := {"finance_lead", "project_owner", "dept_user"}

# 唯一可无请求域限定的角色（对应 rls-finance-all 的 1=1 全量面）
finance_roles := {"finance_lead"}

has_data_role := count(data_roles & subject_roles) > 0

is_finance := count(finance_roles & subject_roles) > 0

# ── 违规集（拒绝原因的单点判定；多条并存时逐字全部落 deniedReason）──────────

violation["NO_DATA_ACCESS_ROLE"] {
	not has_data_role
}

# AI 通道禁全域扫描：非财务主体必须显式声明请求域
violation["AI_SCOPE_NOT_DECLARED"] {
	has_data_role
	not is_finance
	purpose == "ai"
	count(requested_project_ids) == 0
}

# 组织域未解析 ⇒ 无横向越权地板可用，按 fail-closed 拒绝
violation["AI_SUBJECT_DEPARTMENT_UNRESOLVED"] {
	has_data_role
	not is_finance
	purpose == "ai"
	count(requested_project_ids) > 0
	subject_department == ""
}

# 跨部门请求域 = 横向越权（矩阵 p1 ALLOW / p2 DENY 的判据）
violation["AI_SCOPE_OUT_OF_SUBJECT_DEPARTMENT"] {
	has_data_role
	not is_finance
	purpose == "ai"
	count(requested_project_ids) > 0
	subject_department != ""
	requested_department != subject_department
}

allow := count(violation) == 0

# ── 义务（C.2.5 ③ 四类：deniedReason / maskHints / watermark / rateLimitKey）──
#
# 四类义务集一律 `default = []`：拒绝态必须仍然带 deniedReason 义务。
# 若写成"命中才定义、未命中另一分支定义 []"，则当某分支的两个条件一真一假时
# 该规则整体<b>未定义</b>，array.concat 随之未定义 ⇒ 整个 obligations 段消失，
# DENIED 理由一起丢（真引擎实测：DENY 用例的 obligations 为 undefined）。
# 这是本批录制 oracle 时抓出来的形态缺陷，修法即用 default 保证恒有值。

obligations := array.concat(
	deny_obligations,
	array.concat(mask_obligations, array.concat(watermark_obligations, rate_obligations)),
)

default deny_obligations = []

deny_obligations := out {
	count(violation) > 0
	out := [{"obligation": "DENIED", "deniedReason": concat("+", sort(violation))}]
}

# 与 cls-non-finance-hide 的 blocked_cols 同源；财务主体不脱敏
sensitive_columns := ["hourly_rate", "amount"]

default mask_obligations = []

mask_obligations := out {
	not is_finance
	out := [{"obligation": "mask", "column": c, "strategy": "hide"} | c := sensitive_columns[_]]
}

# §2.10.2 五件套之四：导出件页眉页脚水印义务（D-B4-11/D-B4-12 的出处）
default watermark_obligations = []

watermark_obligations := out {
	purpose == "export"
	count(violation) == 0
	out := [{
		"obligation": "watermark",
		"watermark": sprintf("ECOS 导出 主体=%s 资产=%s 通道=export", [subject_id, asset]),
	}]
}

default rate_obligations = []

rate_obligations := out {
	purpose == "ai"
	count(violation) == 0
	out := [{"obligation": "rateLimit", "rateLimitKey": sprintf("ai.tool.%s", [subject_id])}]
}
