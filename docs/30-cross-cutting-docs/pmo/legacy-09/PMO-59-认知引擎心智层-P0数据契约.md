# PMO-59 Phase 1: 认知引擎心智层 — P0 数据契约与三表落库

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../.trae/rules/架构铁律.md)
> 来源: PM项目经理 | 日期: 2026-09-13
> 责任人: fullstack-implementer (开发) / PM项目经理 (验收) / code-artifact-reviewer (审查)
> 铁律: ① ADR-9 解锁"cognitive 不新增 DB 表"红线（口径=推理结果不落盘 / 模型资产落盘 ADR-8 / **认知心智状态落盘**）② API 只增不改 ③ 本 Phase 仅契约 + 三表落库，不做任何业务实现与前端

## §背景

ECOS 认知引擎（cognitive-engine 木·C，寄居 `services/dccheng` :18086，boot :18089）升级为"企业经营心智系统"（参考 `参考-外部方案-经营认知引擎(术语修订版).md`，其中"信念"已统一改称"**不确定性判断**"）。

**已落盘决策（全部经用户 2026-09-13 确认）**：
1. **三表落库（ADR-9 口径）**：`ecos_cognitive_evidence`（证据/来源/可信度/冲突标记）+ `ecos_cognitive_hypothesis`（假设/证据引用/valid/失效时间）+ `ecos_cognitive_belief`（不确定性判断：变量名/有限离散概率分布 JSONB/版本/人工覆写标记）
2. 实时事件冲击链 = **原生 Kafka 事件 + runtime-task 定时补算**（不做流式）
3. "周一故障复盘"流程纳入计划（本 Phase **范围外**，仅在本期告警设计预留接入点）
4. 时间回放（历史决策同参数重算）已确认实施（Phase 3 实现；本 Phase 仅在 belief 表**预留 snapshot 版本字段**）
5. 排期 12 周 2 并行池已确认
6. 前置依赖：PMO-50~53 及 G4 断链已全部交付（commit `b767135`~`1c6c294`，基线 `doc: f0b15b7`）

**ADR-9 三表口径（写入 cognitive-engine AGENTS.md 红线登记）**：
- 推理**结果**不落盘（实时计算，ADR-8 原口径保留）
- 模型**资产**落盘（`ecos_cognitive_model`，ADR-8 修订）
- 认知**心智状态**（证据/假设/不确定性判断）落盘 ← 本 ADR-9 新增

## §禁止清单（继承铁律 §5.1 + 本指令特有）

1. 不跨 Phase 预创建文件（Phase 2 才动的 cognitive-engine-impl mapper/service 实现与前端一律不碰）
2. 不改任何既有 API 路径与参数签名（只增不改）
3. 不新建 Maven 模块 / Docker 容器；不引入 ML 框架与 Python 数值服务
4. 不用 `mvn compile`，必须 `mvn install`；kb-engine-impl 有既有测试编译错误，测试编译一律加 `"-Dmaven.test.skip=true"`（PowerShell 下带引号）
5. LLM 零参与认知计算（ADR-5 口径）
6. Flyway 禁用 — 新表 DDL 走**手工 docker psql 执行**（迁移 SQL 文件仅作版本留痕，与 V123~V126 先例一致）
7. Windows 环境 DDL 执行必须 `docker cp xxx.sql ecos-postgres:/tmp/ && docker exec ecos-postgres psql -U postgres -d sys_man -f /tmp/xxx.sql`（PS 5.1 管道给 psql 注入 BOM，**禁止** `Get-Content | docker exec -i`）
8. DDL 只加不删：三张新表 + 新增索引，不触碰任何既有表/列/索引
9. 不创建临时脚本（铁律 §5.1#14）；DDL 文件放 `gateway/src/main/resources/db/migration/`（版本留痕惯例位置）

## §Task（4 个，单指令 ≤5 Task 合规）

| Task | 文件/路径 | 操作 | 验收 |
|:--|------|------|------|
| T1 | `ecos_backend/gateway/src/main/resources/db/migration/V127__ecos_cognitive_evidence.sql` / `V128__ecos_cognitive_hypothesis.sql` / `V129__ecos_cognitive_belief.sql`（编号=现有最大号 V126+1 起） | 三张表 DDL：全表带 id VARCHAR(64) PK / tenant_scope(可选) / create_time/update_time/create_by/update_by/is_deleted；PK + 唯一索引 `uniq_表名_字段` + 普通索引 `idx_表名_字段`；JSONB 字段带默认值；每表 seed 1~2 条测试数据（`ON CONFLICT DO NOTHING`） | `docker exec ecos-postgres psql` 三表存在且含 seed 行；SQL 文件无 `select *` |
| T2 | `runtime/common-api` 新增 `cognitive` 契约包：`cognitive/EvidenceRecordVO.java` + `cognitive/HypothesisVO.java` + `cognitive/BeliefDistributionVO.java`；检查 `common/event/KafkaTopics.java` | 强类型 VO 契约（风格与 common-api 现状一致=手写 getter/setter，Javadoc 注释）；KafkaTopics 无 cognitive 相关 topic → **新增** `COGNITIVE = "ecos.cognitive"` 常量并注释用途（新证据冲击事件 dccheng→w* 订阅预留） | `mvn install -pl runtime/common-api` 绿；契约类纯 POJO 无业务依赖（enforcer 通过） |
| T3 | `cognitive-engine-api` 新增 `cognitive2/service/IUncertaintyJudgementService.java` + `cognitive2/service/IHypothesisLifecycleService.java`（签名=查询/注册/失效检测占位，仅契约不实现）+ `cognitive-engine/AGENTS.md` 登记 | 接口契约先行（风格对齐 `ForecastService`：Javadoc + 纯接口），impl 留待 Phase 2；AGENTS.md"我的数据库表"节补 ADR-9 三表口径、"禁止"#2 改写为三档落盘口径 | 全量 `mvn install "-Dmaven.test.skip=true"` 绿 |
| T4 | ① `docs/plans/api-contract.md` 追加 §认知心智层 P0 契约（三表 + Kafka topic，标注 Phase 2 开放端点）② `docs/09-PMO指令/PMO-59-认知引擎心智层-Phase1验收记录.md` | Phase 收口验证全记录：全量编译绿 + Gateway 存活 + 三表 psql 查询留痕 + commit hash 凭证 + 验证四步法 V1-V4 记录 | 验收记录文档含 commit hash + psql 输出原文 |

**表结构设计要点（T1 依据）**：

1. `ecos_cognitive_evidence`（证据表·层1）：
   - `id VARCHAR(64) PK`，`evidence_code VARCHAR(128) NOT NULL`（业务唯一键→uniq）
   - `source_type VARCHAR(32) NOT NULL`（SYSTEM_DATA / NEWS / EXPERT / PIPELINE）
   - `source_ref VARCHAR(256)`（来源定位：管道ID/URL/人工录入ID）
   - `blob JSONB NOT NULL DEFAULT '{}'::jsonb`（结构化事实载荷）
   - `confidence DECIMAL(5,4) NOT NULL DEFAULT 0.5`（可信度 0~1：系统数据 0.95-1.0 / 权威新闻 0.8 / 小道消息 0.3-0.5）
   - `is_conflict BOOLEAN NOT NULL DEFAULT FALSE`（同事实多证据不一致→冲突待修正标记）
   - `refuting_evidence_ids JSONB DEFAULT '[]'::jsonb`（冲突对方证据 id 列表）
   - `effective_time / expire_time TIMESTAMP`、`status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'`
2. `ecos_cognitive_hypothesis`（假设表 H 库·层3）：
   - `id VARCHAR(64) PK`，`hypothesis_code VARCHAR(128) NOT NULL`→uniq
   - `statement TEXT NOT NULL`（假设陈述）
   - `domain VARCHAR(64)`（业务域）、`metric_ref VARCHAR(128)`（关联经营变量）
   - `evidence_ids JSONB NOT NULL DEFAULT '[]'::jsonb`（支撑证据 id 引用）
   - `is_valid BOOLEAN NOT NULL DEFAULT TRUE`、`invalid_at TIMESTAMP`、`invalid_reason TEXT`
   - `status VARCHAR(16) NOT NULL DEFAULT 'VALID'`（VALID / INVALIDATED / ARCHIVED）
3. `ecos_cognitive_belief`（不确定性判断表 P 库·层3）：
   - `id VARCHAR(64) PK`，`variable_name VARCHAR(128) NOT NULL`（不可观测经营变量名）
   - `domain VARCHAR(64) NOT NULL`
   - `distribution JSONB NOT NULL DEFAULT '[]'::jsonb`（**有限离散概率分布**：`[{outcome,prob}]`，prob 和=1 约定）
   - `version INTEGER NOT NULL DEFAULT 1`、**`snapshot_version INTEGER`（时间回放预留，Phase 3 启用）**
   - `is_manual_override BOOLEAN NOT NULL DEFAULT FALSE`（人工覆写标记·专家干预）
   - `override_reason TEXT`、`last_evidence_id VARCHAR(64)`（触发本次更新的证据引用）
   - uniq: `uniq_ecos_cognitive_belief_variable_ver (variable_name, domain, version)`（时间回放=按变量+版本可重放）
   - idx: `idx_..._domain`、`idx_..._status(无效时)` 等查询热路径

## §执行与验收流程

1. PM 出本指令（已完成）
2. 派发 fullstack-implementer 实现 T1+T2+T3（后端 Java+SQL）
3. PM 亲自验证：
   - V1 文件生存检查（3 SQL + 3 VO + 2 接口文件存在）
   - V2 集成点 grep（KafkaTopics 引用、接口可编译、无 select *）
   - V3 全量 `& "D:\JavaProjects\env\apache-maven-3.9.11\bin\mvn.cmd" -f "D:\workspace\javaprojects\ECOS\ecos_backend\pom.xml" clean install "-Dmaven.test.skip=true" -q` 绿
   - V4 Gateway 启动存活（:8080 已在跑则做在线探活）+ 三表 psql 查询留痕
4. DDL 手工执行（docker cp 法，铁律 §6 Windows 变体见禁止清单 #7）
5. code-artifact-reviewer 独立审查（重点：铁律 §2.4 安全集成——三表无密钥/凭据类字段，证据 blob 外部落点不改现有脱敏链；命名规范；DDL 无删改既有表）
6. 分支 `feature/cognitive-mentalevidence-p0`（基于 `feat/ontology-workbench-wave-b` 拉取）+ 分批 clean commit，**commit hash 即 DONE 凭证**
7. T4 收口文档 + Phase 2 入口建议

## §环境要点

- Windows PowerShell 5；命令分隔 `;`；多值 -D 参数加引号
- 端口 8080 若被占：`Get-NetTCPConnection -LocalPort 8080 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }`（当前 8080 已有 gateway 进程 123644，验收复用）
- Gateway 启动：`powershell -NoProfile -ExecutionPolicy Bypass -File D:\workspace\javaprojects\ECOS\_win_tasks\start-gateway.ps1`
- DB：postgres/postgres，库 `sys_man`，容器 `ecos-postgres`（已 Up）
