# 知识抽取（K1）功能实现 — 架构轻量设计与任务 DAG

> 依据：`docs/plans/knowledge-workbench-replan-v1.md` §6 K1「知识抽取」+ §5.3 接口规范 + §1 五条边界铁律
> 复杂度：L2 标准（总分 27/60） | 工作流：架构轻量设计 → 前后端并行 → QA → Reviewer | 日期：2026-09-20
> 实施基线：B1\~B8 已完成（B3-2 `KbEntityInstanceExtractionService` 已就绪）

***

## 0. 边界与约束（用户两条红线）

| #   | 约束                                                                      | 落地策略                                                                                                                                                                                       |
| :-- | :---------------------------------------------------------------------- | :----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| C-1 | **知识导入以本体模型为基础**（本体已完成实体 → DW 表映射）                                      | K1 结构化通道**唯一入口** = `ecos_entity_table_mapping`（`materialized=true` 且映射有效），**不扫表猜实体**；复用 B3-2 已有契约驱动抽取核心，不重复实现                                                                              |
| C-2 | **非结构化走既有规划路线（B 经数据工作台结构化 + A3 过渡）**；知识工作台**默认不支持**临时文件上传，上传须**引擎配置开关** | K1 非结构化通道**只读 DW 层** **`doc_chunk`**；`POST /api/v1/knowledge/extract/upload` 端点保留做 A3 快路径，**由引擎配置** **`extract.allow_direct_upload`（默认** **`false`）gate**；前端 `DocumentUploadTab` 默认禁用上传 UI |

**边界铁律（违反 = 验收失败）**：

* §0.5-1 单一事实源：DW 层只读，不回写

* §0.5-2 契约先行：不推断「表↔实体」

* §2.1 引擎只调 API：kb-engine 不 import data/ontology/ai-engine impl

* §2.4 安全：候选返回前脱敏（`POST /api/security/mask`）+ 列过滤（`/cls/columns`）

* §2.5-3 任务调度：周期触发一律委托 `runtime-task`，禁止自建 `ScheduledExecutorService`

***

## 1. 现状盘点（已就绪基础，避免重复造轮子）

| 能力                                                                    | 文件                                                                                                                                                                            | 状态         | 本次复用                                                      |
| :-------------------------------------------------------------------- | :---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | :--------- | :-------------------------------------------------------- |
| 契约驱动实例抽取（C1\~C4 + 水位线 + 分页 500/页×200页）                                | `KbEntityInstanceExtractionService.java`                                                                                                                                      | ✅ B3-2 已交付 | 作为 **K1 结构化通道核心引擎**，不重写                                   |
| 抽取消费：骨架/实例/版本对齐                                                       | `KgSyncServiceImpl.java` + `KgMapperService.java`                                                                                                                             | ✅ B1/B3    | 实例抽取结果由它写图谱（已含）                                           |
| 本体发布事件消费者                                                             | `EcosOntologyEventConsumer.java`                                                                                                                                              | ✅ B2       | K4 触发源，本任务**不改动**（K4 批次独立）                                |
| 文档入库 A3（chunk 落 `kb_doc_chunk`）                                       | `KnowledgeDocIngestService.java` + `KbDocMapper/KbDocChunkMapper`                                                                                                             | ✅ B5       | 非结构化 chunk 来源                                             |
| 抽取端点骨架                                                                | `ExtractionController.java`（`/extract/upload` `/tasks` `/files` `/candidates/{fileId}` `/candidates/{fileId}/approve` `/{id}/approve` `/{id}/reject` `/promote-to-candidate`） | ✅ B5-2     | 加 `/extract/upload` 的开关校验 + 新增 `/extract/structured` 触发端点 |
| 引擎配置                                                                  | `KnowledgeSettingsServiceImpl`（fallback `SysConfigService`）+ `KnowledgeSettingsController`                                                                                    | ✅ 已有       | 新增 `extract` scope + `allow_direct_upload` 配置项            |
| Doc 登记（非结构化原文 → `raw/unstructured/` + `RAW/UNSTRUCTURED/LAKE_OBJECT`） | `KnowledgeDocIngestService` + data-engine `DatalakeController`                                                                                                                | ✅ B5/B6    | 开关开启时上传的最终落点已合规                                           |

**结论**：核心抽取逻辑 90% 已就绪，本批次重点是**触发入口 + 开关 gate + 前端接入**，非重写。

***

## 2. 架构图

### 2.1 K1 双通道

```
K1 知识抽取（kb-engine）
├── 结构化通道（映射驱动 · 零 LLM 成本）★ 主力
│   └─ KbEntityInstanceExtractionService.extract()
│       ① 读 kb_ontology_snapshot（版本/C1 判据）
│       ② REST GET /api/v1/ontology/entity-mappings（契约先行）
│       ③ REST GET /api/v1/engine/data/layers/CURATED（DW 资源索引）
│       ④ REST GET .../resources/{id}/rows?watermark=&limit=500（分页水位线增量）
│       ⑤ C1~C4 校验 → graph_node/graph_edge（幂等 upsert，溯源 4 列）
│       ⑥ 写 kb_extract_watermark（水位线持久化）
│   触发：手动 / 本体发布事件 / 周期（runtime-task）
│
└── 非结构化通道（LLM 驱动 · 走 A3 过渡 + gate）
    └─ KnowledgeDocIngestService.ingest()（B5 已有）
        ① 原文 → raw/unstructured/（data-engine 登记端点）
        ② 解析 → kb_doc_chunk（A3 过渡态，待 A1 迁 DW 层）
        ③ 调 ai-engine 抽候选（实体/关系三元组）→ kb_extract_candidate
    触发：仅在 extract.allow_direct_upload=true 时允许入口
```

### 2.2 与 K2 融合衔接

候选三元组（`kb_extract_candidate`）写入后**不直接入图**，进入 K2 融合（实体消歧/属性补全/关系裁决）+ 人工审核（`/extract/candidates/{fileId}/approve`）后才 `graph_node/graph_edge`。结构化通道因字段映射即确定性规则，允许直接落图（B3-2 现状）。

***

## 3. 新增/改造 API 规范

| #  | 方法·路径                                                   | 模块 | 说明                 | 入参                                               | 出参                                        | 状  |
| :- | :------------------------------------------------------ | :- | :----------------- | :----------------------------------------------- | :---------------------------------------- | :- |
| E1 | `POST /api/v1/knowledge/extract/upload`                 | kb | 非结构化上传（**gate**）   | multipart `file`                                 | `{taskId, docId, status}`                 | 修  |
| E2 | `GET /api/v1/knowledge/extract/upload-enabled`          | kb | 查询上传开关（前端 Tab 初始化） | —                                                | `{allowed:boolean, hint:string}`          | 新  |
| E3 | `POST /api/v1/knowledge/extract/structured`             | kb | 触发结构化抽取（K1 主力）     | `{ontologyId?, mode:FULL\|INCREMENTAL, dryRun?}` | `{jobId, ...report}`                      | 新  |
| E4 | `GET /api/v1/knowledge/extract/structured/jobs`         | kb | 抽取作业列表（分页）         | `pageNum,pageSize`                               | `[ExtractJobVO]`                          | 新  |
| E5 | `GET /api/v1/knowledge/extract/structured/jobs/{jobId}` | kb | 作业详情/进度            | —                                                | `ExtractJobDetailVO`                      | 新  |
| E6 | `GET /api/v1/knowledge/engine-config?scope=extract`     | kb | 抽取引擎配置             | `scope=extract`                                  | `{config:{allow_direct_upload}, version}` | 修  |

**E1 gate 逻辑**（关键）：

```java
@PostMapping(value = "/upload", consumes = MULTIPART_FORM_DATA_VALUE)
public ApiResponse<Map<String,Object>> upload(@RequestParam("file") MultipartFile file) {
    boolean allowed = "true".equalsIgnoreCase(
        settingsService.getSetting("extract.allow_direct_upload"));
    if (!allowed) {
        return ApiResponse.badRequest("临时文件上传未开启：请在引擎配置 → 知识抽取 中启用 allow_direct_upload");
    }
    return ApiResponse.success(extractionService.upload(file));
}
```

**E3 实现要点**：直接委托 `KbEntityInstanceExtractionService.extract(ontologyId, jobId, incremental, dryRun)`（已有），外层包 `jobId` 生成 + 落 `kg_sync_log`。

**E6 配置项**（新增 key）：

| key                           | 类型      | 默认      | 说明                          |
| :---------------------------- | :------ | :------ | :-------------------------- |
| `extract.allow_direct_upload` | boolean | `false` | 是否允许知识工作台直接上传临时文件（非结构化快路径）  |
| `extract.page_limit`          | number  | `500`   | 结构化通道单页行数（R3 慢 SQL 防御）      |
| `extract.max_pages`           | number  | `200`   | 单资源最大页数（上限 10 万行）           |
| `extract.periodic_enabled`    | boolean | `false` | 是否启用周期增量抽取（委托 runtime-task） |

**三滤波器（§1.2）**：新增 `E2/E3/E4/E5` 端点走 `/api/v1/knowledge/...`，前缀已在 `V1_REWRITE_MAP` 内 → `SecurityConfig`/`ClearanceInterceptor` **只写裸路径**，业务数据端点**默认 DENY 不写 permitAll**，带 token 200 / 无 token 403 回归验证。

***

## 4. 数据模型

**无需新增表**（B3/B5 已建）：

* `kb_ontology_snapshot`（版本对齐 + C1）

* `kb_extract_watermark`（结构化水位线，B3-2 已写）

* `kb_doc` / `kb_doc_chunk`（A3 非结构化 chunk，B5 已建）

* `extraction_drafts`（抽取任务台账，B5-2 已有）

**唯一新增 DDL（V140）**：`kb_extract_candidate` 候选池（供非结构化通道 LLM 抽取结果暂存，K2 融合消费）：

```sql
CREATE TABLE IF NOT EXISTS ecos_knowledge.kb_extract_candidate (
  id             BIGSERIAL PRIMARY KEY,
  doc_id         VARCHAR(64),          -- 来源文档（关联 kb_doc）
  entity_name    VARCHAR(256),         -- 候选实体
  entity_type    VARCHAR(128),         -- 候选类型（本体 code）
  relation       VARCHAR(128),         -- 候选关系（三元组边）
  subject_id     VARCHAR(128),
  object_id      VARCHAR(128),
  confidence     NUMERIC(5,4),
  status         VARCHAR(32) DEFAULT 'PENDING',  -- PENDING/APPROVED/REJECTED/FUSED
  review_note    TEXT,
  ontology_id    VARCHAR(64),          -- 溯源：本体
  ontology_version VARCHAR(64),        -- 溯源：本体版本（对齐 K4）
  source_resource_id VARCHAR(128),     -- 溯源：DW 资源
  source_pk      VARCHAR(256),         -- 溯源：源主键
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW(),
  is_deleted     SMALLINT DEFAULT 0
);
CREATE INDEX idx_kbeic_doc ON ecos_knowledge.kb_extract_candidate(doc_id);
CREATE INDEX idx_kbeic_status ON ecos_knowledge.kb_extract_candidate(status);
```

> 溯源列与 graph\_node 一致（`ontologyId/ontologyVersion/sourceResourceId/sourcePk`），保证 K2/K4 可对齐。

***

## 5. 时序（K1 结构化通道 — 主力）

```
前端(K1 数据导入 Tab)
  │  POST /api/v1/knowledge/extract/structured {ontologyId, mode, dryRun}
  ▼
kb KnowledgeExtractionService.triggerStructured()
  │  生成 jobId → 落 kg_sync_log(running)
  │  调 KbEntityInstanceExtractionService.extract(ontologyId, jobId, incremental, dryRun)
  ▼  [kb 内]
  ┌ REST GET ontology entity-mappings   ← 契约（materialized=true）
  ┌ REST GET data layers/CURATED         ← DW 资源索引
  ┌ REST GET data .../rows?watermark=    ← 分页 500×≤200 页
  ├ C1 类型 / C2 关系 / C3 属性 / C4 映射 校验
  ├ graph_node/graph_edge upsert（溯源 4 列）
  └ 写 kb_extract_watermark
  ▼
返回 {jobId, nodeCreated, nodeUpdated, edgeCreated, invalidMappings, ...}
```

***

## 6. 前端改动

| 文件                                | 改动                                                                                                                                                                               |
| :-------------------------------- | :------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `services/knowledgeApi.ts`        | 新增 `fetchTriggerStructuredExtract` / `fetchStructuredJobs` / `fetchStructuredJobDetail` / `fetchUploadEnabled`；`fetchEngineConfig('extract')`                                    |
| `typesAndConstants.ts`            | 新增 `ExtractJob` / `ExtractJobDetail` / `UploadGateState` 类型                                                                                                                      |
| `tabs/DataWorkbenchImportTab.tsx` | **重构**：去 localStorage 队列 → 改为「选本体版本 → dry-run 预览（C1\~C4）→ 触发真实抽取 → 作业列表/进度」（消费 E3/E4/E5）                                                                                         |
| `tabs/DocumentUploadTab.tsx`      | **gate**：进 Tab 时 `fetchUploadEnabled()`，`allowed=false` → 整页禁用上传控件 + 显示「临时上传未开启，请在引擎配置启用」+ 跳转链接；`allowed=true` → 原上传流程（A3 快路径，打 `ephemeral=true` + TTL）                          |
| `tabs/EngineConfigTab.tsx`        | `SCOPES` 增 `extract`；`CONFIG_FIELDS.extract = [{key: allow_direct_upload, type:boolean}, {key: page_limit, number}, {key: max_pages, number}, {key: periodic_enabled, boolean}]` |
| `tabs/KnowledgeUpdateTab.tsx`     | K4「文档更新」卡片从占位改为可消费（读 E4 jobs 中 doc 类型）                                                                                                                                           |
| `locales/{zh-CN,en}.json`         | 补 `knowledge.extract.*` / `knowledge.upload.disabled_hint` / `knowledge.engine_config.scope.extract` / `.field.allow_direct_upload` 等                                            |

**编译门**：`npx tsc --noEmit`；**禁止**硬编码中文/颜色（用 `useLanguage`/`useTheme`）；文件 ≤800 行（warn limit）。

***

## 7. 任务 DAG（每 Task = 单模块 + curl 验收，≤5/指令，必要时分批）

```
[T1 后端·开关]  KnowledgeSettings 加 E2 端点 + extract scope 配置项（allow_direct_upload 等 4 key）
     │           验收：curl GET /engine-config?scope=extract 返回 allow_direct_upload=false
     ▼
[T2 后端·gate]  ExtractionController.upload 加 gate（读 extract.allow_direct_upload，false→badRequest）
     │           验收：开关 false 时 POST /extract/upload 返回 400「未开启」；true 时正常
[T3 后端·触发]  新增 E3/E4/E5（triggerStructured/jobs/jobs/{id}）委托 KbEntityInstanceExtractionService.extract
     │           验收：curl POST /extract/structured {mode:INCREMENTAL,dryRun:true} 返回 report 含 nodeCreated
     ▼
[T4 后端·DDL]  V140 kb_extract_candidate DDL + 手工 psql 执行
     │           验收：\d kb_extract_candidate 含溯源 4 列 + 索引
     ▼  (T1/T2/T3/T4 并行)
[T5 前端·api+types]  knowledgeApi 新端点 + 类型 + 引擎配置 Tab 加 extract scope
     │
     ├──▶ [T6 前端·导入Tab] DataWorkbenchImportTab 重构为「本体版本→dry-run→触发→作业监控」
     └──▶ [T7 前端·上传gate] DocumentUploadTab 按 E2 开关禁用/启用 + 引擎配置开关 UI
              │
              ▼
       [T8 QA curl + 编译门] 后端 mvn install -DskipTests / 前端 tsc + vitest 核心用例
              │
              ▼
       [Reviewer 审查] deliverable_allowed 判定（T7 开关默认 false 重点核验边界铁律）
              │
              ▼
           交付（commit hash = DONE 凭证）
```

**依赖**：T5 依赖 T1\~T3（需端点存在）；T6/T7 依赖 T5。T4 独立可并行。

**裁决点（对应用户可选输入）**：

* 周期抽取（`periodic_enabled`）是否本批次接入 `runtime-task`？默认**否**（留 K4 批次），本批次仅置配置项 + `false`。

***

## 8. 风险与回退

| 风险                | 缓解                                                   |
| :---------------- | :--------------------------------------------------- |
| R3 慢 SQL（DW 实例抽取） | 分页 500/页 + 水位线增量 + MAX\_PAGES=200 上限（B3-2 已实现，本批次不改） |
| R1 向量列迁移          | 不涉及本批次（K3 已做，只加不删）                                   |
| 误开上传导致临时文件堆积      | `ephemeral=true` + TTL 清理（方案 §3.3），默认关闭              |
| 候选池膨胀             | `kb_extract_candidate` 按 status 索引，K2 融合后转 FUSED 归档  |
| 开关变更不生效           | 配置走 sys\_config 实时读，无缓存                              |

**回退**：开关 `allow_direct_upload` 置回 `false` 即可断路非结构化临时入口；结构化通道独立不受影响。

***

## 9. 验收门禁（Reviewer deliverable\_allowed）

1. ✅ 结构化通道以 `ecos_entity_table_mapping` 为唯一入口（grep `KbEntityInstanceExtractionService` 无新扫表逻辑）
2. ✅ 非结构化上传默认关闭：`curl` 默认开关下 `POST /extract/upload` 返回 400
3. ✅ 引擎配置开关生效：开启后上传可用
4. ✅ 无硬编码中文/颜色（前端 lint）
5. ✅ 周期任务未自建 ScheduledExecutorService（grep 0 命中）
6. ✅ 候选/图谱溯源 4 列齐（ontologyId/ontologyVersion/sourceResourceId/sourcePk）
7. ✅ curl 4 项全过（E2/E3 + 开关开/关两态）

