# ADR-21 — ECOS CLI 控制面与契约同源（含"仓内无机器可读 API 契约"缺口）

> 来源: W-Agent落地检视报告 WC-06/WC-14（附件第二册 §19 统一控制面 9 命令族、第三册 D-11 契约同源） | 日期: 2026-09-29 | 责任人: AI Agent（架构治理）
> 版本: v1.1
> 裁决: **Accepted**（2026-09-29 用户批量批准，凭证 = 需求检视报告 §十四 14.1 之 **R-38**：载体按推荐项 = 仓根 `tools/ecos-cli/` 独立工具；其中"技术栈选型"与"是否本期建设"两项仍随 **R-38 推荐档 + R-50/P-5** 落地，未经 P-5 闭环不得开工）
> 上游: 架构铁律 v2.0 :292（不新增 Maven 模块 / 不新增容器）、ADR-10（对外访问只经 gateway）、Q4 裁决（独立工具落仓根 `tools/`，先例 `tools/ecos-mcp-server.py`）、铁律 :530（ABAC）、ST06（审计走 Kafka）、AGENTS.md「API 只增不改」
> 状态: **Accepted** — 前置件 **P-5（机器可读契约）** 未闭环前，CLI 仍不得开工（R-50 推荐项 ① 已批准，P-5 落地属文档/契约制品层；CLI 代码实现需逐项再授权，报告 §14.4）

---

## 1. 背景（Context）

### 1.1 附件主张

附件第二册 §19 定义"ECOS CLI 统一控制面"9 命令族（含 `agent run/status/cancel`、`candidate list/approve/publish`、`model route` 等），并要求：L3 操作必须带 `--approval-token`、禁直连库表、审计标记 `client=cli`。第三册 D-11 进一步要求 **CLI 与 API/Tool 契约同源**（不得独立实现逻辑）。

### 1.2 ECOS 实测

| # | 事实 | 证据 |
|:--|:--|:--|
| ① | **仓内无 CLI 工程**，也无任何 CLI 入口脚本 | `find` 全仓无 `bin/`、无 commander/click/argparse 工程 |
| ② | 仓根 `tools/` 存在且只放独立工具，唯一实体 `tools/ecos-mcp-server.py`（Q4 裁决落位） | `ls tools/` |
| ③ | **仓内不存在任何机器可读 API 契约文件**（OpenAPI/AsyncAPI 均无）；`docs/` 下 `*.yaml`/`*.yml` 实测 **0 个**；"OpenAPI" 仅以文字形式出现在 ARCH_SPEC 与 9 份 PRD/分册中 | `find docs -iname "*.yaml" -o -iname "*.yml"` → 空；`grep -rl "OpenAPI" docs/` |
| ④ | 接口设计目前只以分册 §D 章的 Markdown 表格存在（人读，不可生成） | `docs/30-设计/详细设计-0{0..7}-*.md`、`详细设计-10-*.md` |
| ⑤ | 后端已有 springdoc 类依赖与否**未实测**（不据未实测下结论） | — |

**后果**：附件 D-11 的"契约同源"在 ECOS **当前无源可同**——这不是 CLI 局部问题，而是平台级缺口（追溯链 `PRD → ARCH_SPEC → OpenAPI → DDL` 在 OpenAPI 这一环断裂）。登记为 ARCH_SPEC **C259**。

## 2. 决策（Decision）

### 2.1 载体（随 R-38）

- 落 **`tools/ecos-cli/`** 独立目录（与 `tools/ecos-mcp-server.py` 同级，Q4 先例），**不新建 Maven 模块**（:292，与 R-37 同批语境）、**不新建 Docker 容器**。
- 技术栈三选一由用户裁：
  | 档 | 栈 | 优点 | 代价 |
  |:--|:--|:--|:--|
  | **A（推荐）** | **Node.js + TypeScript** | 与前端同栈（复用 `ecos_frontend` 的类型与 toolchain），无新运行时（Node 已是必备环境） | 与后端类型无法直接共享 |
  | B | Java（打成独立 jar，不进 reactor） | 与后端同语言 | 易被误加进基线；启动重 |
  | C | Python | 与 `ecos-mcp-server.py` 同栈 | 引入第二套包管理，仓内无 Python 工程约定 |
- **是否本期建设**亦由用户裁：附件把 CLI 排在 P-4 之后；若 R-38 裁"推迟"，则分册 10 §十九 对应需求条目改为"暂缓"并显式登记缺口，**不得留空**。

### 2.2 契约同源（前置件 P-5，M0 判定）

1. **先有机器可读契约**：从各分册 §D 章产出 `docs/10-架构/api-contracts/openapi/*.yaml`（按域分文件），并把"契约 ↔ 分册 §D ↔ 代码 Controller"三者一致性纳入门禁。此项独立于 CLI 是否建设，属平台级 **P-5**。
2. CLI 的每个命令必须映射到一个 OpenAPI operation（`operationId` 记入命令元数据），**禁止手写 URL/参数**；命令树由契约生成或校验。
3. 后端 Controller 若已挂 springdoc，则以运行时 `/v3/api-docs` 作为契约校验源之一；**该点尚未实测**，落地前须先做只读验证再写需求。

### 2.3 访问路径与安全（不可协商项）

| 规则 | 依据 |
|:--|:--|
| CLI **只经 gateway（:8080）** 发 HTTP，禁直连任何 service 端口、禁直连数据库 | ADR-10、铁律「对外访问收敛」 |
| 凭据只从环境/凭据文件读取，**永不回显**；日志与错误信息不得打印 token 明文 | 数据源凭据不回显纪律 |
| L3 命令（发布、审批、Kill Switch、路由切换）必须带 `--approval-token`，且服务端经 security-engine ABAC 复核（:530），**CLI 本地判断一律无效** | fail-closed |
| 审计标记 `client=cli` 经请求头透传，落 Kafka `ecos.audit`（ST06） | ST06 |
| CLI 不得包含任何业务计算逻辑（口径/评分/路由算法），只做输入输出编排 | 铁律 §0.6/§2.5；避免"第二实现"成为后门 |

### 2.4 命令族对齐（附件 9 族 → ECOS 前缀）

命令命名与分册 10 的 **前缀单源化**结果绑定（ARCH_SPEC **C234/C249**）：一律 `/api/v1/wagent/*`，**不得**沿用 `/api/v1/agent-loop`、`/api/agent-mesh` 等既有杂项前缀作为 CLI 目标；每条命令需在"路由单源 + 三滤波器"登记表里有对应行，否则不予实现。

## 3. 后果（Consequences）

- **正面**：CLI 成为契约的**消费者**而非第二生产者，D-11 才真正可满足；P-5 顺带补齐了追溯链断裂的 OpenAPI 环，对卷 08（前端/BFF）与测试工程（P-3 Playwright）同样复用。
- **代价**：P-5 是本 ADR 的硬前置；契约文件从 Markdown 反向生成需一次性人工整理约 9 册 §D 章。
- **风险**：若 R-38 裁 B（Java 独立 jar），必须同时声明"不进 reactor、不进基线计数"，否则 `check-legacy-modules.ps1 baselineCount=12` / `ArchitectureTest.baselineModules=12` 双门禁 FAIL。
- **验收标识**：
  - `mvn -Dtest=CliContractParityTest#everyCommandMapsToOpenApiOperation`
  - `mvn -Dtest=CliContractParityTest#noCommandTargetsServicePortDirectly`
  - `pw cli-l3-approval.spec.ts`（在 **P-3/P-4** 闭环前记"未执行"）

## 4. 关联

| 关联 | 位置 |
|:--|:--|
| 需求 | PRD-10 REQ-WAG-20~22（控制面 / 命令族 / L3 审批）；**PRD-01 REQ-PLT-27（CLI 控制面）+ REQ-PLT-26（契约单源，本 ADR §2.2 的前置件 P-5）——两项均已在 PRD-01 v1.1 §3.2 落地为草案，随 R-38/R-50 待裁决** |
| 设计 | 详细设计-10 §四（接口设计）、§八（符合性与改造项，新增 **W277**）；ARCH_SPEC §12.2（新增 P-5 门禁行） |
| 偏差 | ARCH_SPEC **C233**（CLI 无载体）、**C234/C249**（前缀与路由根不唯一）、新增 **C259**（仓内无机器可读 API 契约） |
| 规则 | 铁律 :292 / :530 / §0.6 / §2.5；ADR-10；ST06 |
| 裁决 | **R-38**（载体与栈）、**R-37**（不新增模块）、**R-43**（前缀单源，CLI 目标路径依赖其结案） |
| 前置件 | **P-5**（机器可读契约，本 ADR 新立）；与 **P-3**（Playwright 工程）、**P-4**（SSE/流式）并列进入 ARCH_SPEC §12.2 阻塞表 |

## 5. 变更日志

| 版本 | 日期 | 内容 |
|:--|:--|:--|
| v1.0 | 2026-09-29 | 首版（Proposed）。实测确立"仓内 0 个机器可读契约文件（`docs/**/*.yaml\|yml` 为空）"，据此把附件 D-11「契约同源」重述为**平台级前置件 P-5**，并把 CLI 载体锁定为 `tools/ecos-cli/`（栈与是否本期建设交用户裁）。 |
| v1.1 | 2026-09-29 | 随需求检视报告 §十四 批量批准：裁决 **Proposed → Accepted**（R-38 载体按推荐项 = `tools/ecos-cli/`；R-50 推荐项 ① ⇒ 前置件 **P-5** 成立并计入落地任务）。**P-5 未闭环前 CLI 不得开工**这一约束不变；§14.4 明确 CLI 代码实现需逐项再授权。 |
