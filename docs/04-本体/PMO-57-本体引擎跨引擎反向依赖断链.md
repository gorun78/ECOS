# PMO-57: 本体引擎跨引擎反向依赖断链（ontology-engine-impl → buszhi-impl）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../00-架构/ARCHITECTURE-RULES.md) —— 本指令为铁律 §0.3.1 依赖方向的违规收口专项
> 来源: 肖国荣（本体工作台 20 任务遗留项 #3，Wave D T18 审查上报 P1）
> 日期: 2026-09-13
> 责任人: fullstack-implementer
> 铁律: ① 依赖方向严格恢复 `engine-impl → engine-api → common-api`（引擎层禁止引用**任何** buszhi 服务层 artifact，含 buszhi-api）② 不改 buszhi 侧存活端点的 API 路径/签名（只增不改，§1.1）③ gateway 双跑 excludeFilters 动前必留 diff + curl 双跑验证

## §架构裁定（2026-09-13 肖国荣压实，本 PMO 的方向基准）

**分层定位**：

| 层 | 模块 | 归属 |
|:--|:--|:--|
| 引擎层 | ontology-engine（I 信息·本体） | 管"本体/实体/关系"**是什么** |
| 服务层 | services/buszhi（本体 + **工作流**，`ontology-engine-impl` + workflow 聚合） | 管"怎么流转"（审批/实例/触发） |
| 引擎层 | data-engine / datanet（D 数据） | 数据工作台定义的数据源/字段 |

**依赖方向裁定**：

1. **反向依赖必须去除**：`ontology-engine-impl → buszhi-impl`（pom + import 双实证）是引擎层引用服务层 = 反向引用，违反 §0.3.1"下层禁止 import 上层"。workflow 业务（定义/实例/审批/触发）**按铁律 0.3.1 本就归 buszhi 服务层**——本体引擎不承载任何 workflow 端点。
2. **正确方向保留**：本体（I）**映射到数据工作台定义的数据（D）**是合法业务方向（格物 ge : D→I），走 datanet/data-engine REST（同 T13 `DataNetResourceClient` 模式，铁律 §2.1 引擎间只调 API 不 import Impl）——本 PMO 不动该链路。
3. **契约上提 buszhi-api 方案废弃**（PMO-57 初稿选项）：引擎 → 服务层契约仍是反向引用，不构成合规收敛，禁止采用。

**目标终态**：
- workflow 路由单点 = buszhi 侧 `WorkflowController`（`/api/v1/ecos/workflows`）
- ontology-engine-impl 内 0 处 buszhi import、0 个 workflow Controller、pom 0 个 buszhi 依赖
- 前端零改动（buszhi 侧副本路由相同，endpoint 契约不变）

## §现状（T18 调查实证，commit `01ea1e2`）

| 项 | 事实 |
|:--|:--|
| 反向依赖 | `ontology-engine-impl/pom.xml` L28-32 依赖 `buszhi-impl`；`WorkflowController` + `OntologyWorkflowController` 均 import `com.chinacreator.gzcm.buszhi.workflow.{WorkflowService, WorkflowInstanceService, [WorkflowApprovalService]}` |
| 双副本 | 同路由 `/api/v1/ecos/workflows` 在 ontology 侧 + `services/buszhi/impl` 侧各 1 个 Controller（内容同 hash 同端点）；`OntologyWorkflowController`（`/api/v1/engine/ontology/workflow`）仅 ontology 侧有 |
| 存活侧 | gateway fat-JAR：ASSIGNABLE_TYPE 排除 buszhi 副本（**ontology 存活**——与正确归属相反，即现状把服务层职责放在了引擎层）；buszhi standalone：REGEX 排除 ontology 副本（buszhi 存活） |

## §禁止清单（继承铁律 §5.1）

1. 不跨 Phase 预创建文件
2. **禁止"契约上提 buszhi-api"式收敛**（见 §架构裁定 3）——引擎层引用 buszhi 任何 artifact 均为违规
3. 不改 buszhi 侧存活端点的 API 路径/参数签名
4. gateway excludeFilters 改动前留 diff + 双态启动 curl 验证（Ambiguous mapping 零容忍）
5. 不动 PMO-50/51/52/55 其它残留；不动 data/ D→I 映射链路（T13 等合法方向）
6. mvn install 模块级（不带 -am，防 working tree 污染）

## §Task（≤5）

| Task | 文件/路径 | 操作 | 验收 |
|:--|------|------|------|
| T1 | 端点差集 + 前端消费核查 | `git diff` 比对两侧 `WorkflowController` 端点集合（T18 证实同 hash 同端点，本步复核 T16-4 强类型改造后仍一致——ontology 侧 T16-4 改过，buszhi 侧可能未跟改，**差异必须找回 buszhi 侧补齐**）；grep 前端全部调用路径确认无依赖 ontology 侧独有端点 | 差集清单落 `docs/07-集成联调/`；buszhi 侧端点 ⊇ ontology 侧 |
| T2 | ontology 侧 workflow 物理删除 | 删 `WorkflowController` + `OntologyWorkflowController`（ontology-engine-impl 下）；若 T1 发现 buszhi 侧缺端点，先补齐再删；`OntologyWorkflowController` 的独享路径 `/api/v1/engine/ontology/workflow` 前端若有引用 → 前端同批切换至 buszhi 侧（T8 已把 domain 读源切后端，本处仅 workflow 相关） | `git grep "buszhi.workflow" ontology-engine-impl` = 0（Javadoc 溯源注释除） |
| T3 | pom 断链 | `ontology-engine-impl/pom.xml` 删 `buszhi-impl` 依赖；全模块 grep 确认无 transitive 编译依赖残留（WorkflowRepository 等 buszhi DAO 的引用必须已随 T2 删除） | `grep buszhi ontology-engine-impl/pom.xml` = 0；`mvn -pl engine/ontology-engine/ontology-engine-impl install` exit 0 |
| T4 | excludeFilters 方向翻转 | gateway `GatewayApplication`：删 ASSIGNABLE_TYPE 排除 buszhi 副本的条目（ontology 副本已不存在，排除项失效）；`assignable` 改指 buszhi 侧（复验无 Ambiguous）；`BuszhiServiceApplication` 删指向已除 ontology 副本的 REGEX 排除项；顺手修正 T18 发现的 buszhi 侧注释乱码 | 双侧启动日志 0 Ambiguous mapping；`git diff` excludeFilters 改动留档 |
| T5 | 双态双跑回归 | gateway fat-JAR 态 + buszhi standalone 态各起一次，curl `/api/v1/ecos/workflows` 全端点 2×（列表/详情/创建/测试/实例生命周期） | 双态全 200；前端 workflow 面板冒烟（调用链路切换无感） |

## §验收四步法（继承铁律 §5.4）

V1 文件生存（ontology 侧 2 Controller 已删）→ V2 集成点 grep（pom/import/excludeFilters 三点）→ V3 mvn install 模块级绿 → V4 双态启动 + curl 验收。

## §风险

- **T1 差集是成败点**：T16-4 只改了 ontology 侧 `WorkflowController` 强类型化，buszhi 侧副本若未同步 → 直接删 ontology 侧会丢强类型 VO 契约。T1 差集先行，差集跟进 buszhi 侧（同包内合并改，随 T2 同 commit）
- 方向翻转后 gateway fat-JAR 的 workflow 存活侧从 ontology → buszhi，**线上流量无感**（同路径同签名），但需 T5 双态验证排除 Bean 冲突
- 本 PMO 与 PMO-50/51 touches 交集（pom.xml 漂移），执行前需 working tree clean 协调；建议排期在 buszhi 侧无关改动收口后
