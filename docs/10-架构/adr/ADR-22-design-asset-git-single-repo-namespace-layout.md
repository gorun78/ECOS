# ADR-22 — 设计态资产版本控制：单仓多命名空间布局（`ecos-design`）

> 来源: 用户 2026-11-19 委托「Git 作为设计态内容的版本控制」全量梳理 | 日期: 2026-11-19 | 责任人: 架构治理 Agent
> 版本: v1.0
> 裁决: **Accepted**（2026-11-19 用户四项口径裁决：本轮只出规划与规范 / 合并为单仓多目录 / 存量回填基线快照 / 版本表按四表模式处置）
> 上游: 架构铁律 v2.4 §2.5 第 8 项（Git 唯一出口 + 存储二分 + 六类资产）、§3.1（DROP 例外四表）、§3.5（控制域/业务域二分）、§4.10（前端 Git 界面）；后端开发规范 v1.1 §十一（含 VG-1~VG-7）；补充详细设计册 R7~R11 §2.9.5（仓库规范）/§2.9.6（发布双写状态机）/§2.9.7（追踪矩阵）/§2.9.9（安全治理 G1~G10）；ADR-18（发布即 commit+tag）
> 下游: `docs/30-设计/ECOS设计态资产Git版本治理与存储结构规范-2026-11-19.md`（设计册）、`docs/40-实现/Git设计态版本治理实施路线与回填迁移-2026-11-19.md`（实施路线）
> 状态: **Accepted — 布局冻结，代码与库零改动**（本轮只出文档；落地分 B1~B6 批，见实施路线）

---

## 1. 背景（Context）

### 1.1 已确立的治理模型

「Git 是设计态历史版本的唯一权威源，数据库只存在用版本」这一条已经是宪法级要求，不是本 ADR 要讨论的问题：

- 架构铁律 §2.5 第 8 项：Git 操作唯一出口 = `runtime-access` 的 `GitService`/`GitRepositoryService`；DB 只存 `version_no` + 在用内容；`{asset}_version` 表降级为版本索引；接口禁收客户端 `localPath`；六类设计资产一律接入，未接入前返回 `not_available`。
- 后端开发规范 §十一：存储二分、四版本概念（设计态/发布/运行态/历史）、tag 规范、编排层职责、仓库注册寻址、凭据、异步事件、版本索引表。
- 补充设计册 §2.9.5~§2.9.9 + ADR-18：注册表定名 `ecos_git_repo`、发布 16 态状态机、对账 8 类、五原则与可信链（`Commit SHA ≠ 发布授权`）。

### 1.2 落地现状是点状的（2026-11-19 实测）

底座完整且可用，缺的是「覆盖面」与「仓库形态」：

| 项 | 实测事实 | 证据 |
|---|---|---|
| 唯一出口 | `GitService`（JGit 封装）+ `GitRepositoryService` + `GitRepoRegistry`（持久化到表）+ `GitRepoPathResolver` | `ecos_backend/runtime/runtime-access/src/main/java/com/chinacreator/gzcm/runtime/access/git/` |
| 统一归档器 | `DesignAssetArchiver.archive(...)`，三态 ARCHIVED / NOT_AVAILABLE / FAILED，路径 `<repo>/<type>/<id>/<ver\|latest>.json`，tag = `type/id/ver` | `.../git/archive/DesignAssetArchiver.java:74-154` |
| 注册表 | `ecos_control.ecos_git_repo`，**6 行** ACTIVE：`dw-pipeline`(repo_root=`pipeline`) / `ontology-model`(`ontology`) / `kb-asset-tree` / `cognitive-model` / `agent-collab` / `scenario-def` | V300 建表 + 种子；`docs/50-db_script/00_init/10_system_seed.sql` 六行 |
| 工作树 | `~/ecos-git-repos/` 下 6 个目录**均已 init**；commit 数实测 ontology 32 / scenario-def 5 / kb-asset-tree 4 / cognitive-model 2 / agent-collab 0 / pipeline 0 | `git -C <dir> rev-list --count HEAD` |
| 已接归档的资产 | 本体模型、对象实例、pipeline（手工 commit）、合规规则、认知模型、Agent 定义（`agents/latest.json`）、场景定义（`scenarios/latest.json`） | 7 处 `.archive(...)` 调用点 |
| 未接归档的资产 | 数据元数据、质量规则、术语、指标/口径、本体规则、Mind、场景蓝图、AIP 编排、安全中心全部配置 | 全仓 `DesignAssetArchiver` 引用清单外 |
| 仓库根配置 | `ecos_git_repo_root` 在 `sys_config` **无种子行**，现走代码缺省 `~/ecos-git-repos` | `GitRepoRootResolver`；G3 live 复验同结论 |
| 规范与库漂移 | 规范 §十一.5 列 **10 枚** `repositoryId`，库内 **6 枚**；`dw-metadata`/`dw-lineage`/`dw-classification`/`ontology-function` 只存在于文档 | 规范 §十一.5 vs V300 种子 |
| 遗留版本表 | `ecos_ontology_version`/`ecos_object_version`/`sys_rule_version` 已按 V323/V324/V325 DROP；`ecos_agent_version` 不存在。但 `dq_rule_version`、`ecos_caliber_version` 仍在写全量快照 | DS-4 迁移方案 §五；`DqRuleLifecycleDao` :77 |

### 1.3 为什么必须现在决策仓库形态

六个仓库是在「一个域一枚仓」的渐进路径上长出来的，每加一个域就新增一枚注册行。继续这条路径会遇到三个具体问题：

1. **注册表与规范永久漂移**：每加一域就要改种子 + 改门禁 `GitRepoRegistryConsumptionGateTest` 的双向清单 + 改前端 `RepositoryId` 联合类型，三处不同批即红。
2. **跨域一致性无法表达**：「发布一个场景」要同时落场景定义、绑定 Mind、引用本体规则与口径；多仓下没有一个 commit 能表达这组变更，对账与回滚只能逐仓拼。
3. **机构隔离本就未成立**：`owner_org_id` 六行**全为 null**（G8 live 复验），即当前所有仓都是全局仓。多仓并未带来隔离收益。

同时，用户明确要求「认真规划 Git 库的存储结构」，意味着答案不应是「再加几枚仓」，而应是一套稳定的**路径契约**。

---

## 2. 决策（Decision）

### 2.1 物理单仓：`ecos-design`

设计态资产统一存放于**一枚**仓库，仓库标识 `repositoryId = ecos-design`，物理位置 `{ecos_git_repo_root}/ecos-design`（根仍是 sys_config 单源，`repo_root = ecos-design`）。

逻辑上按 **8 个命名空间**分区，命名空间即一级目录、即路径契约、即权限与纳入/排除的作用域：

```
ecos-design/
├── .ecos/manifest.json     # 布局版本、命名空间清单、排除声明、生成时间
├── data/                   # 土 D · 数据域
├── ontology/               # 金 I · 本体域
├── knowledge/              # 水 K · 知识域
├── cognitive/              # 木 C · 认知域
├── ai/                     # 火 W · 智能域
├── scenario/               # 场景层
├── security/               # 护 · 安全域
└── platform/               # 平台配置与字典
```

完整目录树、每个叶子的资产映射见设计册 §三。**本 ADR 只冻结三件事**：单仓、命名空间即一级目录、叶子路径 `ns/type/id/ver.json`。

### 2.2 路径即契约（Path-as-Contract）

- 路径 `ns/type/id/ver.json` 是**对外契约**：历史读面、diff、回滚、对账一律按路径寻址，不解析业务表。
- **tag 规范统一为 `ns/type/id/ver`**（现有 `DesignAssetArchiver` 为 `type/id/ver`，单仓下必须加命名空间前缀，否则 `models/...` 在 cognitive 与 ai 两域同名冲突）。
- **元数据走 commit trailer，不套文件信封**：
  ```
  <type>(<ns>): <action> <assetId> <version>

  Asset: <ns>/<type>/<assetId>
  Version: <version>
  Content-Hash: sha256:<hex>
  Status: DRAFT|PUBLISHED|DEPRECATED
  Actor: <user>
  Trace: <traceId>
  ```
  理由：现有读取端（`OntologyGitHistoryReader` 解析 `Publish ontology …`；`ComplianceRuleVersionService` 解析 `Snapshot compliance rule …` 并按路径 `listCommitShasForPath` 过滤）是**按消息 + 路径**工作的。若引入 JSON 信封，所有已实装的读面必须同步重写，风险与收益不成比例。trailer 只做增量（键集只增），旧读法继续可用。

### 2.3 提交必须按路径（禁止整树 `add .`）

现有 `commitAllChanges` 实现是 `git.add().addFilepattern(".")` 后整树提交（`GitServiceImpl`）。**单仓下这会把其他域未完成的改动一起带走**，直接违背「一次发布 = 一次 commit」的语义，还会让 `PARTIAL_FAILURE` 对账失去意义。

因此运行时底座必须新增：

1. `commitPaths(repository, message, authorName, authorEmail, List<String> relativePaths)` —— 按路径 `add` + 只提交这些路径；无变更返回显式的「无变更」结果而非伪造 commitId。
2. 每仓**串行锁**：同一 `repositoryId` 的写操作串行化，避免 JGit `index.lock` 争用（单仓必然高频写）。
3. `DesignAssetLayout` 单源：命名空间/类型/版本 → 相对路径的唯一生成器，禁各域拼字符串（现有各域已各有 `GIT_ASSET_TYPE_RULES`、`"agents"`、`"models"`、`"scenarios"` 等散落字面量）。

### 2.4 旧六仓的处置：原地保号、只读、零改写

**不做历史改写导入。** 用户选择「合并为单仓」，但把 6 枚既有仓 rebase/筛选迁移进单仓会产生不可逆的重写，且现有 commit 消息与路径一旦变形，读取端的历史解析会立刻失效。因此：

| 阶段 | 旧仓状态 | 新仓状态 |
|---|---|---|
| B2 | 保持 ACTIVE，零写入窗口内不新增 commit | 建仓 + 登记 + 布局 manifest 首提交 |
| B3 | 保持 ACTIVE（只读） | 基线快照作为业务内容首提交 |
| B4 | 置 `status='DISABLED'`（注册行保留） | 全域写面切新仓 |
| 收口 | 置 `DISABLED` 后**按裁决删除**（R-2026-11-20⑧） | 唯一写入源 |

**删除裁决（R-2026-11-20⑧）与其不可逆前置**：用户裁定最终删除旧六仓，而非长期只读保号。删除前**缺一不可**：

1. B3 基线回填完成，且 `historyOverlay` 读面验收通过（旧历史已在新仓可读或已备份留档）。
2. 逐仓备份并登记哈希（`GitRepoBackupService` 整目录 + sha256），备份存异地受控位置（备份文件不入库）。
3. 六仓的 `rev-list --count` 与 HEAD sha 对账表归档（作为「删除的确实是这份历史」的证据）。
4. 单独授权窗口执行；执行记录写回本 ADR 与实施路线。

> 现状历史规模（2026-11-19 实测，作为删除前必须留档的内容）：`ontology` 32 commit / 26 tag（始于 2026-11-04）、`kb-asset-tree` 4/3（2026-11-02）、`cognitive-model` 2/2（2026-11-19）、`scenario-def` 5/0（2026-11-17）、`agent-collab` 0/0、`pipeline` 0/0。前四仓共 **43 条真实发布历史**，删除即失去这些 commit 的在线可读性。

**历史读面过渡**：新增 `historyOverlay` 映射（旧 `repositoryId` → 新 `namespace`），读面「新仓优先、旧仓回退」，返回每行标注 `contentSource` 与来源仓。这与已实装的 `VersionContentSource{GIT, DB_IN_USE, DB_FALLBACK}` 是同族分态思路——**禁把「旧仓查不到」折叠成「没有历史」**。

### 2.5 存量回填：基线快照，不伪造变更史

每域导出「当前态」作为一条 `baseline` 提交，打 `baseline/<ns>/<YYYYMMDD>` 轻量标签，提交消息显式写明 `baseline`。基线提交**不代表该资产被发布过**，仅表示「这天的在用版本是这样」——因此：

- 基线的 `Status` 取资产当前真实状态（如蓝图 `RELEASED`、规则 `ACTIVE`），不统一造 `PUBLISHED`。
- 导出失败的资产（含不可序列化字段、外键悬空、跨 schema 引用）**列入缺口登记表**，禁静默跳过、禁用空文件占位。
- 一个命名空间一批 commit（用 `archiveBatch`），避免几千条单资产提交把仓库灌爆。

### 2.6 组织隔离：裁决为「新增命名空间级策略」

单仓后 `ecos_git_repo.owner_org_id` 只能表达「整仓属主」，无法表达「数据域属 A 机构、安全域属 B 机构」。现状是六行**全为 null**（全局仓），`GitRepoOrgGuard` 只做仓级四档判定（owner 空放行 / 匹配放行 / 超管旁路 / 其余 403）。

**裁决（R-2026-11-20⑥）：采纳「新增 `ecos_git_repo_namespace_policy`」**，即 namespace × org × 读/写/发布三维矩阵，守卫由仓级升级为命名空间级。

- 表名经全仓 grep 确认未被占用（`namespace_policy` / `NamespacePolicy` 零命中）。
- **本轮不建表**：在此之前仍为「仓级 = 全局」，机构级细粒度隔离**未实现**，任何文档与界面**不得宣称已生效**。
- 建表与守卫改造属后续独立 DDL/代码批次，须单独排期与授权（涉及权限判定链路，影响面大于归档本身）。

### 2.7 版本表处置：沿用四表模式

`dq_rule_version`（approve 时 INSERT 全量 `snapshot` JSONB）、`ecos_caliber_version`（每升版一行、含 `formula` 全文）与「历史只进 Git」冲突。按用户裁决，处置路径与 `ecos_ontology_version` 等四表完全一致：

停写 → 读写切 Git → `pg_dump` 备份 → DROP（迁移脚本 V361 起）。**执行前置缺一不可**：备份完成、代码侧零读写残留、在线版本接口已具备 Git 重建能力、DROP 走单独 DDL 窗口。本 ADR 只登记口径，不授权执行。

---

## 3. 后果（Consequences）

### 3.1 正面

- **路径契约稳定**：新增资产类型只加目录，不改注册表、不改门禁双清单、不改前端联合类型（前端改按命名空间枚举）。
- **跨域变更可表达**：一次场景发布可在同一 commit 内落场景定义 + Blueprint + 绑定的 Mind 引用。
- **对账与回滚具备整体性**：`PublishReconciler` 的 8 类判定可在单仓内跨命名空间工作。
- **注册表与规范不再漂移**：10 枚 vs 6 枚的文档/库分叉在 B4 后消失（规范改为「1 仓 8 命名空间」）。
- **单仓天然具备全量一致性快照**：备份（`GitRepoBackupService`）与发布冻结（`PublishFreezeGuard`）作用域变得清晰。

### 3.2 负面与代价（必须承认）

| 代价 | 说明 | 缓解 |
|---|---|---|
| 写争用 | 单仓 `index.lock` 全局互斥，并发发布会排队 | 每仓串行锁 + 按路径提交缩小临界区；接受排队（设计资产是低频写入） |
| 体积增长 | 单仓体积随时间单向增长 | 设计资产是文本 JSON，量级小（本体 32 commit 未达 MB 级）；备份走整目录 + MinIO 打包 |
| 权限粒度 | 仓级权限无法按域细分 | §2.6，裁决 R-2026-11-20⑥：新增 `ecos_git_repo_namespace_policy`（后续批次建表） |
| 读取端改造 | 前缀变化打断消息解析、路径过滤、tag 名三处 | §2.4 过渡读法 + 旧仓回退 + 旧 tag 只读保留 |
| 迁移期双写窗口 | B2~B4 期间新旧仓并存 | 明确「新仓写面优先、旧仓零写入」，窗口内禁发布回滚以外的操作 |
| 门禁钉值 | `GitRepoRegistryConsumptionGateTest` 六枚 id × 属主域判据失效 | 同批改为「命名空间 × 属主域」判据，禁只改一侧 |

### 3.3 不受本决策影响的既有能力

`PublishManifest` 六项绑定、`BranchProtectionPolicy`、16 态状态机、`PublishFreezeGuard`、`GitRepoBackupService`、`OptionalArchiver` 的诚实三态、前端 `gitService.ts` 单通道、`GitRepoOrgGuard` 的机构校验机制本身——**全部保留**，只是作用域从「多仓」变为「单仓」。

---

## 4. 备选方案（Alternatives）

| 方案 | 描述 | 为什么不选 |
|---|---|---|
| A. 沿用 6 仓 + 按需新增至 10 枚 | 保留现网结构，补齐 `dw-metadata`/`dw-lineage`/`dw-classification`/`ontology-function` | 用户已裁决单仓；且每加一域三处同批改，治理成本线性增长；跨域一致性仍无解 |
| B. 一域一仓重组（8 枚） | 按 8 大域重划，语义最干净 | 需重写各仓历史或废弃历史；本体域已有 32 commit 的真实版本史，迁移风险高于收益 |
| C. 一仓多目录（本 ADR） | 单仓 + 8 命名空间 + 路径契约 | — |
| D. 继续用 DB 版本表 | 不依赖 Git | 违背铁律 §2.5#8 与 §3.1 DROP 例外口径；历史版本表膨胀、无法 diff、无签名与审计绑定 |

**方案 C 与 A/B 的前向兼容**：路径契约设计为「命名空间 = 一级目录」，未来若要拆回多仓，只需把某命名空间目录提为仓根、改注册表绑定，叶子路径不变 ⇒ 读取端零改动。这是选择「目录前缀」而非「改叶子结构」的核心原因。

---

## 5. 实施约束（详见实施路线）

1. 本轮**代码、数据库、Git 仓库零改动**；只产出 ADR-22 + 设计册 + 实施路线三份文档。
2. B1 起，任何新增归档调用必须经 runtime `DesignAssetArchiver` + `DesignAssetLayout`；禁在域内拼路径（新增门禁承接）。
3. 迁移脚本自 **V361** 起落活单源 `docs/50-db_script/02-flyway-migration/gateway/`；DDL 钉值四处（`MAX_BASELINE_VERSION` / `NEXT_FREE_VERSION` / `SCRIPT_COUNT_FLOOR` 三处）同批改。
4. `ecos_git_repo_root` 的种子行缺失属既有缺口；**裁决 R-2026-11-20⑨：由运维补齐种子行**，B2 建仓前完成（禁保留隐式缺省、禁在代码里写第二套缺省）。
5. 签名（GPG/SSH）与远端推送通道仍未接入；单仓是否托管远端取决于该授权，不在本 ADR 范围内预设。

---

## 6. 关联文件与追溯

| 类型 | 路径 |
|---|---|
| 设计册 | `docs/30-设计/ECOS设计态资产Git版本治理与存储结构规范-2026-11-19.md` |
| 实施路线 | `docs/40-实现/Git设计态版本治理实施路线与回填迁移-2026-11-19.md` |
| Git 底座 | `ecos_backend/runtime/runtime-access/src/main/java/com/chinacreator/gzcm/runtime/access/git/` |
| 注册表 DDL | `docs/90-归档/50-db_script/02-flyway-migration-legacy/gateway/V300__ontology_rule_git_cols_and_ecos_git_repo.sql`（V304/V307/V308 补列） |
| 现有版本迁移证据 | `docs/40-实现/DS-4版本真源迁Git迁移方案-2026-11-08.md` |
| 治理差距 | `docs/30-设计/ECOS控制域版本治理现状与差距分析-2026-10-14.md`（含勘定注） |
| 安全治理缺口 | 补充详细设计册 R7~R11 §2.9.9（G1~G10 逐条实测表） |
