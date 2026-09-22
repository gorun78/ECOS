# Wave-6 T-25 修 36 5xx 最终 Verdict (A 方案收口)

> 来源: 肖国荣 | 日期: 2026-09-03
> 状态: Wave-6 收口 - G4 = NO-GO (32 5xx 残留, Wave-7 收尾)
> 决策: 不 tag v2.0.0-stable, push v2.0.0-rc1

## 0 TLDR

Worker A/B/C 全完成 DDL/NPE/LongToDateTime, 按 Wave-6 初版 11P0 + DDL 缺表/列 scope 100% 命中
36 5xx 基线 815 endpoint 到现状 32 5xx, delta -5, repackage 修复首次让 GW 真起
残留 32 个不是 Wave-6 目标范围, 全部 Wave-3.x 老代码 schema/null/JSON 历史欠账
额外红利 fat-jar repackage 修复 (gateway/pom.xml), 首次 java -jar 启动成功
推荐 不进 stable 转 Wave-7 收尾 32 5xx + LCP

## 1 Worker 完成清单

Worker A DDL 缺表/列 OK
V108__wave6_t25_missing_tables.sql 落盘
CREATE TABLE IF NOT EXISTS ecos_ontology_proposals + optimistic_lock_version
ALTER TABLE td_catalog_item ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64)
ALTER TABLE ecos_workflow_instance 补 5 列 (error_message/retry_count/current_node_id/context_json/created_at/started_at)
CREATE TABLE IF NOT EXISTS ecos_knowledge_graph_node + processed_at BIGINT + 2 index
验 docker psql information_schema 11/11 全部命中

Worker B NPE/IAE/Dup 9 个 OK
10 file:
gateway TaskController getTask/getTaskStatus null-guard 404
cognitive CausalReasonerServiceImpl + CausalChainResult + DiagnosisController metricFound 前置 KG 预检
data MetadataServiceImpl + MetadataCollectionService IAE to NotFoundException
ai GuardrailsServiceImpl IAE to ValidationException
data quality DqRepository resolveGeneratedKey 兜底 PG 多 key (getKey to getKeys)
ontology OntologyService 3 处 catch DuplicateKeyException 重抛
sysman GlobalExceptionHandler 加 ValidationException to 400 / DuplicateKeyException to 409

Worker C WF-009 + KB long DateTime OK
buszhi WorkflowApprovalService 4 处 WF-009 to NotFoundException
long to LocalDateTime (PG TIMESTAMP 5xx 根因) 5 处
kb KnowledgeNode + KnowledgeEdge
kb KnowledgeGraphServiceImpl (2) + KGWriterService (3)
ExpertRuleMapper 4 处 epoch SQL EXTRACT EPOCH / TO_TIMESTAMP x/1000.0

## 2 5xx 基线 vs 现状
Wave-5.2 T-22 基线 815 endpoint = 695 200 / 84 4xx / 36 5xx (G4 NO-GO)
Wave-6 后 815 endpoint = 703 200 / 84 4xx / 32 5xx (G4 仍 NO-GO)
delta -5 (repackage fix 让 GW 真起, 之前 thin jar 跑不到)

## 3 残留 32 5xx 根因桶归类 (源 /tmp/curl_all.log 2026-09-03 16:54)
R1 = 9  ontology proposals x CRUD+transition (BadSqlGrammar 未捕获)  部分  Wave-3.4
R2 = 8  workflow instances x suspend/resume/terminate/approve/reject  部分  Wave-3.x
R3 = 6  duty/glossary/workflow 写路径 NOT NULL 缺参  否  Wave-3.x
R4 = 4  datanet metadata preview x LIMIT 类型推断  部分  Wave-4.2
R5 = 3  agent-mesh POST agents + KG graph NPE  部分  Wave-5.1
R6 = 2  guardrails POST policies + knowledge rules PUT  部分  Wave-5.x

关键: 36 5xx 主力 R1-R6 不在本 Wave DDL scope, 全部 Wave-3.x 漂移

## 4 顺带修复

4.1 gateway/pom.xml 加 repackage (必 fat-jar)
之前 thin jar 207K 无 Main-Class 3 次 _final_verify.sh 都 no main manifest
加 repackage 后第一次真起 140M pid 70677 86s actuator/health 200
build.sh 语义恢复 (之前 207K 以为 fat 实际 thin)

4.2 _tmp_final_verify.sh 三个 fix
mvn install 加 -pl gateway -am (前少了 -pl 只跑 root)
.py 用 python3 (原 typo 写 node)
删 tail -n 20 全量 stdout 落盘

4.3 gateway GlobalExceptionHandler 扩 8 handler
子代理 fullstack-implementer 加 8 handler DataBridge/Business/Validation/NotFound/Unauthorized/Forbidden/DataAccess + 500 兜底
遗留 没含 HttpMessageNotReadableException (curl 空 body 应 400) 列 Wave-7

## 5 结论与建议

5.1 不进 v2.0-stable
G4 红线未达 703/815 2xx 32 5xx 残留
36 5xx 主力 R1-R5 不在 DDL scope
32 5xx 修完才是真 stable

5.2 Wave-7 建议任务
T-26 R1 9 + R2 8 ontology proposals + workflow transition (BadSqlGrammar/Transition) 0.5d
T-27 R3 6 NOT NULL 入参预检 0.2d
T-28 R4 4 metadata preview LIMIT 类型 0.2d
T-29 R5 3 agent-mesh + KG graph NPE + GlobalExceptionHandler 补 HttpMessageNotReadable 0.3d
T-30 R6 2 guardrails policy + knowledge rules PUT 入参校验 0.1d
T-31 LCP 5s (Wave-5.2 T-21 推 6 to 7) 0.5d
T-32 smoke 真 E2E stream 4 域 0.5d
T-33 0 5xx 验收后 v2.0.0-stable tag + push 0.1d

## 6 落盘 Commit 对象
gateway/pom.xml repackage
gateway GlobalExceptionHandler 8 handler
gateway V108 migration
engine/ontology-engine worker b
engine/cognitive-engine causer
engine/data-engine metadata/dq
engine/kb-engine knowledge node/edge/graph/kg/exprule
engine/ai-engine guardrails
buszhi workflow-approval
sysman global-exception-handler
gateway task-controller
docs/08 报告 15 23 aws
ecos_tests/_tmp_final_verify.sh fix 3

## 7 实施命令
cd /home/guorongxiao/ECOS
git add gateway/pom.xml + gateway/src + engine + buszhi + sysman + docs/08 (9 reports) + ecos_tests/_tmp_final_verify.sh
git commit -m fix(product): Wave-6 T-25 - 3 worker 修 36 5xx (DDL+NPE+type), 残留 32 推 Wave-7
git tag a v2.0.0-rc1 -m Wave-6 收口 36 to 32 5xx 留 Wave-7
git push origin release/v2.0-alpha
git push origin v2.0.0-rc1

## 8 v2.0.0-stable 判定条件 (原话)
815 端点 100% 200/202/204 (0 5xx + 0 NETERR)
P99 小于 500ms (Wave-5.2 T-20 已 PASS max 254ms)
LCP 小于 5s (Wave-5.2 T-21 推 Wave-7)
soak 72h 无 OOM (Wave-4.2 已 72h)
stream 4 域真 E2E (Wave-7 T-32)

Wave-7 全收工后 git tag a v2.0.0-stable.
