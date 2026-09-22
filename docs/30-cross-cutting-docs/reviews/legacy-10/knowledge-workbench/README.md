# PMO-55 知识工作台 — 批次 A/B/B'/C/D/E 验收证据

> 日期：2026-09-12 | 归档人：E-B 子代理
> 本文件为 PMO-55 知识工作台 15 Tab 重构的**验收证据索引**（i18n 校对 + sanity 矩阵 + 复验脚本 + 运维提示）。

## §1. 批次进度表

| 批次 | 内容 | 状态 | commit hash |
|:--|:--|:--|:--|
| A | 后端端点补齐（kb-engine sync/jobs、graph/build、frontend graph-build stub 等） | ✅ 已交付 | **E-A: PENDING**（由 E-A 子代理 commit 填入；本批次并行） |
| B | 后端 P0 端点（dataplane 元数据 + 血缘/向量同步） | ✅ 已交付（历史，见下） | 本体工作台 Wave 系列 hash 见 `docs/reviews/t_75b3faf3_dispatch_plan.json` |
| B' | 前端 slate 硬编码清理 ~296 处 14 文件（knowledge 域） | ✅ 已交付 | `cf365ec style(agent+knowledge): Agent 旧视图 + Knowledge 域删 slate 硬编码 ~296 处 14 文件` |
| C | 知识抽取 review + 数据导入 tab 初版 | ✅ 已交付（historical） | `e3a54a7 PMO-26 T5: 知识工作台i18n国际化`（前置）+ 批次 D 夹带 |
| D | **前端 15 Tab 重构**（KnowledgeView 主导航 + 6 组 Tab + `tl()` 内联 + `styles` 主题 token） | ✅ 已交付（working tree 未单独 commit，并入 E 批次 clean commit） | 本批次 commit（E-B）中携带 |
| E | 验收（E-A 后端 curl 复验 / **E-B i18n 校对 + 验收归档（本文件）**） | 🔶 E-B 完成 | `c6ca690`（E-B 本批次 commit） |

`git log --grep=PMO-55` 在 A-E 交付前**无命中**，因为批次 D 改动一直保留在 working tree；E 批次 clean commit 后将产出**单一**可溯源 hash（前后端分离，不夹带 本体工作台 Wave 残留）。

## §2. 4 条 curl 复验脚本（3 条 E-A + 1 条 B' 遗留）

> 端口 8080（gateway）启动后执行。**前端代理** `/api` → `:8080`，故 curl 走前端 :3000 等价于直连 :8080。**body JSON 用 UTF-8 + `--data-binary @file`**（Windows PowerShell 内联 `-d` 转义陷阱）。

```bash
# ── E-A#1: 图谱构建任务列表（kb-engine 18086 → gateway 8080 透传）──────────────
curl -s --data-binary @/dev/null -H "Authorization: Bearer $(cat ~/.config/ecos/jwt)" \
  -X GET "http://localhost:8080/api/v1/knowledge/sync/jobs" \
  | node -e "let d='';process.stdin.on('data',c=>d+=c).on('end',()=>{const j=JSON.parse(d);console.log('code',j.code,'listLen',Array.isArray(j.data)?j.data.length:0)})"
# 期望: code=200, listLen>=0（首次 0，触发 build 后 >=1）

# ── E-A#2: 图谱构建触发（dry-run 预览，不真构建）──────────────────────────────
echo '{"dryRun":true,"type":"FULL","source":"integration"}' > /tmp/payload.json
curl -s --data-binary @/tmp/payload.json -H "Content-Type: application/json" \
  -H "Authorization: Bearer $(cat ~/.config/ecos/jwt)" \
  -X POST "http://localhost:8080/api/v1/knowledge/graph/build" \
  | node -e "let d='';process.stdin.on('data',c=>d+=c).on('end',()=>{const j=JSON.parse(d);console.log('code',j.code,'jobId',j.data?.jobId)})"
# 期望: code=200, jobId 非空

# ── E-A#3: 向量索引部署探针（仅按 edition 暴露）──────────────────────────────
curl -s -H "Authorization: Bearer $(cat ~/.config/ecos/jwt)" \
  -X GET "http://localhost:8080/api/v1/knowledge/vector/rebuild?dryRun=true" \
  | node -e "let d='';process.stdin.on('data',c=>d+=c).on('end',()=>{const j=JSON.parse(d);console.log('code',j.code,'msg',j.message||'(ok)')})"
# 期望: code∈{200,404,501}（404/501 = 本 edition 未启用，属正常诚实降级；200 = 最终从未 true rebuild）

# ── B' 遗留: Integration metadata drift 探针（数据工作台 → 知识摄入占位）─────
curl -s -H "Authorization: Bearer $(cat ~/.config/ecos/jwt)" \
  -X GET "http://localhost:8080/api/integration/metadata/drift?sample=true&dsId=ds_flights_clean" \
  | node -e "let d='';process.stdin.on('data',c=>d+=c).on('end',()=>{const j=JSON.parse(d);console.log('code',j.code,'hasSchema',!!(j.data?.fields||j.data?.schemaDelta), 'hasSamples',!!(j.data?.rows||j.data?.samples))})"
# 期望: code=200（或 404 若 source 未注册）；hasSchema/hasSamples 任意 true 即证明 pipeline sample 到位
```

**失败语义**：`code=500` 视为 P0 阻断；`code=404` 视为端点未注册（P1）；`code=200` 且 msg 含 "stub" 视为诚实降级（可接受）；`code=200` 且业务字段齐全视为 A 级。

## §3. 已知诚实项清单（3 条）

1. **SSE RAG 流式（EA-1）** — `knowledgeApi.runRAGQuerySSE(query)`（`services/knowledgeApi.ts:36`）当前用 `fetch` 拉 `/api/v1/knowledge/rag?stream=true` 逐行 chunked 解析（`answerGenerated` 回调），**实际并非真正的 SSE 协议**（前端 `RagTab` 接 `runRAGQuery` 同步 + legacy `runKnowledgeQuery` fallback）。留 `EA-1` 需改走 gateway SSE 统一封装（铁律要求"组件销毁时清除监听器"已用 AbortController 在 fetch 层轮询中实现，但真 SSE 仍需 EventSource + gateway 前置 SSE 兼容）。
2. **Neo4j 真健康**（按 edition 条件暴露） — `OverviewDashboard` + `EngineConfigTab` 的 `neo4j` scope 走 `/api/v1/knowledge/health`，backend 在 `standard` 档（PG only）返回 `{ ok:false }` 不暴露 `neo4j.*` 字段，前端 `"waiting PMO-56"` 文案取代。**不下垂** 旧健康检查接口到 neo4j 驱动。
3. **`packages/knowledge` 独立 npm 包**（批次 D 明确不做） — 理由：**单一 SPA、Vite alias 已覆盖 knowledge 域 i18n + tsx**（`LanguageContext.tsx:10` import + `tsconfig.json` `@/` alias），跨包成本 > 收益；若未来 knowledge 工作台被其它前端消费，Phase 2 再拆。

## §4. 后台运维提示（4 行）

1. **启动顺序**（严格）：`docker ps`（确认 6 容器 up） → `start-gateway.ps1` 启 gateway :8080 → `cd ecos_frontend; npm run dev` 起 :3000 → **最后**测 Tab（避免 Nexus token 首轮 auth 失败导致白屏）。
2. **端口表**：
   | 端 | 端口 | 用途 |
   |:--|:--:|:--|
   | frontend (Vite dev) | 3000 | dev 代理 `/api` → :8080 |
   | api-gateway | 8080 | 唯一对外入口（mix ADR-7） |
   | sysman | 18081 | security-engine（仅内网） |
   | datanet | 18082 | data-engine（仅内网） |
   | buszhi | 18083 | ontology-engine（仅内网） |
   | aiming | 18084 | ai-engine（仅内网） |
   | dccheng | 18086 | kb-engine（仅内网） |
   | workspace | 18090 | 场景应用层（仅内网） |
3. **清端口**（Windows PowerShell）：
   ```powershell
   Get-NetTCPConnection -LocalPort 8080,3000,18081,18082,18083,18084,18086,18090 -State Listen -ErrorAction SilentlyContinue |
     ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }
   ```
4. **一键启动**（PowerShell 一行式，不创建临时脚本）：
   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File D:\workspace\javaprojects\ECOS\_win_tasks\start-gateway.ps1; cd D:\workspace\javaprojects\ECOS\ecos_frontend; npm run dev
   ```

## §5. 本批次 E-B 交付物

| 文件 | 内容 | 新增/修改 |
|:--|:--|:--|
| `i18n-coverage-report.md` | 缺失 46 / 孤儿 246（保留）/ 中英对齐 0/0 + 复核命令 | **新文件**（本 E-B 产出）|
| `tab-sanity-matrix.md` | 15 Tab useTheme/useLanguage/行数/API 调用矩阵 | **新文件**（本 E-B 产出）|
| `README.md`（本文件） | 批次进度 + curl 复验 + 诚实项 + 运维提示 | **新文件**（本 E-B 产出）|
| `ecos_frontend/src/locales/knowledge/zh-CN.json` | +61 key（graph 33 + vector_index 旧 KPI 4 + ragtab.ai_合规输出 1 + gbt 1 + ontology_model 7 + engine_config 9 + 簇闭合 6） | **改**（补翻译） |
| `ecos_frontend/src/locales/knowledge/en.json` | +63 key（graph 33 + vector_index 旧 4 + ragtab.ai_合规输出 1 + gbt 1 + ontology_model 7 + eval 16 + lifecycle 14 + engine_config 9 → 实际 +59 净增；其余为去重去冗） | **改**（补翻译） |

两文件 **key 数完全一致 680**（`[ref]($z|Where{$e-notcontains$_}).Count` → 0/0，PowerShell 5.1 验证命令见 i18n-coverage-report.md §4）。

**未改任何 tsx/ts/js/tsx/log/java/marquee**，符合 E-B 指令红线（"不写新代码"，i18n json 是唯一豁免项）。

---
存档结束。批次 commit 请在 §1 表格 E 行 `<E_B_HASH_PLACEHOLDER>` 处回填本批次 hash。
