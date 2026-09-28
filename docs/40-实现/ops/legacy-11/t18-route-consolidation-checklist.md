# T18 — workflow 双路径/多副本路由收敛 运维清单

> **元信息**
> - 来源: 肖国荣（PMO Wave D T18 派发）/ 撰写: fullstack-implementer
> - 日期: 2026-09-12（调查 + 登记，2026-09-13 复核）
> - 责任人: fullstack-implementer
> - 状态: **登记完成 · 物理下线待前置条件**（本批仅登记 + 标注 + 兼容期决策，零逻辑/路由/gateway 改动）
> - 跟踪 commit: 见本清单提交记录（`git log --grep "T18 双路径收敛登记"`）

---

## 1. 三副本清单（实际核查：Java 代码仅 2 副本，第 3 为文档登记项）

问题描述中的"3 个文件"经 `git grep -n "api/v1/ecos/workflows" -- ecos_backend/` 与 `Glob **/WorkflowController.java` 双通道核查，结论如下：

| # | 文件 | FQCN | 路由 | tracked? | 状态 |
|:--|------|------|------|:--:|------|
| 1 | `ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/controller/WorkflowController.java` | `com.chinacreator.gzcm.engine.ontology.controller.WorkflowController` | `/api/v1/ecos/workflows` | ✅ tracked | **ontology 侧副本**（Wave B T16-4 强类型化，类头已加 T18 兼容期标注） |
| 2 | `ecos_backend/services/buszhi/impl/buszhi-impl/src/main/java/com/chinacreator/gzcm/buszhi/workflow/controller/WorkflowController.java` | `com.chinacreator.gzcm.buszhi.workflow.controller.WorkflowController` | `/api/v1/ecos/workflows` | ✅ tracked | **buszhi 侧副本**（P8-B 迁移后正式位） |
| 3 | 顶层 `ecos_backend/buszhi/` 旧目录 | — | — | — | **目录已不存在**：P8-B 已整体迁入 `services/buszhi/impl/`（2026-09-11，与架构铁律 v1.1 P8-B 记录一致），无残留 Java 副本，无需处置 |

> 说明：`git grep` 另命中 `AGENTS.md` ×2（端点清单/被调用方登记）与 `sysman-impl/.../AuditAspect.java:352`（javadoc 示例），均非 `@RequestMapping` 命中，不构成路由副本。

## 2. 相邻端点（不属本清单收敛范围，仅登记防误删）

- `com.chinacreator.gzcm.engine.ontology.controller.OntologyWorkflowController`（ontology-engine-impl，T16-2）：路由 `/api/v1/engine/ontology/workflow` + `/api/engine/ontology/workflow`（双前缀），definitions/instances/approve 端点组，与 `/api/v1/ecos/workflows` **无路由冲突**，两 Controller 均委托 buszhi 侧 service，**不属于双路径收敛对象**。

## 3. 双跑风险分析（component-scan 调查结论）

| 部署形态 | 启动类 | 扫描范围 | 冲突处置 | 存活副本 | Ambiguous mapping |
|:--|:--|:--|:--|:--|:--:|
| **gateway fat-JAR（:8080，当前生产形态）** | `GatewayApplication` | basePackages 含 `com.chinacreator.gzcm.buszhi` + `com.chinacreator.gzcm.engine`（**两副本 class 同入扫描**） | `excludeFilters` ASSIGNABLE_TYPE 排除 `com.chinacreator.gzcm.buszhi.workflow.controller.WorkflowController.class`（第 60 行，注释"本体引擎已接管（阶段3），排除buszhi侧副本"） | **ontology 副本存活** | 无（被 exclude 规避） |
| **buszhi 微服务（:18083，P2-3 目标态）** | `BuszhiServiceApplication` | basePackages 含 `com.chinacreator.gzcm.engine.ontology` + `com.chinacreator.gzcm.buszhi`（两副本同名简单类 `WorkflowController` class 均在 classpath） | `excludeFilters` REGEX 排除 `com\.chinacreator\.gzcm\.engine\.ontology\.controller\.WorkflowController`（约第 56 行） | **buszhi 副本存活** | 无（被 exclude 规避） |

**结论**
1. 单一形态启动时 Ambiguous mapping **不会真实触发**——两侧启动类各自已用 excludeFilters 排掉对方副本（互斥排除，与 OagController/CognitiveConfigController 的 PMO-55 E-A 处置模式同构）。
2. 但**两套 exclude 排除方向相反**：fat-JAR 态 ontology 副本是活端点，微服务态 buszhi 副本是活端点——同一前端路径 `/api/v1/ecos/workflows` 在两形态下由不同 FQCN 承载，回归/验收时不可互认，必须有 live check。
3. **理论风险保留**：任何一侧后续新增 Controller/调整扫描包时若误删对应 exclude 项，即触发 Spring Ambiguous mapping 启动失败。物理收敛是唯一消除该风险的终态。
4. buszhi 侧启动类 exclude 注释存在乱码（疑似编码事故）："保留, 未握有 ontology.workflow 重类 Bean 冲突 → 墦 故 2026-09-11 映卿 u0e561513MAP2 / Ant cdeclArloy two compccouridsswabe—inmnrisk..."——**建议下次触碰该类时顺手修正注释**（本批不动，属 buszhi 红线范围）。

## 4. P1 上报：跨引擎依赖（违反铁律 §0.3.1 依赖方向）

- ontology 侧 `WorkflowController` 与 `OntologyWorkflowController` 均 import buszhi service 类：
  - `com.chinacreator.gzcm.buszhi.workflow.WorkflowService`
  - `com.chinacreator.gzcm.buszhi.workflow.WorkflowInstanceService`
  - `com.chinacreator.gzcm.buszhi.workflow.WorkflowApprovalService`（仅 OntologyWorkflowController）
- `ontology-engine-impl/pom.xml` 直接依赖 `buszhi-impl`（artifact 级反向依赖：engine-impl → service-impl，正确方向应为 service → engine-impl 或共享契约上提 common-api/buszhi-api）。
- **定性**：引擎层 import 服务层，违反架构铁律依赖方向（`services/* → engine-impl → engine-api → common-api`，横向/反向依赖拒绝）。本期**只登记上报，不修**；修治方向二选一：
  a) workflow service 契约上提（`buszhi-api` 或 common-api），本体侧只依赖契约；
  b) 物理收敛后 workflow 端点整体归 buszhi，ontology 侧删除文件，反向依赖自然断链（与下线联动，推荐）。

## 5. 物理下线前置条件（全满足才执行）

| # | 前置条件 | 验证方式 |
|:--|------|------|
| 1 | 收敛目标方向**先定**：T18 原计划为"单点归 buszhi-impl、ontology 侧下线"，但当前 gateway excludeFilters 方向是"ontology 副本存活"（阶段3"本体引擎已接管"）。**必须先核查线上实际承载（网关 access log / gateway 侧流量）再定方向**，禁止拍脑袋 | 日志/grep 流量统计 |
| 2 | buszhi 聚合 E2E 全绿（buszhi-boot 独立启动跑 workflow 全端点 curl 验收） | curl 全套端点 + code/success 断言 |
| 3 | gateway live check 双跑验证（选定存活侧后，fat-JAR 与微服务形态分别 curl 回归） | V4 验证四步法 |
| 4 | ontology 侧端点 0 流量确认（下线对象侧连续观察窗口无请求） | 网关日志 |

## 6. 物理下线操作 4 步（方向按前置条件 1 裁定；示例：ontology 侧下线）

1. 删除 `engine/ontology-engine/ontology-engine-impl/.../controller/WorkflowController.java`（连同仅被其引用的 T16-4 专属 DTO 核删：`WorkflowListVO`/`WorkflowValidationSaveDTO`/`WorkflowValidationVO`/`WorkflowTestVO`/`WorkflowPreviewVO`/`WorkflowExportVO`——须逐一 grep 引用确认孤儿后方可删，`OntologyWorkflowVO` 等 OntologyWorkflowController 共用 DTO 不动）；
2. gateway `GatewayApplication.excludeFilters` 核对：ontology 副本已不在 classpath，删除第 60 行的 buszhi `WorkflowController` ASSIGNABLE_TYPE 排除项，使 buszhi 副本恢复存活（**注意**：此项变更与下线为同一原子提交，不可拆开）；
3. 全量编译 `mvn install -DskipTests`（P8 策略：保 artifact 稳定，ontology-engine-impl JAR 瘦身，gateway/微服务两侧 classpath 重算）；
4. curl 验证 `/api/v1/ecos/workflows` 列表端点存活（看 `code` + `success`，非仅 HTTP 200）。

## 7. 回滚方案

- 下线提交独立、不夹带（Git 提交规范：clean commit、前后端分离、git log --grep 可溯源）；
- 回滚 = `git revert <下线commit>` + `mvn install -DskipTests` 重建 JAR + 两侧 live check 复验；
- 回滚窗口内 gateway excludeFilters 随 revert 自动还原，无手工残留清理项。
