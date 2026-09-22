# PMO-A3: 统一 knowledge/rag 归属（删 services 薄壳）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-21
> **协同**: ECOS-BE
> **铁律**: ①kb-engine（水·知识对象）是知识检索/RAG 唯一权威，services 层是薄壳不该承载 RAG 业务 ②只删不迁 ③每 Task 独立 commit

## §背景

`services/knowledge-service/KnowledgeRagController`（薄壳，`/api/v1/knowledge` + `/rag`、`/ingest`，29 行）与 `kb-engine/RagController`（权威，`/api/v1/knowledge/rag`，水·知识对象）职能重复。RAG 检索是 kb-engine 的职责，services 层（knowledge-service）不该有。

dccheng 侧的 `KnowledgeApiController`/`KnowledgeGraphController` 等已在 A1-3（删 dccheng）中一并处理，本指令只处理 services 层薄壳。

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | `git rm services/knowledge-service/src/main/java/com/chinacreator/gzcm/services/knowledge/controller/KnowledgeRagController.java` | 文件不存在 |
| T2 | 全量 `mvn install -DskipTests` | BUILD SUCCESS |

> 注：`services/knowledge-service` 仅有这 1 个 Controller（已核实）。删除后该模块成为空壳服务，按技术债修复计划「services 层本次只修冲突不废弃」，模块保留，阶段 D4 再统一收敛为四转化服务。

## §禁止清单

1. ❌ 不碰 kb-engine/RagController 及其他 kb-engine Controller（权威保留）
2. ❌ 不删 knowledge-service 模块本身（阶段 D4 处理）
3. ❌ 不改 RagController 的方法级 `/rag` 路径（`/api/v1/knowledge/rag/rag` 的路径问题属精修范畴，不在本指令）

## §验证门禁

```bash
# V1: knowledge-service 无 KnowledgeRagController
grep -rn "KnowledgeRagController" /home/guorongxiao/ECOS/ecos_backend/services/ --include="*.java"
# 期望: 0 匹配

# V2: 全量编译
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS

# V3: kb-engine 的 RAG 端点仍可达（需 gateway 启动 + 认证）
# curl -X POST /api/v1/knowledge/rag -H "Authorization: Bearer <token>" -d '{"query":"测试"}'
# 期望: 200
```

## §工时

0.5 天（删 1 文件 + 编译验证）。

## §风险

- **前端调用方排查**：删 `/api/v1/knowledge/rag`（薄壳）前，确认前端无代码直连该路径（应改走 kb-engine）。若前端有调用，本指令只需删后端薄壳，前端路由调整归 A5/前端精修。
- **RagService 依赖**：KnowledgeRagController 依赖 `services.knowledge.rag.RagService`，删 Controller 后 RagService 若无其他消费方成为死代码，可一并删（本指令可选：若确认无引用则删 `services/knowledge-service/.../rag/` 包）。
