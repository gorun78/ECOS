# PMO指令: ECOS 跨引擎编排层（借鉴 Semantica · P0-B）

> **来源**: 肖国荣 | **日期**: 2026-08-20
> **协同**: ECOS-PM（cognitive-engine 主责）+ ECOS-ARCH（架构评审）
> **架构铁律**: 必须遵循 [ECOS架构铁律](../ARCHITECTURE-RULES.md)
> **关联**: 依赖 PMO-32（决策智能层，DecisionNode 消费其 API）；方案 `../ECOS-借鉴Semantica-完整方案.md`

## 零、现状摸底

DIKW 五层引擎（data→ontology→kb→cognitive→ai）现状（已核实）：

| 层 | 引擎 | 现状 |
|----|------|------|
| D | data-engine | `PipelineExecutionEngine`(420行) 只管 ETL（source/transform/aggregate/join/sink），Kahn 拓扑已实现但只串行 |
| I | ontology-engine | 本体建模完整 |
| K | kb-engine | KG/RAG/抽取(LLM) |
| C | cognitive-engine | 因果链/混合推理 |
| W | ai-engine | Agent/Loop/Tool |

**核心缺口**：五层引擎能力只有 `PipelineEvent` 事件解耦，**没有统一编排**。"抽取→建图→推理→决策"这条认知管线，没有任何地方能定义成一条可重试、可追溯、可并行的流程。data-engine 的 Pipeline 节点只有三种 ETL 节点，不能编排任意引擎能力。

## 一、目标架构

在 cognitive-engine 落地"KAG 推理链编排"，把 KAG 的 Builder（抽取）→Solver（推理）→决策落地 推理链显式化为**可编排节点**：

- 节点类型对齐 KAG 推理链环节（ingest/extract/kg/reason/decision），**不是五层引擎能力**
- 复用 data-engine 的 Kahn 拓扑排序思路，补重试/并行/验证三件
- 决策节点（decision）调用 PMO-32 的决策落地，形成"抽取→建图→推理→决策"完整闭环

**KAG 定位**：编排不是独立引擎，而是把 KAG 的 Builder（抽取）→Solver（推理）→决策落地 推理链显式化为可编排、可追溯的 pipeline，融合进 KAG 推理链，不另起炉灶。

## 二、分阶段执行计划（4 个 Task）

| Task | 文件/路径 | 操作 | 工期 |
|:-----|----------|------|:---:|
| T1 | `engine/cognitive-engine/cognitive-engine-api/.../cognitive2/model/CognitivePipeline.java` + `CognitivePipelineNode.java` | 编排定义 + 节点模型（含 nodeType 枚举：INGEST/ONTOLOGY/EXTRACT/KG/REASON/DECISION + dependsOn DAG） | 1天 |
| T2 | `engine/cognitive-engine/cognitive-engine-impl/.../cognitive2/service/CognitivePipelineExecutor.java` | 执行引擎：Kahn 拓扑 + FailureHandler/RetryPolicy + ParallelismManager + PipelineValidator | 3天 |
| T3 | `engine/cognitive-engine/cognitive-engine-impl/.../cognitive2/controller/CognitivePipelineController.java` | REST API + auth whitelist | 1天 |
| T4 | `engine/cognitive-engine/cognitive-engine-impl/.../cognitive2/service/EngineCapabilityRegistry.java` | 引擎能力注册表（五层能力→可编排节点），含 DecisionNode 调用决策层 | 2天 |

### T1 节点模型契约

```java
// CognitivePipelineNode.nodeType 枚举（对齐 KAG 推理链：Builder→Solver→决策）
enum NodeType {
    INGEST,     // KAG Builder：文档接入（kb-engine 解析，PMO-34 补齐）
    EXTRACT,    // KAG Builder：知识抽取（kb-engine KnowledgeExtractionService）
    KG,         // KAG Builder：建图（kb-engine KGWriterService）
    REASON,     // KAG Solver：混合推理（cognitive-engine KnowledgeReasonerService）
    DECISION    // 决策落地（调 PMO-32，KAG 推理出口触发）
}
// 节点字段：id / nodeType / config(JSON) / dependsOn(JSON数组，DAG)
```

### T2 执行引擎四件（对齐 Semantica pipeline/）

| 件 | 要求 |
|----|------|
| FailureHandler + RetryPolicy | 节点失败重试 N 次 + 降级策略（Fallback），失败不再直接 throw |
| ParallelismManager | 无依赖节点并行执行（当前 data-engine 只串行） |
| PipelineValidator | 三验：结构（节点/边完整）、依赖（循环检测，复用 Kahn）、性能（节点数上限） |
| 执行状态机 | PENDING→RUNNING→SUCCEEDED/FAILED，逐步记录（参考 `PipelineExecutionEngine` 的 step_run） |

### T4 引擎能力注册表

```java
// KAG 推理链环节 → 可编排节点，跨引擎走 REST
// 每个节点注册：nodeType + 调用端点（都是 KAG 已有能力，不新建引擎）
// INGEST 节点 → kb-engine 文档解析（PMO-34 补齐）
// EXTRACT 节点 → kb-engine KnowledgeExtractionService（KAG Builder 抽取）
// KG 节点 → kb-engine KGWriterService（KAG Builder 建图）
// REASON 节点 → cognitive-engine KnowledgeReasonerService（KAG Solver 推理）
// DECISION 节点 → 决策落地（PMO-32，KAG 推理出口触发）
```

### T3 REST 端点（前缀 `/api/v1/cognitive`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/cognitive/pipeline` | 创建认知管线定义（节点+依赖） |
| GET | `/api/v1/cognitive/pipeline` | 列管线定义 |
| POST | `/api/v1/cognitive/pipeline/{id}/execute` | 执行管线 |
| GET | `/api/v1/cognitive/pipeline/{id}/execution/{execId}` | 查询执行状态 |

## 三、禁止清单

1. **禁止新建 Maven 模块** — 编排层落在 cognitive-engine，不建新模块
2. **禁止修改 data-engine 的 `PipelineExecutionEngine`** — 它是 ETL 编排，本指令只新建认知编排，不碰它（其四件增强留 P1-C 指令）
3. **禁止直连其他引擎的 Service Bean** — 跨引擎能力调用走 REST（遵守架构铁律，避免 boot 模式 bean 找不到）
4. **禁止修改现有 API 路径或签名**
5. **禁止跨 Phase 预创建文件** — 只做编排层，溯源执行级/Pipeline四件/SpEL 留后续指令
6. **禁止自建调度线程** — 定时执行走 runtime-task，不 `@Scheduled`/`ScheduledExecutorService`

## 四、风险与回滚

- **风险1**：跨引擎 REST 调用在 boot 模式下（单引擎启动）目标引擎未启动 → 执行失败。**约定**：编排层在 Gateway 模式（全量 classpath + 全服务）下运行，boot 模式仅单引擎开发。
- **风险2**：DecisionNode 依赖 PMO-32 未完成 → 执行顺序上 PMO-32 先行，本指令 T4 在 PMO-32 验收后开发。
- **回滚**：新增类删除即可，不动既有代码。

## 五、工时估算

| Task | 工期 |
|------|:---:|
| T1 节点模型 | 1天 |
| T2 执行引擎 | 3天 |
| T3 接口层 | 1天 |
| T4 能力注册 | 2天 |
| **合计** | **7天** |

## 交付检查清单

| 验收项 | 命令 | 期望 |
|--------|------|------|
| V1 编译 | `env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -pl engine/cognitive-engine/cognitive-engine-impl -am -DskipTests -q'` | BUILD SUCCESS |
| V2 建管线 | `curl -s -X POST http://localhost:8080/api/v1/cognitive/pipeline -H 'Content-Type: application/json' -d '{"name":"test","nodes":[{"nodeId":"n1","nodeType":"DECISION","config":{}}]}'` | 返回 pipelineId |
| V3 执行 | `curl -s -X POST http://localhost:8080/api/v1/cognitive/pipeline/{id}/execute` | 返回 execId + status |
| V4 状态 | `curl -s http://localhost:8080/api/v1/cognitive/pipeline/{id}/execution/{execId}` | status=SUCCEEDED/FAILED |
| V5 并行 | 两条无依赖节点管线，检查执行日志顺序 | 并行执行（非串行） |
| V6 重试 | 构造失败节点，检查重试 N 次后 FAILED | 有 retry 记录 |

## 一句话给 PMO

在 cognitive-engine 建"KAG 推理链编排"：把 KAG 的 Builder（抽取）→Solver（推理）→决策落地 推理链做成可编排节点，Kahn 拓扑 + 重试/并行/验证。编排的是 KAG 已有能力，别新建引擎，别碰 data-engine 的 ETL Pipeline。
