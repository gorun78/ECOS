# PMO-57: 本体引擎跨引擎反向依赖断链（ontology-engine-impl → buszhi-impl）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../00-架构/ARCHITECTURE-RULES.md) —— 本指令为铁律 §0.3.1 依赖方向的违规收口专项
> 来源: 肖国荣（本体工作台 20 任务遗留项 #3，Wave D T18 审查上报 P1）
> 日期: 2026-09-13
> 责任人: fullstack-implementer
> 铁律: ① 依赖方向必须恢复 `engine-impl → engine-api → common-api`（禁止 engine-impl → 其它 service-impl）② 不改已有 API 路径（只增不改，§1.1）③ gateway 双跑 excludeFilters 60+ 项动前必留 diff 记录

## §背景

本体工作台 Wave D T18（commit `01ea1e2`）审查发现：

1. **反向依赖（P1 在案）**：
   - `engine/ontology-engine/ontology-engine-impl/pom.xml` L28-32 直接依赖 `buszhi-impl` artifact
   - `WorkflowController` + `OntologyWorkflowController` 均 import `com.chinacreator.gzcm.buszhi.workflow.{WorkflowService, WorkflowInstanceService, [WorkflowApprovalService]}`
   - 违反铁律 §0.3.1：`services/* → engine-impl` 合法，**反向 engine-impl → service-impl 拒绝**
2. **同路由双副本**：`/api/v1/ecos/workflows` 在 ontology 侧与 buszhi 侧各有一个 `WorkflowController`（FQCN 不同，均 tracked）；gateway ASSIGNABLE_TYPE 排除 buszhi 副本（ontology 存活），BuszhiServiceApplication REGEX 排除 ontology 副本（buszhi 存活）——**两侧排除方向相反**，当前 fat-JAR 态 ontology 副本存活，与"单点归 buszhi"的直觉方向相反
3. 处置约束登记于 `docs/11-运维/t18-route-consolidation-checklist.md`（T18 产物：副本清单 / 双向互斥分析 / 下线 4 前置 / 下线 4 步 / 回滚方案）

**本 PMO 目标**：消灭反向依赖，workflow 路由单点化。

## §禁止清单（继承铁律 §5.1）

1. 不跨 Phase 预创建文件
2. 不改 `/api/v1/ecos/workflows` 与 `/api/v1/engine/ontology/workflow` 路径与参数签名（只增不改）
3. 物理解删 Controller 前必须过前置条件核查（checklist §4 四条）
4. gateway excludeFilters 改动前留 diff + 双跑 curl 验证
5. 不动 PMO-50/51/52 其它残留
6. mvn install（按模块级，不带 -am 防 working tree 污染）

## §Task（≤5）

| Task | 文件/路径 | 操作 | 验收 |
|:--|------|------|------|
| T1 | 线上流量方向核查 | 查 gateway access log / arthas trace（Runtime 窗口）确认 `/api/v1/ecos/workflows` 实际承载侧（ontology vs buszhi） | 核查记录落 `docs/07-集成联调/`，明确下线对象名单 |
| T2 | 契约上提 buszhi-api | `WorkflowService`/`WorkflowInstanceService`/`WorkflowApprovalService` 接口声明迁 `buszhi-api`（impl 留 buszhi-impl），或整体随物理收敛断链（二选一，以 T1 结果定方向） | `git grep "import.*buszhi.workflow" ontology-engine-impl` = 0 或仅指 buszhi-api 包 |
| T3 | ontology-engine-impl pom 断链 | 删 `buszhi-impl` 依赖；若 T2 走上提则改依赖 `buszhi-api` | `mvn -pl engine/ontology-engine/ontology-engine-impl install` exit 0；`grep buszhi-impl ontology-engine-impl/pom.xml` = 0 |
| T4 | 物理副本下线（按 T1 方向） | 删下线侧 WorkflowController 全部方法/文件 + gateway excludeFilters 对应条目核对 | 双侧启动均无 Ambiguous mapping；curl 存活侧 12 端点 × 2 侧全 200 |
| T5 | 双跑回归 | gateway fat-JAR 态 + buszhi standalone 态各起一次 | 两套启动 workflow 端点全活，日志无 Ambiguous mapping warn |

## §验收四步法（继承铁律 §5.4）

V1 文件生存 → V2 集成点 grep（import/pom/excludeFilters）→ V3 mvn install 模块级绿 → V4 Gateway 启动 + curl 验收（双侧）。

## §风险

- T1 若无法拿到线上流量数据（Gateway 离线），默认方向 = **下线 ontology 侧副本、单点归 buszhi**（铁律 §0.3.1 守卫方向），但需你二次确认
- buszhi 侧 exclude 注释有乱码（T18 发现），T4 触碰该文件时顺手修正编码
- 本 PMO 与 PMO-50/51 的 touches 有交集（pom.xml 漂移），执行前需 working tree clean 协调
