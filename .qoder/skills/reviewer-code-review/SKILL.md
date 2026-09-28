---
name: reviewer-code-review
description: "Reviewer 代码审查执行 Skill：审查 Fullstack 提交的代码变更，优先使用 Open Code Review 引擎，结合安全审计和架构一致性专项检查。输出带 deliverable_allowed 判定的 REVIEW_REPORT + REVIEW_REPORT_APPROVAL_RECORD。当 PM 向 reviewer 分发任务或用户说'代码审查'、'review 代码'时触发。"
version: 3.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [reviewer, code-review, code-quality, open-code-review, ocr, pull-request, approval-record, deliverable-gate]
    related_skills: [open-code-review, reviewer-arch-consistency, reviewer-security-audit]
    artifact_type: REVIEW_REPORT
    workflow_modes: [L3]
    dependencies: [open-code-review]
---

# Reviewer Code Review Skill (v3 — 交付门禁版)

## 核心原则

审查完成后必须生成 `REVIEW_REPORT_APPROVAL_RECORD`，含明确的 `deliverable_allowed` 判定。`deliverable_allowed=false` 时阻断交付，PM 必须等待修复后才能做质量裁定。每个缺陷必须能追溯到 PRD 功能来源（P0/P1 缺陷必须标注对应 PRD）。

**人工确认豁免**：REVIEW_REPORT（源代码审查报告）无需自然人审核确认，其交付门禁由四项质量门禁（P0_GATE / P1_GATE / SECURITY_GATE / ARCH_GATE）的 `deliverable_allowed` 判定自动闭合：
- `deliverable_allowed=true` → `status=AUTO_CLOSED`，PM 可直接进入交付环节，无需人工审核
- `deliverable_allowed=false` → `status=PENDING_HUMAN_REVIEW`，等待 PM 触发修复循环（修复后重新审查，而非人工签字放行）

> 四类审查报告（需求 / 设计 / 用例 / 代码）均以"质量门禁自动闭合 + 修复循环"替代"人工签字"，避免人工成为交付瓶颈；自然人的业务审批（PRD 审批 / Arch 审批 / 代码合并审批）由 PM 在独立节点协调。

## 关键机制

### 质量门禁判定

| 门禁 | 判定条件 | 阻断条件 |
|------|---------|---------|
| **P0_GATE** | P0 缺陷数 = 0 | 任何 P0 缺陷存在 → FAIL |
| **P1_GATE** | P1 缺陷数 ≤ 3 | P1 缺陷数 > 3 → FAIL |
| **SECURITY_GATE** | 无 CRITICAL/HIGH 安全漏洞 | 存在 CRITICAL/HIGH → FAIL |
| **ARCH_GATE** | 无 P0/P1 架构违规 | 存在 P0/P1 架构违规 → FAIL |

### deliverable_allowed 判定

```
deliverable_allowed = true 条件：
  P0_GATE = PASS
  P1_GATE = PASS
  SECURITY_GATE = PASS
  ARCH_GATE = PASS

deliverable_allowed = false 条件：
  任何 P0 缺陷存在 → FAIL
  P1 缺陷数 > 3 → FAIL
  存在 CRITICAL/HIGH 安全漏洞 → FAIL
  存在 P0/P1 架构违规 → FAIL
```

### PRD 追溯表

每个缺陷必须追溯到 PRD 功能：

```markdown
## 缺陷 → PRD 追溯表

| 缺陷 ID | 缺陷描述 | 优先级 | PRD 来源 | PRD 功能名称 | 文件位置 |
|---------|---------|--------|---------|-------------|---------|
| C-001 | SQL 注入漏洞 | P0 | PRD-F1.1 | 用户注册-邮箱注册 | UserServiceImpl.java:42 |
| C-002 | IDOR 越权访问 | P1 | PRD-F4.1 | 下单-创建订单 | OrderController.java:42 |
```

---

## 触发条件

- PM 向 Reviewer 分发任务（`role: reviewer`）
- Fullstack 完成 SOURCE_PATCH 并提交待审查
- 用户说"代码审查"、"review 代码"、"审查 PR"、"ocr 审查"

## 输入

- **必需**：SOURCE_PATCH（APPROVED，artifact_ref）、PRD（已批准，artifact_ref）、ADR（已批准）
- **必需**：OpenAPI 规范（APPROVED）
- **可选**：DDL 建议、测试用例、编码规范文档、业务上下文文件
- **固定约束**：技术栈、安全规范、编码规范、项目版本

## 输出制品

- **REVIEW_REPORT**：代码审查报告（含 deliverable_allowed 判定）
  - 代码质量评分
  - 缺陷列表（按优先级 P0/P1/P2/P3 分类 + PRD 追溯）
  - 改进建议
- **REVIEW_REPORT_APPROVAL_RECORD**：审查批准记录（artifact_type: APPROVAL_RECORD）
- **REVIEW_COMMENTS**：审查意见（逐文件的行内评论）

## 执行步骤

### Step 0: SOURCE_PATCH 前置校验（第一门禁）

```python
def validate_source_patch(source_patch_ref):
    """
    审查前，必须校验 SOURCE_PATCH 已 APPROVED
    """
    approval_record = read_artifact_approval_record(source_patch_ref)
    if not approval_record:
        raise ValueError(f"SOURCE_PATCH {source_patch_ref} 无批准记录，审查禁止开始")
    if approval_record["status"] != "APPROVED":
        raise ValueError(f"SOURCE_PATCH 状态为 {approval_record['status']}，必须 APPROVED 才能审查")
    if not approval_record.get("deliverable_allowed"):
        raise ValueError("SOURCE_PATCH deliverable_allowed=false，禁止开始审查")
    return {
        "source_version": approval_record["version"],
        "source_hash": approval_record["hash"],
        "prd_ref": approval_record.get("prd_ref"),
        "arch_ref": approval_record.get("arch_ref"),
        "openapi_ref": approval_record.get("openapi_ref")
    }
```

```markdown
## SOURCE_PATCH 前置校验 — 第一门禁

收到审查任务：{task_id}
前置校验：

1. [ ] SOURCE_PATCH 状态为 APPROVED ✅
2. [ ] SOURCE_PATCH 有批准记录 ✅
3. [ ] SOURCE_PATCH 的 deliverable_allowed = true ✅
4. [ ] PRD 引用：PRD@{prd_hash} ✅
5. [ ] OpenAPI 引用：OPENAPI@{openapi_hash} ✅

当前 SOURCE_PATCH：
- 版本：{version}
- Hash：{hash}
- 状态：APPROVED
- PRD 来源：{prd_name}

→ 第一门禁通过，可开始审查
```

校验失败时回复 PM：

```json
{
  "artifact": "REVIEW_REPORT",
  "task_id": "{task_id}",
  "status": "BLOCKED",
  "gate": "FIRST_GATE",
  "blocker": "SOURCE_PATCH_NOT_APPROVED",
  "deliverable_allowed": false,
  "message": "SOURCE_PATCH 必须 APPROVED 才能开始审查"
}
```

---

### Step 1: 理解变更范围

```python
# 分析变更文件（通过 git diff 获取）
changed_files = [
    "src/main/java/com/example/app/controller/UserController.java",
    "src/main/java/com/example/app/service/UserServiceImpl.java",
    "src/main/java/com/example/app/repository/UserRepository.java",
    "src/main/java/com/example/app/dto/UserResponse.java",
    "frontend/src/views/UserListPage.vue",
    "frontend/src/api/user.ts",
]

# 变更统计
stats = {
    "files_changed": 6,
    "lines_added": 245,
    "lines_deleted": 32,
    "files_by_type": {
        "java": 4,
        "vue": 1,
        "ts": 1
    }
}
```

---

### Step 2: 代码审查（OCR 驱动）

#### 2.1 调用 open-code-review skill

```bash
cd {repo_dir}

# 依赖检查
which ocr || (npm install -g @alibaba-group/open-code-review && which ocr)
ocr llm test

# 调用 OCR 审查
ocr review \
  --from "{base_ref}" \
  --to "{head_ref}" \
  --format json \
  --concurrency 8 \
  --audience agent \
  --background-file docs/requirements/PRD.md \
  2>&1 | tee /tmp/ocr_raw_output.json

OCR_EXIT_CODE=${PIPESTATUS[0]}
```

#### 2.2 解析 OCR 结果并映射到 P0/P1/P2/P3

> 区块模板/示例见 references（原 SKILL.md L209-265），生成对应制品前必须读取。

---

### Step 3: 缺陷 PRD 追溯标注

每个 OCR 缺陷需要追溯到 PRD 功能（通过比对 OpenAPI 和 PRD 功能映射）：

```python
def annotate_prd_traceability(defects, openapi_to_prd_mapping):
    """
    为每个缺陷标注 PRD 功能来源
    """
    annotated = []
    for d in defects:
        # 通过 OpenAPI operationId 追溯到 PRD
        api_path = d.get("file", "")
        operationId = d.get("rule", "")  # 或从 OCR 结果获取

        prd_ref = openapi_to_prd_mapping.get(operationId, "unknown")
        prd_name = extract_prd_name(prd_ref)

        d["prd_ref"] = prd_ref
        d["prd_name"] = prd_name
        annotated.append(d)
    return annotated
```

```markdown
## 缺陷 → PRD 追溯表

| 缺陷 ID | 缺陷描述 | 优先级 | PRD 来源 | PRD 功能名称 |
|---------|---------|--------|---------|-------------|
| C-001 | SQL 注入漏洞 | P0 | PRD-F1.1 | 用户注册-邮箱注册 |
| C-002 | IDOR 越权访问 | P1 | PRD-F4.1 | 下单-创建订单 |
| C-003 | 密码加密强度不足 | P1 | PRD-F1.1 | 用户注册-邮箱注册 |
| C-004 | 分页参数缺失 | P2 | PRD-F3.1 | 商品搜索-关键词搜索 |
```

---

### Step 4: 架构一致性专项检查

调用 `reviewer-arch-consistency` skill（详见该 skill 输出）：

- 模块边界是否清晰
- API 契约是否与 OpenAPI 一致
- 数据模型是否与 DDL 一致

---

### Step 5: 安全专项检查

调用 `reviewer-security-audit` skill（详见该 skill 输出）：

- IDOR（越权访问）
- JWT 安全配置
- DOM XSS
- CSRF

---

### Step 6: 质量门禁判定

> 区块模板/示例见 references（原 SKILL.md L329-378），生成对应制品前必须读取。

---

### Step 7: 生成 REVIEW_REPORT（带明确门禁判定）

> 区块模板/示例见 references（原 SKILL.md L384-451），生成对应制品前必须读取。

---

### Step 8: 生成 REVIEW_REPORT_APPROVAL_RECORD

> 区块模板/示例见 references（原 SKILL.md L457-502），生成对应制品前必须读取。

### 落盘路径

REVIEW_REPORT_APPROVAL_RECORD 除作为 Skill 返回值外，必须同步落盘为 JSON：

| 制品 | 路径 |
|---|---|
| REVIEW_REPORT | `docs/03开发阶段/{NN}-审查报告/{task_id}_review_report.md` |
| REVIEW_COMMENTS | `docs/03开发阶段/{NN}-审查报告/{task_id}_review_comments.md` |
| REVIEW_REPORT_APPROVAL_RECORD | `docs/03开发阶段/{NN}-审查报告/{task_id}_review_approval_record.json` |
| ARCH_CONSISTENCY（来自 reviewer-arch-consistency） | `docs/03开发阶段/{NN}-审查报告/{task_id}_arch_consistency.md` |
| SECURITY_ASSESSMENT（来自 reviewer-security-audit） | `docs/03开发阶段/{NN}-审查报告/{task_id}_security_assessment.md` |
| OCRResult（来自 open-code-review） | `docs/03开发阶段/{NN}-审查报告/{task_id}_ocr_report.md` |

> `{NN}` 为审查报告目录序号，**按 `docs/03开发阶段/` 同级目录动态确定**（取当前最大序号 +1，复用已有 `*-审查报告` 目录），详见 docs/AGENTS.md「审查报告子目录」。当前快照：`03-03`。

执行前按动态编号规则解析目录并创建：

```bash
mkdir -p "docs/03开发阶段/{NN}-审查报告"
```

arch-consistency 与 security-audit 的子结果并入本 REVIEW_REPORT_APPROVAL_RECORD，不再单独生成 JSON 批准记录。

---

## PM 回复模板

### 审查完成 + deliverable_allowed = true

```
✅ 代码审查完成：{task_id}
Session: {session_id}
Engine: Open Code Review v1.x

质量门禁结果：
  P0_GATE：✅ PASS（0 个 P0）
  P1_GATE：✅ PASS（2 个 P1 ≤ 3）
  SECURITY_GATE：✅ PASS（0 个 CRITICAL/HIGH）
  ARCH_GATE：✅ PASS（0 个 P0/P1 架构违规）

**deliverable_allowed: true** ✅

审查结果：
  P0: 0 个 ✅
  P1: 2 个（可接受）
  P2: 5 个（建议修复）
  P3: 5 个（优化建议）

**质量裁定：通过** — 可进入交付环节
（源代码审查由质量门禁自动闭合，无需自然人审核确认）
```

### 审查完成 + deliverable_allowed = false

```
⚠️ 代码审查完成：{task_id}
Session: {session_id}
Engine: Open Code Review v1.x

质量门禁结果：
  P0_GATE：❌ FAIL（{n} 个 P0）
  P1_GATE：⚠️ PASS（{n} 个 P1）
  SECURITY_GATE：❌ FAIL（{n} 个 HIGH 安全漏洞）
  ARCH_GATE：✅ PASS

**deliverable_allowed: false** ⛔

阻断原因：
  - P0 缺陷：{n} 个（代码冻结）
  - P1 缺陷：{n} 个
  - 安全漏洞：{n} 个 CRITICAL/HIGH

**质量裁定：阻断** — 存在 P0/安全漏洞，暂缓交付

已通知：
  - PM（质量裁定阻断）
  - Fullstack（立即修复 P0 + 安全漏洞）

审查报告：docs/03开发阶段/{NN}-审查报告/{task_id}_review_report.md
```

---

## 验证步骤

1. [ ] SOURCE_PATCH 前置校验通过（APPROVED 状态）
2. [ ] `which ocr` 返回有效路径
3. [ ] `ocr llm test` 通过
4. [ ] OCR JSON 输出包含 `findings` 数组
5. [ ] 映射后的 defects 包含 P0/P1/P2/P3 分组
6. [ ] **每个缺陷有 PRD 追溯字段（prd_ref）**
7. [ ] **REVIEW_REPORT 包含 deliverable_allowed 字段**
8. [ ] **REVIEW_REPORT_APPROVAL_RECORD 已生成**
9. [ ] 4 个质量门禁有明确的 PASS/FAIL 状态
10. [ ] OCR 未覆盖领域已通过专项 skill 补充

## 常见陷阱

1. **跳过 SOURCE_PATCH 校验**：直接审查未批准的代码
2. **OCR 未安装就调用**：必须先 `npm install -g @alibaba-group/open-code-review`
3. **缺陷无 PRD 追溯**：无法知道缺陷对应哪个 PRD 功能
4. **deliverable_allowed 判定错误**：任何 P0 缺陷都应阻断，不是"基本通过"就行
5. **只依赖 OCR**：OCR 覆盖不了的领域（IDOR、JWT、架构一致性）必须补充分析
6. **Security Gate 漏判**：OCR security/medium 应升为 P1，HIGH 以上应阻断