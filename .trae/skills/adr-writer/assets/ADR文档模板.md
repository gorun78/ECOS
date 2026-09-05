---
id: ADR-{seq}
summary: >
  {一句话描述架构决策内容，包括问题背景、决策要点和结论。}
scope:
  - {范围1}
  - {范围2}
not_for:
  - API implementation
  - Database schema
  - UI implementation
read_when:
  - Architecture decision review
  - Tech stack evaluation
  - Design discussion
related:
  - docs/02设计阶段/02-01架构设计/{doc-name}.md
  - docs/02设计阶段/02-03API设计/{doc-name}.md
status: proposed
---

# ADR文档 | ADR-{序号} | {决策标题} | {status} | {YYYY-MM-DD}

**日期**: {YYYY-MM-DD}
**状态**: {提议中 | 已接受 | 已废弃 | 已替换}
**上下文**: {决策背景和需要解决的问题}
**决策**: {我们决定做什么}
**理由**: {为什么做这个决定}
**后果**: {这个决定带来的正面和负面影响}
**模块归属**: {ARCH_SPEC 模块名称}
**相关制品**: ARCH_SPEC@{hash}, OpenAPI@{hash}

---

## 详细说明

### 背景

{问题的详细描述，为什么这个问题需要解决}

### 选项对比

| 选项 | 优点 | 缺点 | 成本 | 风险 |
|------|------|------|------|------|
| 选项A | ... | ... | ... | ... |
| 选项B | ... | ... | ... | ... |
| 选项C（选择） | ... | ... | ... | ... |

### 决定

{详细的决策描述}

### 影响

**正面影响**：
- {列出正面影响}

**负面影响**：
- {列出负面影响}

### 弃用说明（仅当状态为"已废弃"或"已替换"时）

{说明为什么废弃，以及替代方案是什么}

---

## APPROVAL_RECORD

```json
{
  "artifact": "ADR",
  "name": "ADR-{序号}_{标题}",
  "version": "v{version}",
  "hash": "{内容哈希}",
  "status": "ACCEPTED",
  "approvals": [
    {
      "role": "技术负责人",
      "result": "APPROVED",
      "timestamp": "{timestamp}",
      "conditions": []
    }
  ],
  "gates_passed": ["Gate-1"],
  "deliverable_allowed": true,
  "module_ref": "{ARCH_SPEC 模块名称}",
  "related_artifacts": [
    "ARCH_SPEC@{arch_hash}",
    "OPENAPI@{openapi_hash}"
  ],
  "prev_version": null,
  "next_version": null
}
```

---

## ADR 审批确认门

请确认以下内容：
1. ADR 格式是否完整（序号/标题/日期/状态/上下文/决策/理由/后果）？
2. 选项对比是否充分（至少 2 个选项）？
3. 决策理由是否有说服力（不是"社区流行"这种理由）？
4. 正面和负面影响是否都已列出？
5. 模块归属是否正确（影响哪个 ARCH_SPEC 模块）？
6. ADR 之间是否有引用关系（形成知识网络）？

**审批人**：技术负责人

**批准结果**：
- APPROVED → 状态改为"已接受"，生成 Hash，可作为 ARCH_SPEC 输入
- REJECTED → 打回修改

---

## 验证步骤

1. [ ] L3 工作流：ADR 必须执行（L2 可跳过）
2. [ ] 每条 ADR 有模块归属（指向 ARCH_SPEC 模块）
3. [ ] 每条 ADR 有相关制品引用（ARCH_SPEC, OpenAPI）
4. [ ] ADR 格式完整（上下文/决策/理由/后果）
5. [ ] 选项对比充分（至少 2 个选项）
6. [ ] 技术负责人审批门已通过（Gate-1 APPROVED）
7. [ ] ADR_APPROVAL_RECORD 已生成，deliverable_allowed=true
8. [ ] ADR 索引是最新的

---

## ADR 索引模板（ADR_INDEX.md）

```markdown
# ADR 索引

| 序号 | 标题 | 日期 | 状态 | 模块归属 |
|------|------|------|------|---------|
| ADR-001 | {标题} | {YYYY-MM-DD} | 已接受 | {module} |
| ADR-002 | {标题} | {YYYY-MM-DD} | 已接受 | {module} |

---

## 详细内容

- [ADR-001](./ADR-001_{title}.md)
- [ADR-002](./ADR-002_{title}.md)
```