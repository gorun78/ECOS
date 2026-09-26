# 需求分析摘要 — 知识工作台模块重构 v2.0

> 来源: PM 需求分析 | 日期: 2026-09-24 | 责任人: PM Agent
> 版本: v0.1-draft（第一门禁待确认）
> 追溯: 用户指令 + 原型 index.html v1.0 + 复杂度评估 L2+

## 背景与目标

**背景**：

知识工作台前端现有 5 组 14 个 Tab（overview / datasync / streaming / review / classification / update / rag / graph_explorer / rules / eval / lifecycle / compliance / engine_config），分属 6 文件组、18 个 Tab 组件，语义交叉：
- 抽取链路（datasync / streaming / review / update）分散在 manage 组，用户找不到完整链路
- 资产管理（classification）藏在 manage 组末尾，不突出
- 治理（rules / eval / lifecycle / compliance）4 个 Tab 语义分散，无统一治理首页
- 图谱（graph_explorer）与 RAG（rag）分属 retrieve 组，检索能力割裂
- 无企业知识（Wiki / md 文档）页面——md 导入缺统一入口
- 引擎配置（engine_config）面向运维而非业务用户，不应在工作台暴露

用户提供了界面原型 v1.0（`index.html` 93 行 HTML + 18 CSS + JS），定义了 7 个平铺侧栏页面。**本 PRD 按原型 + 用户 4 条微调收敛为 6 页面**（知识中心暂不考虑）。

**业务目标**（量化）：
- 前端 Tab 从 14 个收敛为 **6 页面**（侧栏平铺，无嵌套分组）
- 每页面单文件，代码量 ≤现有对应 Tab 总和（合并后不膨胀）
- 撤销（P3）的 Tab 路由保留不删（R9 只加不删），但侧栏导航隐藏
- 用户从侧栏任一按钮点击 **≤ 1 次**到达目标功能（现部分功能需 2 次选择）

**用户目标**：
- 业务用户：录入 md → 抽取 → 审核 → 发布，全链路 3 步内可达
- 图谱用户：浏览图谱 + 按 categoryIds / domain 过滤（已落地后端下沉）
- 管理员：统一治理入口（审核 / 生命周期 / 质量 / 规则 / 评测 1 页）

## 需求范围

**包含**：
- 前端 KnowledgeView 重构：5 组 14 Tab → 6 平铺页面
- 企业知识 WikiPage 新建：md 文档导入（非在线编辑器）
- 知识资产页：合并 datasync + classification，资产表含「图谱实体 / 向量索引」状态列
- 知识抽取页：源头标注 DW 层，合并 streaming / vector_index / doc_upload
- 知识治理页：合并 review / lifecycle / compliance / rules / eval 为 1 页分区显示
- 知识图谱页：graph_explorer 重命名 + categoryIds 后端下沉复用（本 goal 已落地）
- 知识总览页：KPI 6 指标 + 生产流水线 + 待处理事项
- i18n 清理：2010 key 中仅保留 6 页面实际消费子集
- 后端 Controller 文档化分组（endpoint 不变，只加常量表 + Javadoc 分组注释）
- 设计文档 v2.0：`docs/engine-kb/knowledge-navigation-design.md` → v2.0（7.9 未做项补全 + 6 页面结构定稿）

**不包含**（明确排除）：
- **知识中心 / RAG 问答页面**（用户裁决暂不考虑，rag Tab 撤导航但路由保留）
- **引擎配置页**（engine_config 撤导航，归 `engine-knowledge` 监控页）
- **本体模型**（OntologyModelTab 撤导航，归本体工作台）
- **图谱构建**（GraphBuilderTab 撤导航，归 runtime-task）
- **术语表**（GlossaryTab 撤导航 P3）
- **在线 Wiki 编辑器**（用户裁决采用 md 导入方式，非直接编辑）
- **后端表结构变更**（V155/156/157 已落，架构图与 endpoint 不动）
- **前端 dev server 新端口**（沿用 3000 + gateway 8080 proxy）

## 初步功能清单

| ID | 功能名称 | 优先级 | 说明 |
|:--|:--|:--:|:--|
| F1 | KnowledgeView 侧栏导航重构（6 平铺页面，替代 5 组 14 Tab） | P0 | 原型侧栏：知识总览 / 知识资产 / 知识抽取 / 知识图谱 / 企业知识 / 知识治理 |
| F2 | 知识总览页（OverviewPage）— KPI 6 指标 + 流水线 + 待办 | P0 | 6 KPI：知识资产 / 图谱实体 / 图谱关系 / 向量索引 / 待审核 / 抽取任务 |
| F3 | 知识资产页（AssetListPage）— 统一对象表 + 图谱/向量状态列 | P0 | 合入 DatasyncTab + ClassificationTab，资产表 6 列：名称/类型/状态/语义关联/来源/**图谱实体+向量状态** |
| F4 | 知识抽取页（ExtractionPage）— 结构化 DW 层源头 + 非结构化导入 | P0 | 左栏：结构化映射（已发布 DW 契约 → 抽取 → 预览）；右栏：文档导入 → DW 层；标注「源头唯一 = DW 层」 |
| F5 | 知识图谱页（GraphPage）— 可视化 + categoryIds 后端下沉 | P0 | 复用 GraphExplorerTab，categoryIds 后端下沉已完成（12a20d6），保留前端 checkbox 但 walk 透传后端 |
| F6 | 企业知识页（WikiPage）— md 文档导入 + 预览 + 状态 | P0 | 三栏：知识空间树 / md 预览（read-only）/ 元数据。导入入口（非编辑器） |
| F7 | 知识治理页（GovernPage）— 审核 / 生命周期 / 质量 / 规则 / 评测 5 in 1 | P0 | 上区：审核 feed（原 review Tab）；中区：生命周期 + 质量指标；下区：规则仓库 + 评测运行 |
| F8 | 撤下 Tab 路由保留（rag / engine_config / ontology_model / graph_builder / glossary / sync / data_import / vector_index / document_upload / extraction_streaming） | P1 | 路由不删（R9 只加不删），侧栏隐藏，保留 deep-link 可访问 |
| F9 | i18n 清理 — 6 页面消费 key 子集标注，废弃 key 添加 `@deprecated` 注释 | P1 | 2010 key 不清理（R9 只加不删），只给 6 页面 checkbox 勾选集合 |
| F10 | 后端 Controller 分组文档化（不改 endpoint） | P2 | 在 `kb-engine-impl` 加 `KbEngineModuleRegistry` 常量类（注释 6 模块 → Controller → endpoint 映射），加 Javadoc 分组注解 |
| F11 | 设计文档 v2.0 — knowledge-navigation-design.md 升级 | P2 | §一 定位改 6 页面 / §七 5 项未做项补齐（#1-#3 已做 ✅，#4/#5 P3）/ 新增 §八 6 页面结构定稿 |
| F12 | 回归 & 质量门 — 编译 + 类型检查 + 6 页面 console err 零 + 4 用例 | P0 | `mvn install -Penterprise -pl kb-engine-impl -am` SUCCESS + `npx tsc --noEmit` + 6 页面 screenshot 零 console err |

## 需求缺口（已全部补齐）

| 缺口 | 状态 |
|:--|:--|
| 知识中心 RAG 问答页是否保留 | 已裁决：暂不考虑，撤导航（路由保留） |
| 企业知识编辑方式 | 已裁决：md 文档导入，非在线编辑器 |
| 知识抽取源头 | 已裁决：唯一源头 = DW 层（本体映射契约 + 文档导入均经 DW 层） |
| 知识资产存储载体 | 已裁决：Neo4j 图 + pgvector 向量双写（表状态列展示） |
| Tab 数量 | 已裁决：6 页面（原型 7 去掉知识中心） |

## 目标用户与场景

| 用户角色 | 描述 | 主要场景 |
|:--|:--|:--|
| **知识管理员** | 企业知识运营人员 | md 文档导入 → 看抽取预览 → 审核发布 → 看治理指标 |
| **业务用户** | 产品 / 开发 / 运营 | 知识资产浏览 + 图谱探索 + 待处理事项跟踪 |
| **画图表管理员** | 数据 / 本体工程师 | 选 DW 契约 → 提交抽取 → 看实体/关系入图谱状态 |
| **系统管理员** | 运维 | 走 `engine-knowledge` 监控页（不在工作台） |

## 6 维度评分

| 维度 | 分 | 依据 |
|:--|:--:|:--|
| 功能数量 | 7/10 | 6 重构页面 + 撤下路由保留 + 回归 4 用例 = 12 功能点 |
| 模块数量 | 7/10 | 前端 6 Page + 后端 Controller 分组文档化 + API 调用归属重分 |
| 数据复杂度 | 5/10 | 表结构不动，资产表新增「图谱实体 / 向量索引」状态列读 PgVectorSupport + Neo4j |
| 外部依赖 | 5/10 | Neo4j / pgvector / Kafka / LLM-gateway / security-engine ABAC / runtime-task |
| 并发/性能 | 3/10 | 管理台级别，单用户为主 |
| 用户界面 | 9/10 | 6 页面 × 多分辨率 + 多状态 + 跨 Tab 联动 + md 预览 |
| **总分** | **36/60** | **L2 标准（边界 L3 — 跨模块全量重构）** |

**推荐工作流**：L2+（前后端并行）— 后端 Controller 注释化分组不改 endpoint；前端 KnowledgeView 全量重构 + 6 Page 并行开发；QA 简化（编译 + 类型 + 6 页面 console err）；**Reviewer 必跑禁跳**。

---

**【第一门禁 — 需求理解确认】**

请确认以下内容（逐条 ✅ 或提出修正）：

1. 需求背景和目标描述准确？（14 Tab → 6 页面，知识中心撤，md 导入非编辑器，源头 DW 层，图 + 向量双写）
2. 功能范围（包含 12 件 / 不包含 8 件）正确？
3. 功能优先级（P0×8 / P1×2 / P2×2）合理？
4. 目标用户 4 角色与场景完整？
5. 需求缺口 5 项已裁决正确？

**回复「第一门确认」或「确认」后**：进入第二门 PRD 初稿编写。
**回复「需要修正 XX」**：基于反馈修订后重新走第一门。
