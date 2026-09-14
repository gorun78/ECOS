# PMO-48-C: 监控调度 + 告警分发 + 工单状态机（Phase 3）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../.trae/rules/架构铁律.md)
> 来源: 肖国荣
> 日期: 2026-09-10
> 上游方案: 数据质量治理方案 §3（监控）+ §5（告警工单）
> 子指令: PMO-48-B（Phase 2 已落地状态机 + 评分引擎）
> 状态: **待执行**
> 铁律:
>   1. 监控调度走 runtime-task，不自建调度（铁律 2.5 #3）
>   2. 告警走 runtime-core `IAlertService`，不直连 IM/Kafka（决策 #6/#7）
>   3. 告警落 PG `ecos_dq.dq_alert_record` + 工单 `dq_work_order`（决策 #2 P0-P3 全落库）
>   4. 仅低危自动修复（决策 #5），白名单守卫
>   5. 安全卡：mask + audit 命中 ≥ 1
>   6. 不跨 Phase 预建 Phase 4 文件（报告 Thymeleaf/知识库）

---

## §背景

Phase 2 已就绪：规则状态机（6 动作）+ 6 维评分 SPI + Score API + `dq_rule_check`/`dq_alert_record`/`dq_work_order` 表。

**缺**：监控调度器（谁在何时触发 `DqRuleExecutor`？）、告警分发器（`dq_rule_check.passed=false` 怎么转 `dq_alert_record` + `IAlertService`？）、工单状态机（工单怎么派给值班 + 自动修复白名单？）。

### 关键集成点（已存在，本波接上）
- `runtime-task`：`DqScheduledTask`（Phase 1 已存在，每天 08:00 cron）
- `runtime-core` `IAlertService.triggerAlert(level, title, message, metadata)`
- `runtime-monitor` `countOpenDqIssues()`（已有，喂仪表盘）
- `ecom.alerts.WebHookSender` / `SmsSender`（Phase 1 已存在，决策 #6 由 runtime 统一实现）

## §Task（Phase 3 共 4 Task）

| Task | 文件/路径 | 操作 |
|:--|:--|:--|
| **T11 监控调度器 + 规则执行** | `data-engine-impl/.../quality/scheduler/DqScheduleExecutor.java` + `DqScheduleServiceImpl.java` + `DqScheduleController.java`（`/api/v1/dq/schedules` CRUD）+ 新表 `ecos_dq.dq_restriction`（限流配置）+ 新表 `ecos_dq.dq_watch`（纳入监控的资产） | ① `DqSchedule` entity 接 runtime-task；② 3 种触发源：SCHEDULE（cron）/EVENT（`PIPELINE_EXECUTION_SUCCEEDED` via IntegrationListener）/MANUAL（API 触发）；③ `DqRuleExecutor.runRuleBatch(scheduleId)` 拉 ACTIVE 规则 + 拉 `dq_rule_check` 最近 1h 数据 → 调 `DqScoreEngine` 评分 → 写 `dq_score_snapshot`/`dq_score_asset` + 写 `dq_rule_check`；④ 限流：每资产每天 `max_rules_per_day`（默认 100）防雪崩 |
| **T12 告警分发器 + 工单创建** | `data-engine-impl/.../quality/alert/DqAlertDispatcher.java` + `DqAlertService.java` + `DqWorkOrderService.java` + `DqAutoRepairService.java`（仅低危） | ① `DqAlertDispatcher.dispatch(alertLevel, rule, check)` 判重（同 rule_id 同 severity 5min 窗口 notify_count++）→ 写 `dq_alert_record`（P0/P1/P2/P3 都落库）→ `securityEngine.mask(colist)` 脱敏 payload → `IAlertService.triggerAlert`（runtime 统一推送）→ P0/P1 自动创建 `dq_work_order`；② `DqAutoRepairService.tryRepair(workOrder)`:白名单 `[断连重试 pipeline=3次, pipeline retry 已配置]` → 调 `PipelineExecutor.execute` 重跑 → 重跑相关 ACTIVE 规则 → 写 `repair_log` JSONB；③ 白名单校验：`rule.severity ∈ {CRITICAL,HIGH}` + `handling_mode=MANUAL` 否则走人工工单 |
| **T13 工单流转 + RCA 留接口** | `DqWorkOrderController.java`（`/api/v1/dq/work-orders` CRUD + 状态转换）+ 状态机 validator + `DqRcaTrigger.java`（Phase 3 占位，Phase 4 接 cognitive） | ① 工单状态机：PENDING→ASSIGNED→IN_WORK→RESOLVED→VERIFIED→CLOSED（+REJECTED）；② 分配策略：P0/P1 自动分配 oncall，P2/P3 创建者认领；③ 升级：P2 5min 未 ack → P1，P1 15min 未 ack → P0；④ `retry_count` 限制 3 次，超过强制人工；⑤ `DqRcaTrigger` 占位（P0/P1 工单 IN_WORK 时调 cognitive `POST /api/v1/cognitive/diagnose`，本波只留 stub + TODO，authentication 走 `IAlertService` mock） |
| **T14 监控面板 + 工单中心前端** | 改 `RuleCenterTab.tsx`（资产卡片加「监控中」/「告警 P0」徽章）+ 新增 `data-quality/AlertCenterTab.tsx`（告警列表 + ack/resolve）+ 新增 `data-quality/WorkOrderTab.tsx`（工单列表 + 状态徽章 + 分派 + 重试）+ `api.ts` 加 6 函数（schedules CRUD + workOrders CRUD + alerts list/ack）+ i18n `dw.dqAlert.*`/`dw.dqWorkOrder.*` | 前端 4 个新 Tab：规则中心 / 6 维度 / 监控调度 / 告警中心 / 工单中心（共 5 Tab）；SLO 倒计时 VUE 实现 10s 轮询 |

## §工期

| Task | 人日 |
|:--|:--:|
| T11 监控调度 | 3 |
| T12 告警分发 + 工单创建 | 3.5 |
| T13 工单流转 + RCA 占位 | 2.5 |
| T14 前端 4 Tab | 3 |
| 合计 | 12 人日 |

## §依赖

```
T11 (Schedules) ─┬→ T12 (AlertDispatcher) ─┬→ T13 (WorkOrder)
                 │                          ├→ T14 (前端)
                 └→ T12 (AlertDispatcher)   │
                                            └→ T16 (IT，Phase 3 自带)
```

**分发策略**：
1. 第 1 波：T11 单独跑（建 `dq_restriction`/`dq_watch` 表 + 调度器）
2. 第 2 波：T11 完成后 T12 单跑（依赖 T11 的调度器产出 `dq_rule_check` 行）
3. 第 3 波：T12 完成后 T13 单跑
4. 第 4 波：T13 完成后 T14 前端
5. 每波完跑 curl + 浏览器 E2E

## §安全自检卡

| 卡 | 阈值 |
|:--|:--:|
| `audit` 在 DqAlertDispatcher + DqWorkOrderService | ≥ 6 |
| `mask` 在 DqAlertDispatcher | ≥ 1（告警 payload 脱敏） |
| `IAlertService` 不直连 IM | ≥ 1 |
| `PipelineExecutor`/`JdbcTemplate` 不 new | = 0（构造器注入） |

## §风险

| 风险 | 概率 | 缓解 |
|:--|:--|:--:|
| 调度器高频触发拉爆存储 | 中 | 限流表 `dq_restriction` + 单资产每天硬顶 |
| 自动修复误杀数据 | 高 | 白名单仅 2 场景 + severity IRULA checker |
| 工单升级风暴 | 中 | 升级只 1 次（P2→P1）不循环 |
| `PIPELINE_EXECUTION_SUCCEEDED` 事件丢 | 中 | 每日 08:00 全量巡检兜底 |
| Kafka 接入要求 runtime-core 已有 | 低 | 本波不接 Kafka（决策 #7 runtime 统一） |

## §Task 验收

### T11
- curl `POST /api/v1/dq/schedules` 建一个 cron `0 0 8 * * *` 的 schedule
- 手动触发 `POST /api/v1/dq/schedules/{id}/-trigger` → 查 `dq_rule_check` 新增 ≥ 1 行
- 资产限流生效：连建 101 个规则 → 第 101 个被 429
- 编译 `mvn install -DskipTests -pl data-engine-impl -am` 通过

### T12
- 造 1 条 `dq_rule` ACTIVE + 1 条 `dq_rule_check` passed=false → alert_level=P1
- curl `GET /api/v1/dq/alerts?level=P1` 返 1 条
- 自动创建 `dq_work_order` 验 `severity=HIGH` + `handling_mode=AUTO_REPAIR`
- 触发 `DqAutoRepairService.tryRepair`（mock pipeline retry 配置已启用）→ `repair_status=SUCCESS`
- Grep `IAlertService` 调 `triggerAlert` ≥ 1；Grep `IAlertService` 无直接 webhook/IM 调
- 编译通过

### T13
- 1 条 `dq_work_order` PENDING → `POST /api/v1/dq/work-orders/{id}/assign?to=u001` → ASSIGNED
- `POST /api/v1/dq/work-orders/{id}/start` → IN_WORK
- `POST /api/v1/dq/work-orders/{id}/resolve` → RESOLVED
- `POST /api/v1/dq/work-orders/{id}/verify` → VERIFIED
- `POST /api/v1/dq/work-orders/{id}/close` → CLOSED
- 5min 内未 ack 自动升级 P2→P1（mock clock）
- `retry_count=3` 强制人工（handling_mode=MANUAL）
- DqRcaTrigger 留占位 TODO，桩返 501
- 编译通过

### T14
- 浏览器 `http://localhost:3000/#/dq_dashboard` 4 个 Tab 切换正常
- 告警中心 Tab：列表 + ack + resolve 按钮全通
- 工单中心 Tab：SLO 徽章 10s tick + 分派下拉 + 重试按钮
- tsc --noEmit 通过
- 文件行数每 Tab < 800

## §产出物
- 后端：3 个新 Controller + 3 个新 Service + 2 个新表（V113）+ 调度器 + 工单状态机
- 前端：2 个新 Tab + api.ts +8 函数 + i18n 30+ key
- `data-engine/AGENTS.md` 追加「DQ 监控调度 + 告警分发 + 工单中心 API」段

---

> 注：Phase 3 完成后 Phase 5 E2E 用真数据联调：Pipeline 完成 → 触发评估 → 写 check → 告警 → 工单 → 自动修复 → 验证，全流程浏览器可观察。JaCoCo 覆盖率门禁 T10 补 ~300 case 后预期达调试（25% 阈值）。
