# PMO-59 Phase 1 验收记录 — 认知引擎心智层 P0 数据契约与三表落库

> 来源: PM项目经理（PMO-59 Phase 1 收口）
> 日期: 2026-09-13
> 责任人: fullstack-implementer（开发实现）/ PM项目经理（验收）/ code-artifact-reviewer（独立审查）
> 铁律: 继承 [架构铁律](../../.trae/rules/架构铁律.md) + 本指令 §禁止清单；commit hash 即 DONE 凭证

## 1. 交付物清单（V1 文件生存检查）

| Task | 交付文件 | 状态 |
|:--|------|:--:|
| T1 | `ecos_backend/gateway/src/main/resources/db/migration/V127__ecos_cognitive_evidence.sql` | ✅ |
| T1 | `ecos_backend/gateway/src/main/resources/db/migration/V128__ecos_cognitive_hypothesis.sql` | ✅ |
| T1 | `ecos_backend/gateway/src/main/resources/db/migration/V129__ecos_cognitive_belief.sql` | ✅ |
| T2 | `runtime/common-api/.../common/cognitive/EvidenceRecordVO.java` | ✅ |
| T2 | `runtime/common-api/.../common/cognitive/HypothesisVO.java` | ✅ |
| T2 | `runtime/common-api/.../common/cognitive/BeliefDistributionVO.java` | ✅ |
| T2 | `runtime/common-api/.../common/event/KafkaTopics.java`（新增 `COGNITIVE = "ecos.cognitive"` 常量） | ✅ |
| T3 | `cognitive-engine-api/.../cognitive2/service/IUncertaintyJudgementService.java` | ✅ |
| T3 | `cognitive-engine-api/.../cognitive2/service/IHypothesisLifecycleService.java` | ✅ |
| T3 | `engine/cognitive-engine/AGENTS.md` + `cognitive-engine-api/AGENTS.md` + `cognitive-engine-impl/AGENTS.md`（红线 #2 改 ADR-9 三档口径 + 端点登记） | ✅ |
| T4 | `docs/plans/api-contract.md`（§3.4 dccheng 心智层 P0 注记 + §5.1 Kafka topic 登记） | ✅ |
| T4 | 本验收记录 | ✅ |

## 2. 验证四步法记录

### V2: 集成点 grep
- `grep -i "select \\*" gateway/db/migration/`：V127~V129 三文件 0 命中（命中均为 V1~V8 历史 seed 惯例 `SELECT * FROM (VALUES`，非本批次产物）
- `grep "ecos.cognitive" --type=java`：仅 `KafkaTopics.COGNITIVE` 常量定义 + `IUncertaintyJudgementService` Javadoc 引用，无意外生产/消费代码（符合"本 Phase 仅契约"边界）
- 依赖方向核验：cognitive-engine-api POM 既有 common-api 依赖 → 新接口 import `com.chinacreator.gzcm.common.cognitive.*` 合法（engine-api → common-api 铁律 §0.3.1）

### V3: 全量编译（V3 编译门）
命令：`& "D:\JavaProjects\env\apache-maven-3.9.11\bin\mvn.cmd" -f "D:\workspace\javaprojects\ECOS\ecos_backend\pom.xml" clean install "-Dmaven.test.skip=true" -q`

| 轮次 | 结果 | 说明 |
|:--:|:--|------|
| ① | ❌ `Failed to clean project: Failed to delete gateway-1.0.0-SNAPSHOT.jar` | 旧 gateway 进程 123644（2026-09-13 20:05 启动的旧 JAR）锁住 target 产物 → 清进程后重跑 |
| ② | ✅ **BUILD SUCCESS**（`MVN_EXIT=0`，`-q` 静默零输出） | 17 模块全绿；`-Dmaven.test.skip=true` 隔离 kb-engine-impl 既有测试编译错误（指令铁律 #4）。起止 23:08:32 → 23:32（约 24 min），新 `gateway-1.0.0-SNAPSHOT.jar` LWT=09/13 23:32:20 |

### V4: Gateway 启动 + 存活
- 清端口旧进程：`Stop-Process -Id 123644 -Force`（实测执行，`OLD_GATEWAY_STOPPED=True`）
- 启动：`powershell -NoProfile -ExecutionPolicy Bypass -File D:\workspace\javaprojects\ECOS\_win_tasks\start-gateway.ps1`（独立后台终端）
- 探活（启动后 ~126s）：
```
GET http://localhost:8080/actuator/health → {"status":"UP"}
```
- 既有认知端点无回归冒烟：
```
GET http://localhost:8080/api/v1/cognitive/models → HTTP=200
{"code":0,"message":"ok","data":[{"id":"model_fcst_baseline_1","model_id":"baseline-linear","model_type":"FORECAST","version":1,...,"status":"active"}],"success":true}
```
（本 Phase 未动任何既有 API，该冒烟证明 gateway 新 JAR 加载后路由/DB 无回归。）

### V4-DB: 三表 psql 验证（docker cp 法执行 DDL 后）

DDL 执行（`-v ON_ERROR_STOP=1`，全部成功）：
```
V127: CREATE TABLE / 4x CREATE INDEX / 5x COMMENT / INSERT 0 2
V128: CREATE TABLE / 4x CREATE INDEX / 5x COMMENT / INSERT 0 1
V129: CREATE TABLE / 3x CREATE INDEX / 7x COMMENT / INSERT 0 2
```

`\dt ecos_cognitive_*`：
```
 Schema |           Name            | Type  |  Owner
--------+---------------------------+-------+----------
 public | ecos_cognitive_belief     | table | postgres
 public | ecos_cognitive_evidence   | table | postgres
 public | ecos_cognitive_hypothesis | table | postgres
 public | ecos_cognitive_model      | table | postgres
(4 rows)
```

证据表 seed：
```
        id        |  evidence_code  | source_type | confidence | is_conflict |   status
-----------------+-----------------+-------------+------------+-------------+------------
 cog_ev_seed_001 | EV-20260913-001 | SYSTEM_DATA |     0.9800 | f           | ACTIVE
 cog_ev_seed_002 | EV-20260913-002 | NEWS        |     0.8000 | t           | CONFLICTED
(2 rows)
```

假设表 seed：
```
         id        |  hypothesis_code  |    domain    | is_valid | status |    evidence_ids
------------------+-------------------+--------------+----------+--------+---------------------
 cog_hyp_seed_001 | HYP-20260913-001  | supply-chain | t        | VALID  | ["cog_ev_seed_001"]
(1 row)
```

不确定性判断表 seed（2 版本递进 + 证据溯源）：
```
          id       |       variable_name       | domain  | version | snapshot_version | is_manual_override | last_evidence_id
------------------+---------------------------+---------+---------+------------------+--------------------+------------------
 cog_blf_seed_001 | competitor_price_cut_prob | pricing |       1 |                1 | f                  |
 cog_blf_seed_002 | competitor_price_cut_prob | pricing |       2 |                1 | f                  | cog_ev_seed_001
(2 rows)
```

## 3. 铁律合规自检
- `select *`：本批次 SQL 0 命中 ✅
- DDL 只加不删：3 张新表 + 新增索引，0 触碰既有表/列/索引 ✅
- 审计列齐全：三表均含 id/create_time/update_time/create_by/update_by/is_deleted ✅（`tenant_scope` 可选列按指令口径）
- 索引命名：uniq_/idx_ 前缀合规 ✅；JSONB 字段带 `DEFAULT` ✅
- 不新增 Maven 模块 / Docker 容器：0 ✅
- LLM 零参与：本 Phase 无 LLM 调用 ✅
- 铁律 §2.4 安全集成卡：三表**无密钥/凭据/密码/Token 类字段**（evidence.source_ref 仅为来源定位标识，非凭据），不触发"落库前加密/读取解密/响应脱敏"强制卡；外部来源 URL 类字段的敏感暴露由 Phase 2 端点上线时统一走 security-engine 列过滤/脱敏（api-contract.md 已预登记三滤波器要求）

## 4. Gateway 存活证据
见 §2 V4 节原文（actuator/health UP + `/api/v1/cognitive/models` 200 无回归冒烟）。

## 5. 独立审查结论（code-artifact-reviewer）

**审查结果：PASS**（分支 `feature/cognitive-mentalevidence-p0` @ 98f5cf4，审查范围=本批次 8 交付文件，0 高危）。

| 维度 | 结论 |
|------|------|
| 铁律 §2.4 安全集成 | PASS——三表无密钥/凭据/密码/Token 类字段（`evidence.source_ref` 仅来源定位标识），不触发 §2.4#8 加密强制卡；外部 URL 类数据的安全暴露由 Phase 2 端点上线时统一走 security-engine 列过滤/脱敏（api-contract 已预登记三滤波器） |
| 架构依赖方向 | PASS——common-api 新增包 enforcer 通过（无业务反向依赖）；cognitive-engine-api import common-api VO 符合 `engine-api → common-api` 铁律 §0.3.1；0 处 import `*-engine-impl` |
| 命名规范 | PASS——表/字段全小写下划线、审计六列齐全、uniq_/idx_ 前缀、JSONB 带默认值；VO/接口 PascalCase+camelCase，全英文语义化 |
| DDL 合规 | PASS——`IF NOT EXISTS` 幂等、`ON CONFLICT DO NOTHING`、0 删改既有表/列/索引、0 `select *`、seed 可重复执行 |
| ADR-9 口径一致性 | PASS——cognitive-engine 顶层/api/impl 三处 AGENTS.md 红线 #2 同步改为三档落盘口径，口径一致可追溯 |

低风险备注（不阻塞，Phase 2 处理）：
1. `ecos.cognitive` topic 无 Kafka 配置预注册（Kafka 首次 publish 自动建 topic），Phase 2 consumer 接入时定 `group.id`
2. belief.distribution 概率和=1 无 DB CHECK（JSONB 约束局限），Phase 2 Service 层注册时强校验

## 6. commit hash 凭证（DONE 凭证，按批次）
| 批次 | commit | 内容 |
|:--:|:--|------|
| 0 | `af15d92` | `docs(认知引擎): PMO-59 P0 数据契约指令文档 + 外部方案归档(信念->不确定性判断术语修订)` |
| 1 | `028fa63` | `feat(认知引擎): PMO-59-P1 心智层三表 DDL — evidence/hypothesis/belief (ADR-9 口径, 含 seed)` |
| 2 | `8fc00e4` | `feat(认知引擎): PMO-59-P1 common-api cognitive VO 契约 (EvidenceRecordVO/HypothesisVO/BeliefDistributionVO) + KafkaTopics.COGNITIVE` |
| 3 | `98f5cf4` | `feat(认知引擎): PMO-59-P1 心智层 Service 接口契约 (IUncertaintyJudgementService/IHypothesisLifecycleService) + AGENTS ADR-9 三档落盘口径登记` |
| 4 | 本文档随本批次提交（溯源：`git log --grep "PMO-59"`） | `docs(认知引擎): PMO-59-P1 api-contract 心智层契约登记 + Phase1 验收记录` |

分支：`feature/cognitive-mentalevidence-p0`（基于 `feat/ontology-workbench-wave-b` @ `f0b15b7` 拉取）
溯源命令：`git log --grep "PMO-59" --oneline`

## 7. 已知残留风险
1. `architecture-rules` 铁律 §0.3 表格与 §3.3 条文仍写"cognitive-engine 不新增 DB 表"原文（未本 Phase 修订源文件，仅三处 AGENTS.md 登记 ADR-9；铁律源文件修订留待 Phase 2 正式 ADR 落盘时同步，当前 AGENTS 登记可追溯）
2. `ecos.cognitive` topic 仅常量登记：Kafka 实际 topic 随首次 publish 创建，Phase 2 接入 consumer 时需配 `group.id`（建议 `dccheng-cognitive-consumer` 模式，防多业务共用）
3. `distribution` 概率和=1 约定无数据库级 CHECK 约束（JSONB 动态结构，DB 层难约束）→ Phase 2 Service 层注册/更新时强制校验（`IUncertaintyJudgementService` Javadoc 已声明）
4. belief 表 `variable_name` 全局命名空间：跨 domain 同名变量由唯一索引 `(variable_name, domain, version)` 隔离，但应用层查询必须带 domain（Javadoc 已声明必填）

## 8. Phase 2 入口建议
- **T03 心智层语义化首单**（用户点名）：Phase 2 第一项 = 认知心智层数值管线的**语义化绑定**——将 belief.variable_name/metric_ref 与本体指标体系（`/api/v1/dccheng/metrics` 对齐）建立正式映射，使"竞品降价概率"类变量可挂接本体实体（Cell/Rule Binding 模式，参考 ADR 本体级联）
- 实施顺序建议：① 三表 MyBatis Mapper + 3 Controller（api-contract 已预登记端点，走三滤波器）② 效用序列 MCP 工具端点（semantic/utility 走 common-api 接口注入模式，断 workspace↔cognitive DB 直连）③ 新证据 Kafka `ecos.cognitive` 事件生产 + runtime-task 定时补算 ④ 假设失效检测 + 告警（预留周一故障复盘接入点）
