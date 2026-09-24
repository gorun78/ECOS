# 文档编写 Agent — 规则与模板（含目录规范）

> 来源: 架构治理 | 日期: 2026-09-22 | 责任人: Arch-Reviewer
> 适用: ECOS 项目 `docs/` 全部 Markdown 文档
> 上游规范: `docs/00-架构/ARCHITECTURE-RULES.md` v1.1 + `.trae/rules/架构铁律.md` v1.1
> 本文件定位: **Agent 行为说明书**——告诉 AI/数字员工在 ECOS 项目中如何编写、命名、归类、维护文档
> **目录结构权威约定见 §〇**（按 6 引擎 + 5 service 划分），与 `.trae/rules/文档目录规范.md`（旧 v1.0，14 目录）互补，迁移完成后旧规范升级 v2.0 引用本规范

---

## 〇、文档目录规范（按 6 引擎 + 5 服务层划分）

> 本节是 `docs/` 目录结构的权威约定，与 `.trae/rules/文档目录规范.md`（群目录约定）互补。
> 设计原则：**文档结构镜像系统架构**——每个可部署/可维护的工程单元在 `docs/` 下有一对一目录，文档与代码保持同步演进。

### 0.1 6 个一级目录（对应 6 个服务层部署单元）

| 一级目录 | 对应工程模块 | 端口 | 文档内容 | 典型文件 |
|:--|:--|:--:|:--|:--|
| `10-gateway/` | `gateway/`（顶层） | :8080 | 组织/认证/限流 facade 的设计、路由表、过滤器清单、限流策略 | `routers-map.md` / `filters-checklist.md` / `auth-flow.md` |
| `20-services/` | `services/`（5 service 聚合 + agent-service library） | — | 5 service 的封装设计、跨服务接口矩阵、双跑期配置 | 见 §0.2 二级 |
| `engine-security/` | `engine/security-engine`（护·横切） | :18081 | 认证/授权/审计/脱敏/ABAC 能力设计、OPA 策略、安全接入操作手册 | `security-capability.md` / `05-安全能力操作手册.md` / `abac-policy-design.md` |
| `engine-data/` | `engine/data-engine`（土·D） | :18082 | 数据源/管道/血缘/DQ 设计、数据湖分层、Pipeline 规范 | `pipeline-schema-spec.md` / `databench-datasource.md` / `data-quality-plan.md` |
| `engine-ontology/` | `engine/ontology-engine`（金·I） | :18083 | 本体建模/对象/关系/版本设计、DSL 编译器、KG 同步契约 | `ontology-object-design.md` / `dsl-compiler.md` / `kg-sync-contract.md` |
| `engine-kb/` | `engine/kb-engine`（水·K） | :18086 | KG 存储/检索/RAG/规则 CRUD、抽取设计 | `kb-rag-design.md` / `rule-schema.md` / `extraction-design.md` |
| `engine-cognitive/` | `engine/cognitive-engine`（木·C） | :18086* | 因果推理/情景推演/混合推理、落盘策略（无新增 DB 表） | `causal-reasoning.md` / `scenario-simulation.md` / `persistence-strategy.md` |
| `engine-ai/` | `engine/ai-engine`（火·W）+ `agent-service` | :18084 | Agent/Loop/Memory 设计、LLM 调用封装、Memory 持久化、workflow 闭环 | `agent-loop-design.md` / `llm-gateway-usage.md` / `memory-schema.md` |

> *注：cognitive 与 kb 共享 service 端口 :18086（dccheng 双引擎），目录按引擎独立切分，不合并。

### 0.2 `20-services/` 二级目录（5 服务层部署单元）

| 二级目录 | 对应 service module | 端口 | 文档内容 | 典型文件 |
|:--|:--|:--:|:--|:--|
| `20-services/sysman/` | `services/sysman`（封装 security-engine） | :18081 | IAM/角色/字典/租户、SSO 集成、菜单管理、部署 Runbook | `iam-design.md` / `sso-integration.md` / `rollback-runbook.md` |
| `20-services/datanet/` | `services/datanet`（封装 data-engine + ge 转化） | :18082 | 数据服务业务封装、ge（D→I）转化逻辑、数据湖生产管道 | `ge-transform-design.md` / `data-pipeline-orch.md` |
| `20-services/buszhi/` | `services/buszhi`（封装 ontology-engine + 工作流） | :18083 | 工作流状态机/审批、zhi（I→K）KG 同步、DSL 编译部署 | `workflow-state-machine.md` / `zhi-kg-sync.md` |
| `20-services/dccheng/` | `services/dccheng`（封装 kb + cognitive 双引擎） | :18086 | 知识服务封装、cheng（K→C）推理编排、知识库工作流 | `cheng-reasoning-orch.md` / `kb-workflow.md` |
| `20-services/aiming/` | `services/aiming`（封装 ai-engine + llm-gateway） | :18084 | Agent 服务封装、ming（**C→W** 认知→决策与行动）转化、LLM 调用统一入口（v1.4 修订：原 K→W 降级为"确定性知识驱动自动化"） | `ming-agent-design.md` / `llm-gateway-usage.md` |

### 0.3 跨域/横切目录（按方法论 + 基础设施划分）

| 一级目录 | 划分依据 | 文档内容 |
|:--|:--|:--|
| `workspace/` | 顶层场景应用层（:18090，非 5 service 之一） | 对象运行时/ObjectQL/Scenario/Workbook、跨服务场景编排 |
| `21-runtime/` | runtime 器·横切底座（6 子模块） | `runtime-core` 工具 / `runtime-access` 基础设施 / `runtime-task` 调度 / `runtime-monitor` 监控 / `llm-gateway` 网关 / `common-api` 契约 |
| `00-架构/` | 系统级设计（跨所有 6+5 模块） | 架构宪法、独立 ADR（`adr/` 子目录）、技术选型 |
| `22-integration/` | 跨模块联调 / Wave 收口 | 联调矩阵 / Soak 报告 / 冒烟脚本（`.mjs`） |
| `23-quality/` | 质量 / 产品化 | 交付质量总报告 / 产品化重构方案 / 性能基线 |
| `30-cross-cutting-docs/` | 文档类型横切（跨所有模块） | 审查证据 / PMO 指令 / 行业参考 / 运维手册 |

### 0.4 `30-cross-cutting-docs/` 二级目录（文档类型级）

| 二级目录 | 文档类型 | 说明 |
|:--|:--|:--|
| `reviews/` | 审查证据（task_id 一组，含 JSON） | 与代码 commit 对应，**不按模块拆，按 task 组织** |
| `pmo/` | 跨模块 / 验收类 PMO 指令 | 模块级 PMO 留各模块目录，跨模块归此 |
| `refs/` | 行业参考 / 周报素材 / 竞品 | 维护 `.env` 类的外部素材 |
| `ops/` | 部署 / 回滚 / 清理 / 运行手册 | 全模块共用的运维 Runbook |
| `99-archive/` | 归档（不再引用，不删除） | 历史 Wave / 旧版本 |

### 0.5 完整目录树

```
docs/
├── 00-架构/                        # 架构宪法 + ADR
│   └── adr/                        # 独立 ADR 文件（adr-{NNN}-{title}.md）
├── 10-gateway/                     # 顶层网关
├── 20-services/                    # 5 service 聚合（核心服务层）
│   ├── sysman/                     # :18081  封装 security-engine
│   ├── datanet/                    # :18082  封装 data-engine + ge
│   ├── buszhi/                     # :18083  封装 ontology-engine + 工作流 + zhi
│   ├── dccheng/                    # :18086  封装 kb-engine + cognitive-engine + cheng
│   └── aiming/                     # :18084  封装 ai-engine + llm-gateway + ming
├── 21-runtime/                     # 器·横切底座（6 子模块）
├── 22-integration/                 # 跨模块联调 / Soak / Wave 报告
├── 23-quality/                     # 交付质量 / 产品化
├── engine-security/                # 护·横切（security-engine :18081）
├── engine-data/                    # 土·D（data-engine :18082）
├── engine-ontology/                # 金·I（ontology-engine :18083）
├── engine-kb/                      # 水·K（kb-engine :18086）
├── engine-cognitive/               # 木·C（cognitive-engine，与 kb 共享端口）
├── engine-ai/                      # 火·W（ai-engine + agent-service :18084）
├── workspace/                      # 顶层场景应用层 :18090
└── 30-cross-cutting-docs/          # 文档类型横切
    ├── reviews/                    # 按 task_id 组织
    ├── pmo/                        # 跨模块 PMO
    ├── refs/                       # 行业/竞品/周报
    ├── ops/                        # 运维/部署/回滚
    └── 99-archive/                 # 归档
```

### 0.6 目录与模块的映射铁律

| 铁律 | 说明 |
|:--|:--|
| **目录与代码模块一对一** | `docs/20-services/datanet/` 对应 `ecos_backend/services/datanet/`，不允许一个目录跨多个 module |
| **目录深度 ≤ 3 层** | 一级 + 二级（如有）+ 三级（罕见，需 PMO 授权） |
| **重命名/移动即架构变更** | 目录迁移必须走 ADR + PMO 指令 + `git mv` + 引用更新 |
| **端口代码钉在一级目录注释** | 每个一级目录顶部 `README.md` 写 `> 端口: {端口} / 模块: {module 路径}` |
| **横切目录不拆 service** | `21-runtime/`、`30-cross-cutting-docs/` 不按 service 拆二级 |
| 新引擎/新 service 必须同步开目录 | PMO 指令开新模块时，必须在 `§Task` 含"新建 `docs/engine-{name}/` 目录 + README" |
| **归档不删** | 模块下线时文档不删，移 `30-cross-cutting-docs/99-archive/{模块}-archive-{YYYY-MM}/` |

### 0.7 迁移映射（旧目录 → 新目录，一次性）

| 现存目录 | 新目录 |
|:--|:--|
| `1-sysman/` + `2-aispace/` 中安全相关 | `engine-security/`（护） |
| `2-aispace/`（AI 工作台/Chatbot） | `engine-ai/` + `20-services/aiming/` |
| `3-data/` | `engine-data/` + `20-services/datanet/` |
| `4-onto/` | `engine-ontology/` + `20-services/buszhi/` |
| `5-cognitive/` | `engine-cognitive/` + `20-services/dccheng/` |
| `6-techdebt/`（技术债 PMO 指令） | `30-cross-cutting-docs/pmo/`（跨模块）或对应模块目录（单模块） |
| `7-integration/` | `22-integration/` |
| `08-产品化重构方案/` | `23-quality/` |
| `09-PMO指令/`（跨模块 PMO）+ `PMO/` | `30-cross-cutting-docs/pmo/` |
| `10-审查证据/` + `reviews/` + `03开发阶段/` + `07项目管理/` + `swarm/` | `30-cross-cutting-docs/reviews/` |
| `11-运维/` | `30-cross-cutting-docs/ops/` |
| `9-checks/` + `24-交付质量报告-*.md` | `23-quality/` |
| `12-参考资料/`（不存在，散文档） | `30-cross-cutting-docs/refs/` |
| `99-归档/`（不存在） | `30-cross-cutting-docs/99-archive/` |
| docs 根散落 5 文件 | 按内容归类到上面对应的二级目录 |

### 0.8 与 `.trae/rules/文档目录规范.md` 的关系

- 旧规范（v1.0）的 `00-架构 / 01-系统管理 / 02-智能空间 / 03-数据 / 04-本体 / 05-认知 / 06-技术债 / 07-集成联调 / 08-产品质量 / 09-PMO指令 / 10-审查证据 / 11-运维 / 12-参考资料 / 99-归档` 共 14 目录为**过渡期**目录（已存在大量历史文档）
- 本规范（§〇）定义的 6 引擎 + 5 service + 横切目录为**目标态**目录
- **迁移策略**：新文档一律按 §0.5 目标态目录编写；存量文档按 §0.7 映射表一次性 `git mv` 批量迁移（需 PMO 指令授权 + commit 留痕，见 R13）
- 迁移完成后，`.trae/rules/文档目录规范.md` 升级到 v2.0，引用本规范

### 0.9 一次性 `git mv` 迁移（PMO 批准后执行）

**迁移脚本**：`_win_tasks/docs-migration.sh`（bash，Windows 上通过 Git Bash 执行；铁律"脚本只增不改"已遵守——新脚本归入 `_win_tasks/`，与 4 核心脚本并列）

**执行前置条件**（脚本自检 `set -euo pipefail` 强制）：

| # | 条件 | 验证命令 |
|:--:|:--|:--|
| 1 | 工作树干净 | `git status` 无 modified / untracked 垃圾 |
| 2 | 当前在 feature/docs-migration 分支（非 dev / main） | `git branch --show-current` |
| 3 | PMO 指令 `#PMO-{X}-docs-目录迁移` 已批准 | 检查批准记录 |
| 4 | 已跑 dry-run（dry-run 模式不实际 mv，只打印） | `bash _win_tasks/docs-migration.sh --dry-run`（需支持） |

**执行后验收**（codereview 必查）：

| # | 验收 | 命令 | 预期 |
|:--:|:--|:--|:--|
| 1 | 有 commit | `git log --oneline -1` | `refactor(docs): 目录一次迁移...` |
| 2 | 工作树干净 | `git status` | clean |
| 3 | 旧路径无残留 | `grep -rn "docs/1-sysman\|docs/7-integration" docs/ --include="*.md" \| wc -l` | 0 |
| 4 | 后端可编译 | `mvn -f ecos_backend/pom.xml validate -q` | 0 |
| 5 | 前端可编译 | `cd ecos_frontend; npm run lint` | 0 |
| 6 | 14 旧目录已消失 | `ls docs/ \| grep -E "^[0-9]" \| wc -l` | 只剩 `00-架构/`、`10-gateway/`、`20-services/`、`21-runtime/`、`22-integration/`、`23-quality/`、`30-cross-cutting-docs/` 7 个 |

**迁移规则**：
1. 旧目录改名为 `{原名}-legacy/` 落到新目录（不删，符合 R9 "只加不删"）
2. 每个目标态一级目录建 `README.md`（脚本内置，6 个引擎 + workspace + 10-gateway 已生成）
3. 迁移完成后跑 `grep -r "docs/plans\|docs/7-integration\|docs/ARCHITECTURE-RULES\.md"` 全文更新引用（手动，约 12 处）
4. 完成后 `.trae/rules/文档目录规范.md` 升级 v2.0（移除 14 个过渡期目录，指向本规范 §〇）

---

## 一、红线（违反 = Reviewer 判 FAIL）

| # | 红线 | 后果 |
|:--|:--|:--|
| R1 | 文档头必须 3 行元信息：`> 来源 / > 日期 / > 责任人` | 缺任一 → 拒收 |
| R2 | 文件名禁全中文 / 大写英文单词 / 下划线 `_` / 空格 / `+` / `/` | 命名违规 → 移目录或重命名 |
| R3 | PMO 指令头部必须引用 `docs/00-架构/ARCHITECTURE-RULES.md`（相对路径） | 缺引用 → 拒收（铁律 §7） |
| R4 | 验收记录必须含 `commit_hash`（Git 凭证铁律） | 缺 hash → 不算交付 |
| R5 | 文件一律 `.md` 后缀，**禁止** `.png`/`.jpg`/`.pdf` 入 `docs/`（图片走外部 CDN，PDF 不入库） | 类型违规 → 拒收 |
| R6 | 子目录深度 ≤ 3 层（相对 `docs/`），且必须符合 §0.5 目标态目录树 | 过深 / 越界 → 拒收 |
| R7 | 引用代码/文档路径必须可解析（`grep` 命中），禁用占位符 `<TODO>` / `<TBD>` / `<待定>` | 占位符 → 拒收 |
| R8 | 删除 `docs/` 下文档**必须 PMO 指令授权** + `git rm` + `git log` 留痕 | 未授权 → 回滚 |
| R9 | 文档只加不删（与 DB Schema 同语义），重命名/移动需同步更新所有引用链接 | 引用失效 → 拒收 |
| R10 | 含密钥/密码/明文凭据的文档**禁止入 git**，敏感配置走 `.env` 或 `.env.example` | 泄露 → 升级 P0 |
| **R11** | **新文档必须落入 §0.5 目标态目录**（不在 14 个过渡期目录新建文件） | 越界 → 拒收 |
| **R12** | **新建 PMO 指令开新模块时**，`§Task` 必须包含"新建 `docs/engine-{name}/` 或 `docs/services/` 下二级目录 + README"任务 | 缺目录 → 拒收 |
| **R13** | **新旧目录过渡期**：`docs/` 下新旧目录并存，旧目录（14 个过渡期 `1-sysman` ~ `99-归档`）只读不新增；新文档一律进新目录；**一次性 `git mv` 批量迁移在 PMO 指令 #PMO-{X}-目录迁移 批准后执行，禁止零散迁移** | 零散迁移 → 回滚 |

---

## 二、文档类型清单与模板

> 每类按「头部 + 推荐章节 + 代表性样例」给出。模板中的 `{}` 占位符必须替换为实际内容。

### 2.1 PMO 指令（最高频类型，120+ 份）

**头部**（铁律 §7 模板强制）：

```markdown
# PMO-{编号}: {标题}

> **架构铁律**: 必须遵循 [ECOS架构铁律](../00-架构/ARCHITECTURE-RULES.md)
> 来源: {姓名 or PM 代号} | 日期: YYYY-MM-DD
> 铁律: {本指令特有的 ≤3 条硬约束}
```

**章节结构**：

```markdown
## §背景   ← 必填。要解决什么问题、为什么现在做
## §禁止清单   ← 必填。继承铁律 §5.1 + 本指令特有的禁止项
## §Task   ← 必填，≤ 5 个 task。表格格式见下
## §环境要点   ← 可选。端口/profile/JWT 等
## §验收   ← 必填。curl 命令 + 预期响应 + psql 验证语句
```

**Task 表格**（铁律 §5.2 原子任务格式）：

```markdown
| Task | 文件/路径 | 操作 | 验收 |
|:--|------|------|------|
| T1 | `{绝对路径}` | {重写/新增/修改} {目标} | `curl POST /api/...` 返回 ≥X 条 |
| T2 | ... | ... | ... |
```

**命名**：`pmo-{NN}-{简明中文标题}.md`（如 `pmo-05-登录安全+内置角色.md`）
**归档**：模块级 PMO 留对应模块目录（`engine-{name}/` 或 `20-services/{xxx}/`）；跨模块/验收类归 `30-cross-cutting-docs/pmo/`

**参考样例**：[09-PMO指令/PMO-59-认知引擎心智层-Phase2指令.md](09-PMO指令/PMO-59-认知引擎心智层-Phase2指令.md)

---

### 2.2 PMO 验收记录（1:1 配对，commit 凭证）

**头部**：

```markdown
# PMO-{编号}-{Phase/批次} 验收记录

> 对应指令: [pmo-{NN}-{标题}](同目录路径)
> 责任人: {fullstack-implementer / reviewer / qa}
> 日期: YYYY-MM-DD
> commit: {git-hash-8位}
> 分支: {feature/xxx 或 dev}
```

**章节结构**：

```markdown
## 0. 结论（✅ 通过 / ❌ 驳回 / ⏸ 中止）
## 1. Task 完成情况   ← 与指令 §Task 一一对应
## 2. curl 原文与响应   ← 真实粘贴，禁改写
## 3. 数据库验证   ← psql 摘要（行数/关键字段）
## 4. Reviewer 结论   ← deliverable_allowed: true/false + P0/P1/P2 计数
## 5. 遗留与回滚   ← 如有
```

**命名**：`pmo-{NN}-{PhaseN}-验收记录.md`
**归档**：与对应指令同目录

---

### 2.3 ADR 架构决策记录

**两种落地方式**（按重要性选）：

**方式 A（轻量，嵌入改造方案）**：在改造方案 `§2 关键架构决策（ADR）` 章节内按 ADR-N 编号列出。

```markdown
### ADR-{N}: {标题}

**状态**: {已采纳 / 已否决 / 已撤销} | **日期**: YYYY-MM-DD
**决策者**: {肖国荣 / 技术负责人}

**背景**: 1~3 句。要解决什么问题
**决策**: 1~3 句。最终选了什么方案
**理由**: 1~3 句。为什么选 A 不选 B
**后果**: 1~3 句。正面 + 负面
**备选方案（否定）**: 简述 + 否决原因
```

**方式 B（重量，独立 ADR 文件）**：新增 ADR 且被多方引用时，落到 `docs/00-架构/adr/adr-{NNN}-{title}.md`。

```markdown
# ADR-{NNN}: {标题}

> 状态: {已采纳/已否决/已撤销} | 日期: YYYY-MM-DD | 决策者: {姓名}
> 追溯: {对应 PRD / PMO / 改造方案章节}

## 背景
## 决策
## 理由
## 后果
## 备选方案（否定）
## 关联文档
```

**命名（独立文件）**：`adr-{NNN}-{小写短横线英文}.md`（NNN 三位连续递增）
**归档**：`docs/00-架构/adr/`

**参考样例**：[11-运维/current-plan.md §2](11-运维/current-plan.md)（ADR-1 ~ ADR-9，方式 A）

---

### 2.4 审查报告（Reviewer 输出）

**头部**（两种风格并存，选其一）：

```markdown
# REVIEW REPORT — {被审对象}

> 被审对象: {task_id / pmo-{NN} / {分支名}}
> 审查者: t_{profile}
> 审查基线: {commit-hash 或 PRD 版本}
> 审查时间: YYYY-MM-DD
> deliverable_allowed: {true / false}
> P0: {n} / P1: {n} / P2: {n} / P3: {n}
```

**章节结构**：

```markdown
## 0. 总判定表
| 维度 | 判定 |
|:--|:--:|
| P0（阻断） | {0 或 n} |
| P1（立即修） | {0 或 n} |
| P2（延后） | {0 或 n} |
| 架构铁律 6 大域 | {全过 / N 项违规} |
| **deliverable_allowed** | **✅ YES / ❌ NO** |

## 1. P0 / P1 必修项   ← 含：文件路径 + 行号 + 修复建议
## 2. 架构铁律抽查   ← 6 大域：分层/安全/编译/前端/文档/性能
## 3. 证据   ← 命令 + 输出（curl / grep / mvn）
## 4. 结论与交付判定
```

**命名**：`review-report-{对象简称}-{YYYY-MM-DD}.md`
**归档**：`docs/30-cross-cutting-docs/reviews/{task_id}/`
**配套 JSON**（可选但推荐）：`review-approval-record.json` 含 `deliverable_allowed` / `commit_hash`

---

### 2.5 审查/执行证据（验收留痕）

```markdown
# {PMO-NN / task} 执行证据 — {全闭环 / 中止 / 部分}

> 来源: {fullstack-implementer / qa}
> 日期: YYYY-MM-DD
> 对应指令: [pmo-{NN}](同目录)
> 分支: {feature/xxx}
> commit: {git-hash}
```

**章节**：

```markdown
## 1. 执行步骤表（T1~Tn 一一对应）
## 2. curl 原文与响应
## 3. DB 变更（psql 摘要）
## 4. 构建产物（mvn / npm 输出摘要）
## 5. 异常与回滚
```

**命名**：`pmo-{NN}-execution-evidence.md` / `pmo-{NN}-abort-evidence.md`
**归档**：`docs/30-cross-cutting-docs/reviews/{子目录}/`

---

### 2.6 操作手册 / Runbook

```markdown
# {对象} 操作手册 / {标题} Runbook

> 来源: {owner}
> 日期: YYYY-MM-DD
> 责任人: {技术负责人}
> 状态: {在用 / 废弃}
> 关联铁律: {§X.X}
```

**章节**（按场景）：

```markdown
## 1. 前置条件   ← 依赖服务/端口/凭据
## 2. 操作步骤   ← 编号步骤 + 命令
## 3. 预期结果   ← 成功标志
## 4. 失败排查   ← 常见错误 + 排查命令
## 5. 回滚方案   ← 回退步骤 + 回滚完成后验证
## 6. 相关文档
```

**典型位置**：
- 安全能力手册 → `engine-security/05-安全能力操作手册.md`（铁律 §2.4 引用）
- 部署/回滚 Runbook → `20-services/{对应 service}/rollback-runbook.md` 或 `30-cross-cutting-docs/ops/`
- 清理清单 → `30-cross-cutting-docs/ops/`
- 接口契约 → `11-运维/api-contract.md`（旧位置，新位置待迁移到 `30-cross-cutting-docs/ops/`）

**命名**：小写英文短横线，如 `05-安全能力操作手册.md`（保留编号）、`rollback-runbook.md`

**参考样例**：[07-集成联调/02-security/05-安全能力操作手册.md](07-集成联调/02-security/05-安全能力操作手册.md)

---

### 2.7 联调 / Soak 报告

```markdown
# Wave-{N}-{联调/Soak} 段{X} 报告

> 版本: {1.0} | 日期: YYYY-MM-DD | 分支: {dev | feature/xxx}
> 范围: {哪些模块/端点}
> 前置: {前置 Wave 验收 commit}
```

**章节**：

```markdown
## 0. 总体判定  ✅ / ⚠️ / ❌
## 1. 联调矩阵  | 模块对 | 调用方式 | 预期 | 实际 | 判定 |
## 2. 关键调用链  ← 含 curl 与响应摘要
## 3. 性能指标（Soak 类必填）   ← CPU / 内存 / 延迟 P95 / GC
## 4. 遗留问题  P1/P2 清单
## 5. 后续计划
```

**命名**：`wave{N}-segment-{X}-联调-YYYY-MM-DD.md`
**归档**：`docs/22-integration/{域子目录}/`

---

### 2.8 检查/扫描报告

```markdown
# {检查对象} 检查报告

> 检查人: {姓名 / 代号}
> 日期: YYYY-MM-DD
> 范围: {仓库 / 模块 / 前端页面}
> 口径依据: {铁律 §X / 规范 §Y}
```

**章节**：

```markdown
## 0. 总览  | 维度 | 完整度 | 评价 |
## 1. 致命问题（❌）
## 2. 各路细节
## 3. 改进建议
```

**命名**：`YYYY-MM-DD-{检查对象}-检查报告.md`
**归档**：跨模块的检查归 `23-quality/`；单模块的检查归对应模块目录

---

### 2.9 测试方案 / 用例 / 报告

**方案**：

```markdown
# {对象} 测试方案

> 来源: QA | 日期: YYYY-MM-DD | 责任人: qa
> 关联 PRD: {路径}
```

```markdown
## 1. 测试范围
## 2. 测试策略   ← 单测/集成/E2E 比例
## 3. 用例清单   ← 表格：ID | 场景 | 前置 | 步骤 | 预期 | 优先级
## 4. 测试数据   ← 脱敏后的测试账号/数据
```

**报告**（执行后）：

```markdown
## 0. 通过率  通过 n / 失败 m / 跳过 k
## 1. 失败用例详情   ← 用例 ID + 失败原因 + 截图链接（如有）
## 2. 缺陷清单   ← BUG_LIST，每个 bug 必须追溯 PRD 来源
## 3. 风险与建议
```

**命名**：`02-测试方案.md` / `03-测试用例.md` / `04-测试报告.md`（同目录按序号）
**归档**：`docs/07-集成联调/{域子目录}/`

---

### 2.10 差距分析（Phase 启动前）

```markdown
# {模块} 差距分析

> 来源: {PM / 架构师} | 日期: YYYY-MM-DD | 责任人: {姓名}
> 基线: {commit-hash 或版本}
```

**章节**：

```markdown
## 1. 现状盘点（已有能力的代码证据）
## 2. 目标态（PRD 接受标准）
## 3. 差距清单  | # | 功能点 | 预期 | 实际 | 差距 | 优先级 |
## 4. 风险与未知项
## 5. 完善计划（进入 PMO 指令的前置）
```

**命名**：`01-差距分析.md`（统一序号 01）
**归档**：`NN-engine-{xxx}/` 或 `20-services/{xxx}/`（一模块一份）

---

### 2.11 改造方案 / 技术方案（重文档）

```markdown
# {主题} 改造方案 / 技术方案

> **架构铁律**: 必须遵循 [ARCHITECTURE-RULES](../00-架构/ARCHITECTURE-RULES.md)
> 日期: YYYY-MM-DD | 版本: vX.Y
> 责任人: {架构师}
> 变更记录: vX.Y → vX.Y+1: {变更点}
```

**章节**（重文档固定 6+ 章）：

```markdown
## 1. 执行摘要   ← 改造目标 + 改造前后对比表
## 2. 关键架构决策（ADR）
## 3. 协作关系   ← 上下游调用/数据流 mermaid
## 4. 非功能需求（NFR）   ← 性能/可用/扩展/安全
## 5. 实施计划   ← Phase 划分 + 里程碑
## 6. 开放问题（O1~On）
## 7. 风险矩阵   ← 风险 × 概率 × 影响 + 缓解
## 8. 破除铁律清单   ← 若改造涉及铁律修订，必须显式列出
## 9. 变更记录
## 附录 A: 目标模块结构
```

**命名**：`{主题}-改造方案.md` / `{主题}-replan-v{N}.md`
**归档**：改造方案 / 技术契约归 `00-架构/`（重文档）或 `30-cross-cutting-docs/ops/`（运行类）；产品化总纲归 `23-quality/`

**参考样例**：[11-运维/current-plan.md](11-运维/current-plan.md) v1.5

---

### 2.12 PRD（产品需求文档）

```markdown
# {功能/模块} PRD

> 来源: {PM 姓名} | 日期: YYYY-MM-DD | 状态: {草稿/评审中/通过}
> 关联 PRD: {若有上游}
> 验收标准: 见 §6
```

**章节**（L3 工作流必选）：

```markdown
## 1. 背景与目标   ← 用户痛点 + 业务价值
## 2. 用户故事   ← "作为 X，我想 Y，以便 Z"
## 3. 功能清单   ← R1, R2, R3...
## 4. 非功能需求   ← 性能/安全/兼容
## 5. 边界与异常   ← 显式列出"不做"
## 6. 验收标准   ← 可测试（Given-When-Then）
## 7. 数据需求   ← 新增/修改的实体
## 8. 开放问题
```

**命名**：`prd-{功能名}-v{N}.md`
**归档**：模块级 PRD 留对应模块目录（`NN-engine-{xxx}/` 或 `20-services/{xxx}/`）；跨模块 PRD 归 `30-cross-cutting-docs/pmo/`（与对应 PMO 同目录）
**参考样例**：[plans/knowledge-import-enhance-prd.md](11-运维/knowledge-import-enhance-prd.md)（待迁移）

---

### 2.13 归口 README（目录入口）

```markdown
# {目录名} 入口

> 入口文档 · {生成日期}
> 责任人: {PM / 架构师}
> 总周期: {N 周}

## 文档地图
| 序号 | 文档 | 状态 | 摘要 |
|:--:|:--|:--:|:--|

## 关键事实
## 立即行动
## 引用文档
## 更新优先级
```

**命名**：统一 `README.md`（唯一可大写 README 例外）
**归档**：每个一级目录一份（仅需在内容多时建）

---

### 2.14 行业参考 / 周报素材

```markdown
# {素材标题}

> 来源: {公众号 / 论文 / 内部分享}
> 日期: YYYY-MM-DD
> 标注: 🔖值得跟进 ✅已验证 ⚡高优先级
```

**章节**：

```markdown
## 0. 摘要（3~5 行干货）
## 1. 关键条目   ← 按 🔖✅⚡ 标记
## 2. 我方应用建议
## 3. 原始链接
```

**命名**：`{主题}-周报素材-{YYYY-MM-DD}.md`
**归档**：`30-cross-cutting-docs/refs/`

---

### 2.15 迁移记录

```markdown
# {迁移主题} 完成报告

> 来源: {owner} | 日期: YYYY-MM-DD | 责任人: {姓名}
> 状态: ✅ 完成 / ⏸ 部分
```

**章节**：

```markdown
## 1. 迁移范围
## 2. 关键路径/环境变量
## 3. 验证清单   ← 迁移后立即执行的检查
## 4. 回滚步骤
```

**命名**：`{迁移主题}-完成报告-{YYYY-MM-DD}.md`
**归档**：`30-cross-cutting-docs/ops/`

---

## 三、命名规范（速查表）

| 规则 | 说明 | 示例 ✅ | 反例 ❌ |
|:--|:--|:--|:--|
| **小写 + 短横线** | 主规则 | `pmo-05-登录安全.md` | `PMO-05_登录安全.md` |
| **数字前缀两位数** | 一级目录 | `09-PMO指令/` | `9-checks/` |
| **日期格式** | 报告/交付类必带 | `-2026-09-03` | `-20260903` / `_20260908` |
| **README 唯一例外** | 入口文档可大写 | `README.md` | `Readme.md` |
| **PMO 编号** | 带 `PMO-{NN}-` 前缀 | `PMO-48-数据质量.md` | `数据质量-PMO48.md` |
| **task_id** | 审查类带 `t_xxxx` | `t_fdf943ce_review-report.md` | `review-t_fdf943ce.md` |
| **版本号** | 带 `-v{N}` 后缀 | `replan-v1.md` | `replan_v1_v2.md` |
| **ADR** | `adr-{NNN}-{title}` NNN 三位 | `adr-001-rust-adopt.md` | `ADR1-rust.md` |
| **禁字符** | `+` / 空格 / `_` / `?` / `&` | — | `登录安全+内置角色` |

**中文标题允许**：`pmo-05-登录安全.md` 中 `登录安全` 是中文，合规。

---

## 四、文档生命周期与处置

| 阶段 | 动作 | 责任人 | 状态标记 |
|:--|:--|:--|:--|
| **起草** | 新建文件，写完整头部 3 行元信息 | 起草人 | 无 |
| **评审** | Reviewer 出 `REVIEW_REPORT` | reviewer | `> 评审: t_xxxx` |
| **批准** | 技术负责人 + 产品负责人双签 | 决策者 | `> 批准: {姓名} {日期}` |
| **使用** | 被引用时同步版本号/哈希校验 | 所有引用方 | — |
| **变更** | 加变更记录节 + 版本号递增（`v1.0 → v1.1`） | 变更人 | `## 变更记录` |
| **废弃** | 头部加 `> ⚠️ 已废弃 YYYY-MM-DD，新文档见 {路径}` | 废弃人 | — |
| **归档** | 移 `99-归档/`（不删除） | 文档园丁 | 归档头 |

**版本号语义**：
- `v1.0` 首版正式批准
- `v1.N` 增量修订（不破坏性）
- `vN.0` 破坏性变更（目录迁移/章节重排/ADR 撤销）

---

## 五、目录归类决策树（按 §〇 目标态目录）

```
新文档要放哪？
├─ 架构决策 / ADR？ → 00-架构/（独立 ADR 进 00-架构/adr/）
├─ 横切底座 / 基础设施 / LLM 网关？ → 21-runtime/
├─ 网关 / 路由 / 限流设计？ → 10-gateway/
├─ 顶层场景应用层（Object/Scenario/Workbook）？ → workspace/
├─ 某个 engine 能力设计？ → engine-{security,data,ontology,kb,cognitive,ai}/
├─ 某个 service 业务封装？ → 20-services/{sysman,datanet,buszhi,dccheng,aiming}/
├─ PMO 指令？
│   ├─ 仅模块专属（单 engine 或单 service）？ → 对应 NN-engine-xxx/ 或 20-services/xxx/
│   └─ 跨模块 / 验收 / 验收门禁？ → 30-cross-cutting-docs/pmo/
├─ 跨模块联调 / Wave 收口 / Soak？ → 22-integration/
├─ 交付质量 / 产品化方案 / 性能基线？ → 23-quality/
├─ 审查 / 验收 / 证据（含 JSON）？ → 30-cross-cutting-docs/reviews/<task_id>/
├─ 部署 / 回滚 / 运维 Runbook / 迁移记录？ → 30-cross-cutting-docs/ops/
├─ 行业参考 / 周报素材 / 竞品？ → 30-cross-cutting-docs/refs/
├─ 测试方案 / 用例 / 报告（单模块非联调）？ → 对应模块目录（NN-engine-xxx/ 或 20-services/xxx/）
└─ 已完成不再引用？ → 30-cross-cutting-docs/99-archive/{模块}-archive-{YYYY-MM}/
```

**冲突处理**：一份文档归"主要意图"唯一目录，避免双重存放。模块级文档优先入模块目录；跨模块文档入横切目录。

---

## 六、文档间引用规范

### 6.1 相对路径优先级

```
1. 同级相对：    ../09-PMO指令/pmo-59-xxx.md
2. 从 docs 根：  09-PMO指令/pmo-59-xxx.md（适合跨目录引用）
3. 绝对路径：    file:///D:/workspace/...（仅 IDE 跳转，不推荐）
```

**禁用**：`../..` 超过三层、相对 Windows 盘符路径（`D:\`）、URL 未编码含中文的路径

### 6.2 引用必须可解析

- Agent 写引用后必须 `grep`/`glob` 校验文件存在
- 被引用文档若重命名/移动，必须同步更新所有引用方（文档园丁职责）

### 6.3 跨文档引用格式

```markdown
- 架构铁律 §2.4（安全接入）：见 [.trae/rules/架构铁律.md](.trae/rules/架构铁律.md) §2.4
- 当前改造方案 ADR-9：见 [11-运维/current-plan.md](11-运维/current-plan.md) §2 ADR-9
```

---

## 七、元信息头模板（速查）

```markdown
# {H1 标题}

> 来源: {姓名 or 角色 or PMO}
> 日期: YYYY-MM-DD
> 责任人: {fullstack-implementer / reviewer / qa / arch / pmo}
> (可选) 铁律: 继承 [架构铁律](...) §N 条
> (可选) 关联 PRD / 改造方案 / PMO：
```

**必填 3 行**：来源 + 日期 + 责任人。PMO/改造方案/ADR 必加"铁律"行。

---

## 八、Agent 工作流（代码与文档联动）

### 8.1 触发条件

| 条件 | 动作 |
|:--|:--|
| 新增 Controller / 新 Service | 检查是否需要更新 `11-运维/api-contract.md` |
| 新增/修改 PMO 指令 | 按 §2.1 模板建文件，必须执行 §2.2 验收记录 |
| 改造方案变更 | 更新 `## 变更记录` + 版本号 +1 |
| ADR 新增/撤销 | 落到 `00-架构/adr/`（独立）或更新改造方案 §2（嵌入） |
| 代码与文档不一致 | 文档园丁扫描，按"谁变更谁同步"原则修复 |

### 8.2 自检清单（提交前）

- [ ] 头部 3 行元信息齐全
- [ ] 文件名合规（小写+短横线+日期格式正确）
- [ ] 归类目录正确（按 §5 决策树）
- [ ] 子目录深度 ≤ 3 层
- [ ] 所有引用路径可解析（无 `<TODO>` / `<TBD>`）
- [ ] PMO 有对应验收记录（commit_hash 留痕）
- [ ] 无密钥/密码明文
- [ ] 修订文档有版本号递增

### 8.3 与代码层协同

- 新增功能 → 先 PRD（如有）→ 再 PMO 指令 → 再开发 → 再验收记录
- 架构决策 → ADR（独立或嵌入）→ 同步更新改造方案与铁律
- 文档第一人制：谁改代码谁更新文档（PR 必须包含文档 diff）

---

## 九、违规处置

| 违规类型 | 处置 |
|:--|:--|
| 缺头部元信息 | Reviewer 拒收，打回起草人 |
| 命名违规 | 通知起草人名，30 天内整改（迁移 + 改引用） |
| 关键字段缺失（commit_hash / 验收） | 不算 DONE（Git 规约：working tree 未提交不算交付） |
| 路径失效 / 引用悬空 | 文档园丁列入清理清单，下次 Sprint 修复 |
| 密钥/密码泄露 | 立即 P0，回滚 + 凭据轮换 |
| 越权删除 | 回滚 + 审计日志留痕 |

---

## 十、本规范与现有规则的边界

| 文件 | 职责 | 边界 |
|:--|:--|:--|
| `.trae/rules/文档目录规范.md` v1.0 | 目录结构 + 文件命名（**结构层面**） | 管"放哪里" |
| `.trae/rules/架构铁律.md` v1.1 | 代码层红线 + PMO 模板铁律（**行为层面**） | 管"必须引用铁律" |
| **本文件** | 文档类型模板 + 生命周期 + 引用规范（**内容层面**） | 管"怎么写" |
| `docs/00-架构/ARCHITECTURE-RULES.md` v1.1 | 架构宪法（系统层事实） | 管"系统长什么样" |

**冲突时优先级**：文档目录规范 > 架构铁律 > 本文件 > 单文档自述

---

## 附：文档类型速查表（Agent 入口）

| 我在写…… | 用哪个模板 |
|:--|:--|
| 给开发的下任务指令 | §2.1 PMO 指令 |
| 任务做完要留凭证 | §2.2 PMO 验收记录 |
| 技术选型/架构拍板 | §2.3 ADR |
| 上线前/合并前审查 | §2.4 审查报告 |
| 测试证明 work 了 | §2.5 执行证据 |
| 给人看的 how-to | §2.6 操作手册 / Runbook |
| 多模块跑通了 | §2.7 联调 / Soak 报告 |
| 质量摸底 | §2.8 检查 / 扫描报告 |
| 测自动化前的设计 | §2.9 测试方案 / 用例 |
| Sprint 启动前现状摸底 | §2.10 差距分析 |
| 大改造/大重构计划 | §2.11 改造方案 |
| 新功能/新模块需求 | §2.12 PRD |
| 新一级目录的入口 | §2.13 归口 README |
| 行业/竞品/周报 | §2.14 行业参考 |
| WSL→Windows / 环境迁移 | §2.15 迁移记录 |

---

> 本规范由 Agent 协作生成，需 PMO 批准后纳入 `.trae/rules/` 作为永久规范。
> 修订需更新版本号（当前 v0.9 草案），重大修订走 PMO 流程。
