# 本体工作台 20 任务交付收口报告

> 来源: 肖国荣 / 日期: 2026-09-13 / 责任人: fullstack-implementer + QA + Reviewer
> 铁律: 继承架构铁律 v1.1（§2.4 安全集成 / §2.5 runtime 公共底座 / §3.1 Schema 只加不删 / §1.2 三滤波器）
> 分支: `feat/ontology-workbench-wave-b` · 凭证: 22 个 clean commit（`git log --grep "本体-工作台"` 可溯源）

## 一、交付结论

**PASS（可合入 dev）**：最终总闸门 QA `deliverable_allowed=true`（15/15 spot check、双编译绿、P0=0），Reviewer 两轮 P1 已全部修复回验（`ea7ec9b`）。

## 二、20 任务凭证链

| Wave | 任务 | 凭证 commit | 摘要 |
|:--|:--|:--|:--|
| A | T1 useOntologyData N+1 修复 | `0a5b9fa` | 打开工作台 N+1 → 固定请求数 |
| A | T2 双 API 层收敛 | `526faca` | api.ts 重复段 → services/ontologyApi.ts |
| A | T3 Export Modal 主题 | `2840d52` | 硬编码色 → theme tokens |
| A | T4 Sidebar 主题 | `d97f78a` | 域卡片 hover/按钮 → tokens |
| A | T5 BusinessWorkbench i18n | `db93338` | ~28 处中文 → t('ow.biz.*') |
| A | T6 死页面清理 | `9daf382` | bak2 删除 + 死路由注释 |
| B-1 | T12 安全集成 | `40ba536` | security-engine 4 端点 + Kafka 审计 + 默认 DENY |
| B-1 | T12 P0 加固 | `55bb833` `c6cf4fd` | 读路径 fail-open 三级全关 + RLS 白名单 + 3s timeout |
| B-2 | T13 跨引擎 JDBC→REST | `7b60c17` | DataNetResourceClient 替代 td_data_field 直查 |
| B-2 | T14 Neo4j 直写→kb REST | `1b79a22` | KbEngineGraphSyncClient + @Deprecated 兼容 |
| B-3 | lineage 收口 | `7e7f3e8` | Wave31 残留 DTO 补齐过编译门 |
| B-3 | T15 内存态落 PG | `5b148f4` | ConcurrentHashMap → ecos_ontology_data (V119) + 全量 UUID |
| B-3 | T17 逻辑删除+4表兜底 | `f5c6cd9` | 3 Repository DELETE→UPDATE + V120 三表 CREATE 兼底 |
| B-4 | T16-1~5 Map→DTO/VO | `b33da10` `e646f46` `9cb9df2` `050cf63` `8b031aa` | 26/30 Controller 强类型（Lineage/EcosMappingFull 属 PMO-51 残留例外） |
| B-5 | T7 5类视图 CRUD | `5c580a7` | /objects?type=X 统一端点 + V121 新表，T12 pointcut 命中 |
| B-5 | T8 域 CRUD 前端连通 | `2c28819` | 读源切后端权威源 + 7 域 API + 发布/废弃 workflow |
| C | T9 TestTab 去 mock | `4c445f2` | 接 FunctionController /test 真实执行 |
| C | T10 导出任务闭环 | `3b1da2f` | 任务列表/轮询(cleanup)/下载/删除 |
| C | T11 版本 diff 联调 | `73f3c09` | 接 /versions/diff（versionId 修复 404 根因）+ 快照下钻 |
| C | Wave C P0/P1 修复 | `280b76c` | /export/tasks 端点对齐 + en.json 15 keys 补齐 |
| D | T18 双路径收敛登记 | `01ea1e2` | workflow 双副本清单 + 下线 4 前置 + 兼容期 JSDoc |
| D | T19 路由契约补齐 | `3cebd75` | api-contract.md 32 Controller 全端点登记 |
| D | T20 双套表收敛 | `c58fa03` | V122 ghost 表登记 + public 幂等兜底（零 DROP） |
| D | T11-P1 双链路补丁 | `22987d9` | 后端成功直用/本地 fallback + catch 留痕 + 来源标注 |
| D | 最终 P1 修复 | `ea7ec9b` | V122 COMMENT 幂等 DO 块（PG16 实测零错误）+ 契约表重数 32/204/切点 23/32 |

## 三、闸门记录

| 闸门 | 结果 |
|:--|:--|
| Wave A 闸门 | PASS（T6 越界拆分修复后重判） |
| Wave B 闸门 A（T12-T14） | Reviewer FAIL → P0 加固 `55bb833`/`c6cf4fd` 后放行 |
| Wave B-3 闸门（T15/T17） | PASS |
| Wave B-5 闸门（T7/T8） | 双 PASS |
| Wave C 闸门（T9-T11） | P0（/export/tasks 端点错位）→ `280b76c` 修复后放行 |
| Wave D 审查（T18-T20） | CONDITIONAL PASS → `ea7ec9b` P1 回验闭环 |
| **最终总闸门** | **QA PASS（15/15 + 双编译绿 + 29 commit 溯源 0 越界）** |

## 四、遗留清单（非阻断，PMO 跟踪）

| # | 项 | 处置建议 |
|:-:|:--|:--|
| 1 | T13 datanet `/resources/{id}/fields` 端点未落地（stub fail-soft） | 与 datanet owner 对齐后落端点 |
| 2 | T14 kb `graph/sync` 端点 + body 契约未落地（stub fail-soft） | 与 kb owner 对齐（真实端点 `POST /api/v1/knowledge/sync/trigger` 不读 body） |
| 3 | **P1：ontology-engine-impl → buszhi-impl 跨引擎反向依赖**（pom + import 双实证） | 随 workflow 物理收敛断链，或契约上提 buszhi-api；已登记 `docs/11-运维/t18-route-consolidation-checklist.md` |
| 4 | workflow 同路由双副本（gateway/buszhi 双向互斥 exclude 存续） | 物理下线前置：buszhi 聚合 E2E + 线上流量方向核查（当前存活侧是 ontology，与原方向相反） |
| 5 | T7 新端点 VO 不走 T12 RLS/CLS（interceptor 仅适配 Map/List\<Map\>） | 下一波 interceptor 适配 VO |
| 6 | `ecos_ontology` schema 8 张 0 行 ghost 表 | 物理 DROP 需 PMO 专项（V122 已逐表 COMMENT 登记） |
| 7 | FunctionTypeDetail 死代码 ~23 行 / 旧 downloadExport 无消费者 | 下波顺手清理 |
| 8 | 8 个 Controller 不在 T12 pointcut（Ontology 前缀缺失：Workflow/Function/Glossary 等） | 契约文档已 ❌ 标注，建议下一波扩 pointcut 或改类名 |

## 五、环境事项声明

- 分支与并发 PMO-52（knowledge 批次）同分支交织提交，全程精确白名单 add，29 个本体工作台 commit 可 `git log --grep` 独立溯源
- 全量编译（-am）被 working tree 他人 in-progress 文件污染，各波次以模块级 `mvn -pl ontology-engine-impl install`（绿）+ QA 复验为准
- Gateway 离线，curl/浏览器 E2E 未执行，属静态契约验收；合入 dev 前建议 Runtime 窗口跑 V4 四步法（网关起后 curl 5 类对象 + 域 CRUD + 版本 diff 冒烟）
