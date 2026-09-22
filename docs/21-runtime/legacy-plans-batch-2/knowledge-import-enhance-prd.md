# 知识工作台「数据导入」功能增强 — L3 PRD + 架构设计

> 依据：docs/plans/knowledge-workbench-replan-v1.md §6 K1 + .trae/rules/架构铁律.md §0.5/§2.1/§2.4/§2.5-3/§4.8
> 复杂度：L3 复杂（47/60） | 日期：2026-09-20 | 版本 v1.0

## 0. 已裁决（落地）
| # | 裁决 | 落地 |
|:--|:--|:--|
| R1 | 完整交付定时抽取 | 仿 PipelineServiceImpl.registerSchedule：taskSchedulerService.scheduleTask(desc,cron) 拿 scheduleId 持久化 |
| R2 | 全量覆盖=软覆盖 | KbEntityInstanceExtractionService 现状幂等 upsert（ON CONFLICT DO UPDATE）不物理删旧节点；audit.rows_total 记本次扫到量 |
| R3 | 参考数据工作台立即采集 | submitTask(KB_IMPORT) + 前端 2s 轮询 GET /status/{taskId} 读 TaskStatus |

## 1. 功能清单 F1~F10
F1 本体树形多选(GET /ecos/versions 三级) | F2 抽取方式toggle(立即/定时) | F3 定时策略UI | F4 定时任务管理 | F5 立即抽取异步+轮询 | F6 过程日志实时 | F7 日志导出.txt | F8 失败诊断+建议 | F9 DW/Neo4j落库校验+tier | F10 抽取审计表

## 2. 新增 DDL
### V141 kb_scheduled_extract
id/schedule_id(UNIQUE)/name/ontology_ids(JSONB)/mode/period/cron_expression/next_run_at/enabled/last_run_at/last_status/created_by/created_at/updated_at/is_deleted + idx(enabled)
### V142 kb_extract_audit
id/job_id/task_id/tier/mode/status/duration_ms/rows_total/rows_ok/rows_failed/mismatched/error_message/suggestion/created_at + idx(job_id)/idx(created_at DESC)

## 3. 新增端点（6）
N1 POST /extract/structured 改异步 {ontologyId?,mode,dryRun?}-> {taskId,jobId}
N2 GET /extract/structured/status/{taskId} -> KbImportTaskStatusVO
N3 GET /extract/structured/logs/{jobId} -> [{ts,level,msg}]
N4 GET /extract/ontology-tree -> 三级树
N5 POST /extract/scheduled CRUD(4)
N6 POST /extract/structured/export-log {jobId} -> .txt
三滤波器：/api/v1/knowledge/ 已注册，默认 DENY，token200/无403

## 4. 任务 DAG
Wave1 后端基础(并行): T1 DDL / T2 任务适配(Registrar+Executor KB_IMPORT) / T3 异步化(N1+N2) / T4 Audit落库
Wave2 后端定时+日志: T5 定时CRUD(scheduleTask+persist) / T6 日志导出 / T7 本体树
Wave3 前端: T8 api+types / T9 树形选择器 / T10 即时抽取+监控 / T11 定时管理UI / T12 tier banner

## 5. 红线
§0.5-1 DW只读不回写 | §0.5-2 契约先行不扫表 | §2.5-3 周期走runtime-task禁自建调度器 | Neo4j tier 既有Writer透传 | 默认DENY

## 6. 验收门禁
N1异步+progress推进 / 定时scheduleTask同Pipeline模式 / 本体树三级 / 导出txt三列 / audit完整落库 / 周期30s内fire / grep无new ScheduledExecutorService且无td_data_*直查 / 前端无硬编码

## 7. 用户文档
交付附 docs/plans/knowledge-import-user-guide.md
