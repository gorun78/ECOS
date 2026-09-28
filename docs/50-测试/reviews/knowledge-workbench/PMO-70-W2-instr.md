# PMO-70：知识工作台 W2 — 本体契约硬化 + 抽取执行接入 runtime-task

> **架构铁律**: 必须遵循 [ECOS 架构铁律](../../.trae/rules/架构铁律.md) §1.6 (2026-09-22 新增即时/定时接入)
> 来源: 肖国荣 | 日期: 2026-09-22
> 铁律: ① 即时抽取必须 `submitTask + executeTask` 同步（禁业务自造 jobId 当运行中 ID）② `buildNodeId` 必须全量 SHA-256 含 ontologyId（禁 20 字符截断撞车）③ 不另建任务表、不复序 `kb_scheduled_extract`
> 参考 task card: `docs/30-cross-cutting-docs/reviews/knowledge-workbench/w2w3-taskcard-2026-09-22.md` TA-1 / TA-2

## §背景

W0~W3 上线后自审（见 task card）。两条 P0：
- TA-1 抽取走业务 `KbImportTaskExecutor` 直接 `extract()` → **不进 runtime-task**，`AsyncTaskCenterView` 看不到 / MonitorPanel 拉不到 logs
- TA-2 `buildNodeId` 截 `entityType` 到 20 字符 → 同 20 字符 prefix 的两个实体走 hash 40 前缀可能碰撞

## §禁止清单（继承 §5.1 + 本指令特有）

1. 不动 `kb_scheduled_extract / kb_sync_job`（**留 W3 的 TB-1 处理**，不要顺带做）
2. 不动 `KbImportTaskExecutor` 现有逻辑（只改 `StructuredExtractController` 入口 + `KbEntityInstanceExtractionService` id 生成）
3. 不引入新表（除非业务必需——本指令不需要）

## §Task

| Task | 文件 | 操作 | 验收 |
|:--|:--|:--|:--|
| T1 | `engine/kb-engine/kb-engine-impl/src/.../controller/StructuredExtractController.java` | `dryRun=false` 路径改：建 `TaskDescription(KB_IMPORT, params={ontologyId,mode,biz_kind=KB_EXTRACT}, async=false)` → `taskManagementService.submitTask + executeTask(taskId)` → 回 `KbImportTriggerVO.taskId=<taskId>, jobId=<taskId>, status="SUBMITTED"` | `curl -X POST http://localhost:8080/api/v1/knowledge/extract/structured -d '{"dryRun":false,"mode":"FULL"}'` 回 `taskId` 非 `KBK1S-` 前缀 |
| T2 | `engine/kb-engine/kb-engine-impl/src/.../controller/StructuredExtractController.java` | `dryRun=true` 路径**保持**（同步短跑）；`logs/export-log/jobs` 端点用 `taskId` 反查 runtime-task log 表 | `dryRun=true` 回 `EntityInstanceExtractionReportVO`（含 nodeCreated/invalidMappings 等） |
| T3 | `service/KbEntityInstanceExtractionService.java#buildNodeId / buildEdgeId` | 改：`id = sha256(ontologyId + "::" + nodeType + "::" + pkValue).toLowerCase()` → 前 32 字符；**不再截断 dataType 长度** | 两个不同 `t>20 字符 prefix 共 prefix` 实体各 10 PK → `SELECT COUNT(id), node_type FROM graph_node GROUP BY node_type` 不出现跨实体同 id |
| T4 | `engine/kb-engine/kb-engine-impl/src/.../task/KbImportTaskExecutor.java` | 在 `onTaskComplete(success=true)` **后**调 `KbImportAuditService.write(jobId=taskId,...)`；不动现有执行逻辑 | 走一次真跑 → `SELECT * FROM kb_extract_audit WHERE status='SUCCEEDED' ORDER BY created_at DESC LIMIT 1` 出现 |
| T5 | `engine/kb-engine/kb-engine-impl/AGENTS.md` | 端点清单加 `taskType=KB_IMPORT` + `biz_kind=KB_EXTRACT` 标注 | 文档同步 |

## §验证

```
V1 文件生存: ls -la 4 files
V2 集成 grep: grep -c "submitTask" StructuredExtractController.java ≥ 1
              grep -c "taskManagementService" StructuredExtractController.java ≥ 1
V3 后端: cd ecos_backend && mvn install -pl engine/kb-engine/kb-engine-impl,engine/kb-engine/kb-engine-api -am -DskipTests
V4: 重启 dccheng:18086 + gateway:8080 + sysman:18081
     curl -H 'Authorization: Bearer <token>' -X POST .../extract/structured (dry=false) → taskId ≠ KBK1S-
     curl .../api/v1/taskCenter/task-status?taskId=<taskId> → status=RUNNING (等 5s) → SUCCEEDED
     浏览器异步任务中心 KB_EXTRACT filter 出现该 taskId + 点详情拿到 progress 100%
     Graph Explorer 看 ECOS demo 数据建完不冲突
```

## §批次提交

commit 形态（**前后端分离**）：
- `feat(kb-engine): 即时抽取接入 runtime-task submit+execute（架构铁律 §1.6-1）`
- `fix(kb-engine): buildNodeId/buildEdgeId 防跨实体 sha 截断撞车`
