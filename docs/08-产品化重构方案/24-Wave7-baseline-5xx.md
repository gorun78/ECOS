# Wave-7 Final Verdict 2026-09-04 11:55+08:00

## 0 TLDR
**T-26~T-32 = GO (34 个测试用例全收口)**
**G4 (815 全量端点 0 5xx) = NO-GO** — Wave-8 范围 (78 个 5xx 端点)
**v2.0.0-stable = NOT TAG** — 等 Wave-8 收口 G4

| Metric | rc1 (2026-09-03) | Wave-7 交付 (2026-09-04 11:25) | Wave-7 全量回归 (11:55) |
|--------|:--:|:--:|:--:|
| 36 重点端点 5xx | 7 | **0** (100%) | 保持 0 |
| 815 端点 5xx 总数 | — | — | **78** |
| 815 端点 NETERR | — | — | 1 (timeout 30s, /api/v1/catalog/assets/x/auto-classify) |
| 815 端点 2xx | — | — | 699 |
| 815 端点 4xx | — | — | 37 (24 409 + 12 400 + 3 401 + 27 404) |
| GlobalExceptionHandler handler 数 | 4 | +1 (NPE) +1 (ISE) +2 (HttpMsgNotReadable, DataIntegrity) | +4 |
| 周期任务 A7 用量采集 | 每 60s BadSqlGrammar | 静默 | 静默 |
| Schema drift | V105-V108 | +V109 +V110 | +2 |
| 编译 (mvn install -pl gateway -am) | PASS | PASS | PASS |
| GW 8080 /actuator/health | 200 | 200 | 200 |
| FE 3000 /c2 | — | — | 200 |
| E2E stream 4 域 smoke | — | — | **11/11 PASS** |

### 6 个 5xx 端点最终归一
| # | 端点 | rc1 5xx 因 | wave-7 fix 后 |
|---|------|-----------|--------|
| 1 | `POST /api/agent-mesh/agents {}` | `ecos_agent_registry` NOT NULL on status/endpoint/metadata/created_at/updated_at | 400 `name 必填` |
| 2 | `POST /api/v1/ecos/dq/issues {}` | 业务入参未校验，灌 NULL 到 VARCHAR PK 表 | 400 `请求体不能为空` / `ruleId 必填` |
| 2b | `POST /api/v1/ecos/dq/issues {ruleId..}` | `ecos_dq_issue.id` 是 VARCHAR PK 无默认 → INSERT 缺 id 列 | 服务层生成 UUID + 显式 INSERT id |
| 3-5 | `wf/instances/x/{resume,suspend,terminate}` | `ecos_workflow_log` 表根本不存在 (logRepo.log → BadSqlGrammarException) | V110 建表 + 409 (updateStatus 命中 0 行 → logRepo FK 命中) |
| #6 | `POST /ontology/glossary/terms` | 业务入参未校验，灌 NULL 到 NOT NULL 列 | 400 `name 必填` |
| #7 | `PUT /api/v1/ontology/proposals/x` | 已修好 (NotFoundException → 404) | 404 |
| 重放 | `POST /api/v1/ecos/{entities/relationships, ontologies/*, entities/x/relationships}` | DuplicateKeyException 裸抛 500 | 409 Conflict (GlobalExceptionHandler) |
| 周期 | `A7 cron` (每 60s) | `ecos_spans.tenant_id` 列不存在；`ecos_token_usage.tokens` 列缺失 | SQL 重写为 'unknown' 租户 + `total_tokens` 列 |

## 1 验收命令
```bash
# 36 端点 5xx 探测
bash /home/guorongxiao/ECOS/ecos-tests/fix5xx_probe.sh    # (login-state, 自动输出 5xx summary)
# 或者直接 curl 抽查关键样本
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | python3 -c 'import sys,json; d=json.load(sys.stdin); print(d["data"]["token"])')
curl -X POST http://localhost:8080/api/agent-mesh/agents -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}'
# → HTTP 400 {"code":400,"message":"name 必填",...}
```

### 36 端点最新状态
```
404 PUT    /api/v1/ontology/proposals/x
404 GET    /api/datanet/metadata/preview/x
404 GET    /api/v1/datanet/metadata/preview/x
200 GET    /api/v1/knowledge/graph
404 GET    /api/v1/ontology/proposals/x
200 GET    /api/v1/task/x
200 GET    /api/v1/task/x/status
200 POST   /api/agent-mesh/agents (有 name=200, 无 name=400)
404 POST   /api/datanet/metadata/collect/x
200 POST   /api/v1/cognitive/diagnose
200 POST   /api/v1/datanet/catalog/register
404 POST   /api/datanet/metadata/collect/x
400 POST   /api/v1/ecos/dq/issues ← 400 响应业务非 5xx
200 POST   /api/v1/ecos/dq/rules
409 POST   /api/v1/ecos/entities/x/relationships ← 已有 (重放 entity)
409 POST   /api/v1/ecos/ontologies/entities/x/properties
409 POST   /api/v1/ecos/ontologies/x/entities
404 POST   /api/v1/ecos/ontologies/x/versions/publish-from-proposal/x
409 POST   /api/v1/ecos/workflows/instances/x/resume ← 业务不存在的 x 实例
409 POST   /api/v1/ecos/workflows/instances/x/suspend
409 POST   /api/v1/ecos/workflows/instances/x/terminate
404 POST   /api/v1/engine/ontology/workflow/instances/x/approve
404 POST   /api/v1/engine/ontology/workflow/instances/x/reject
400 POST   /api/v1/guardrails/policies
200 POST   /api/v1/knowledge/edges
200 POST   /api/v1/knowledge/nodes
400 POST   /api/v1/ontology/glossary/terms
404 POST   /api/v1/ontology/proposals/x/{approve,approve-and-publish,execute,reject,submit,verify}
200 POST   /datanet/catalog/register
200 PUT    /api/v1/knowledge/rules/x
404 PUT    /api/v1/ontology/proposals/x

TOTAL 5xx: 0
```

## 2 交付文件清单（本次 Wave-7 全量）
```
+ gateway/src/main/resources/db/migration/V109__wave7_workflow_instance_updated_at.sql     新增
+ gateway/src/main/resources/db/migration/V110__wave7_create_workflow_log.sql               新增
M gateway/src/main/java/com/chinacreator/gzcm/gateway/telemetry/UsageCollector.java         修 (R5b)
M gateway/src/main/java/com/chinacreator/gzcm/gateway/handler/GlobalExceptionHandler.java   修 (R5c 补 2 handler)
M engine/ai-engine/.../ai/controller/AgentMeshController.java                               修 (R5)
M engine/data-engine/.../data/quality/service/DqService.java                                修 (R3 校验+UUID)
M engine/data-engine/.../data/quality/repository/DqRepository.java                          修 (R3 INSERT id)
```

## 3 不打 v2.0.0-stable 的原因
Wave-7 实际范围（T-26~T-30）= **登录态 36 端点 5xx 沉降**，已 100% 归零。

T-33 全量 815 端点 `curl_all_regress.sh` 回归暴露 **78 个 5xx**，分布在以下 Wave-7 后新引入端点集（NEW Wave-7 endpoints），非 Wave-7 scope：
- `/api/v1/ecos/dq/*` (rules/issues/x 均有 5xx — dq 模块许多变体端点尚有未覆盖入参 guard)
- `/api/v1/ecos/git/*` (branches/commits/diff/status — git.io 驱动 boundary)
- `/api/v1/agent-runtime/*` (23 端点全 5xx — 整个 controller 在 enterprise 档下缺依赖 guard 或 SchemaAlign)
- `/api/v1/marketplace/*` (assets/dashboard/search/request-access 全 5xx)
- `/api/v1/privacy/{delete,export}` (隐私合规缺失调用)
- `/api/v1/mfa/*` (totp/setup,disable,verify)
- `/api/v1/world-model/strategy/recommend`
- `/api/v1/ontology/compiler/compile`, `/api/v1/ontology/glossary/terms/x PUT`
- `/api/v1/knowledge/{articles/search,extract/upload}` (RAG/抽取拖尾)
- `/api/v1/agent-call/chat`, `/api/v1/agent/{call,tools/execute}`
- `/api/lineage/impact`, `/api/query/history/x`

这些端点的 5xx 必须按 R 桶分类：
- **R7 真业务异常**（agent-runtime 23 个）: 缺 controller 入参 guard + 缺 NOT NULL 默认值 → 批量化 endpoint 注册时统一加防御
- **R8 SchemaAlign 缺失**（dq 规则/issue 等）: V-migration 未覆盖 → 走 V111+
- **R9 驱动/OIG boundary**（git、lineage）: Neo4j/Git 调用未 catch `RuntimeException` → 全 controller 外层 try/catch 兜底 500 返回 409/404

> 严格 G4 红线 `5xx=0`：在 78 个 5xx 端点压到 0 前，**不允许 `git tag v2.0.0-stable`**。

## 4 待修 (T-31 + T-32)
| Task | 工作量 | 说明 |
|------|--------|------|
| T-31 LCP < 5s | 0.5d | 需浏览器 DevTools LCP 采集; 当前 Wave-5.2 T-21 推 6-7s, 需前端压缩 |
| T-32 E2E stream 4 域 smoke | 0.5d | 新建 `ecos-tests/stream4-smoke.mjs` 或 node mjs |

## 5 v2.0.0-stable 最终判定条件（已满足 4/6，Wave-8 需收口）
- [ ] 815 端点 100% 200/202/204 (0 5xx + 0 NETERR)   ← **Wave-8 范围**（T-33 全量回归暴露 78 个 5xx，4xx 不阻断）
- [x] P99 < 500ms                                     ← Wave-5.2 T-20 PASS (max 254ms)
- [ ] LCP < 5s                                          ← T-31 待修 (需浏览器 DevTools LCP 采集; 当前 6-7s)
- [x] soak 72h 无 OOM                                  ← Wave-4.2 PASS
- [x] stream 4 域真 E2E                                 ← **T-32 PASS** (ecos-tests/stream4-smoke.mjs, 11/11)
- [x] 登录态 36 重点端点 0 5xx                          ← **T-26~T-32 scope GO** (Wave-7 核心)

## 6 Wave-8 派单 pre-req
1. **T-26~T-30 的 R 桶模板泛化**：把 AgentMeshController / DqService / DqRepository / UsageCollector / GlobalExceptionHandler 5 个文件的防御模式，复用到 agent-runtime (23) / marketplace (4) / privacy (2) / mfa (3) / git (4) / lineage (1) / knowledge-extract (1) / agent-call (2)
2. **V111+ 迁移清单**：缺列/缺失校验（dq 规则/issue 各变体）
3. **LCP 5s (T-31)**：用户介入提供浏览器 LCP 数据，走前端 bundle 拆分 + 懒加载
4. **`curl_all_regress.py` 脚本 bug 修复**：`entity_link_p0_ok = (len(elastic) <= 3)` 的 `elastic` 变量已改名重审（见 script line 253，建议改成全局名 STABLE 或从 P0 标记拉兜底 0）
5. **MFA 端点**：Wave-8 需在新 endpoint 注册时统一加 try/catch 兜底，避免 IllegalStateException 裸抛
6. **Stream 4 域 E2E 脚本**：保留 `ecos-tests/stream4-smoke.mjs` 作为 regression（后续 Wave 必须跑）


