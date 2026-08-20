# PMO指令: ECOS Pipeline 四件 + JOIN/SINK 补全（借鉴 Semantica · P1-C）

> **来源**: 肖国荣 | **日期**: 2026-08-20
> **协同**: ECOS-BE（data-engine 主责）
> **架构铁律**: 必须遵循 [ECOS架构铁律](../ARCHITECTURE-RULES.md)
> **关联**: 依赖 PMO-32（`ecos_provenance_entry` 表已建）；方案 `../ECOS-借鉴Semantica-完整方案.md`

## 零、现状摸底

data-engine `PipelineExecutionEngine`(420行) 现状（已核实代码）：

| 能力 | 现状 |
|------|------|
| 拓扑排序 | ✅ Kahn 已实现 |
| 失败重试/降级 | ❌ 失败直接 `throw e`（L99），无重试 |
| 并行执行 | ❌ 串行 for 循环（L62） |
| 管线验证 | ⚠️ 只检测循环依赖（Kahn 内） |
| 管线溯源 | ⚠️ 有 `step_run`（elapsed_ms/error_msg），无来源链路 |
| JOIN | ❌ `executeJoinStep`(L347) 是 pass-through（"多源 JOIN 需 DAG 调度"） |
| SINK | ❌ `executeSinkStep`(L356) 降级为日志（"降级为日志记录"），不真写入 |

## 一、目标架构

补 Semantica `pipeline/` 的四件（重试/并行/验证/溯源），并补 JOIN/SINK 两个空实现。执行级溯源写入 PMO-32 已建的 `ecos_provenance_entry` 表，与决策层数据级溯源**同一套表**（统一溯源）。

## 二、分阶段执行计划（5 个 Task）

| Task | 文件/路径 | 操作 | 工期 |
|:-----|----------|------|:---:|
| T1 | `engine/data-engine/data-engine-impl/.../data/service/PipelineFailureHandler.java` | FailureHandler + RetryPolicy + Fallback（节点失败重试 N 次后降级） | 1.5天 |
| T2 | `engine/data-engine/data-engine-impl/.../data/service/PipelineParallelismManager.java` | 无依赖节点并行执行（拓扑分层后同层并发） | 1.5天 |
| T3 | `engine/data-engine/data-engine-impl/.../data/service/PipelineValidator.java` | 三验：结构/依赖/性能（节点上限） | 1天 |
| T4 | `engine/data-engine/data-engine-impl/.../data/service/PipelineProvenanceRecorder.java` | 执行级溯源，写 `ecos_provenance_entry` | 1天 |
| T5 | `engine/data-engine/data-engine-impl/.../data/service/PipelineExecutionEngine.java` | 四件接入 + JOIN/SINK 真实现 | 2天 |

### T1 失败处理契约

```java
// 节点失败策略：retry N 次（指数退避）→ 仍失败 → Fallback（跳过/降级/终止，按 config 配置）
// 默认：retry=2, backoffMs=1000, fallback=SKIP（跳过节点，记录 FAILED 但管线继续）
// 配置在节点 config JSON：{"retry": 3, "fallback": "TERMINATE"}
```

### T2 并行契约

```java
// 拓扑分层后，同一层（无相互依赖）节点用线程池并发执行
// 复用 Kahn 排序结果，按 depth 分组，组内 CompletableFuture 并发，组间串行
// 线程池大小默认 min(4, 同层节点数)，不新建设调度线程（一次性线程池，用完关闭）
```

### T3 验证契约

```java
// 三验：①结构（节点/边非空、nodeId 唯一）②依赖（dependsOn 无环、引用节点存在）③性能（节点数上限 100）
// 返回 List<String> errors，非空则拒绝执行
```

### T4 溯源契约（写入 PMO-32 已建表）

```java
// 每个步骤执行后写 ecos_provenance_entry：
// entity_type="pipeline_step", entity_id=stepRunId,
// source_type="PIPELINE", source_ref=taskId, agent="data-engine",
// activity="execute", timestamp=now
// 与决策层溯源共用同一张表，保证"统一溯源"
```

### T5 改造点

```java
// executeJoinStep：真正多源 JOIN（同 DataFrame 按 join_keys 合并，基于内存 Map）
// executeSinkStep：真写入目标表（JdbcTemplate batchUpdate，不再是日志降级）
// execute() 主循环：接入 T1(失败处理)+T2(并行)+T3(验证)+T4(溯源)
```

## 三、禁止清单

1. **禁止新建 Maven 模块** — 落在 data-engine 现有模块
2. **禁止修改现有 API 路径或签名** — PipelineController 对外不变
3. **禁止自建调度线程** — 并行用一次性线程池，不 `@Scheduled`/`ScheduledExecutorService` 常驻
4. **禁止新建溯源表** — 复用 PMO-32 的 `ecos_provenance_entry`（统一溯源，不许另建 `pipeline_provenance` 表）
5. **禁止跨 Phase 预创建文件** — 只做 Pipeline 四件 + JOIN/SINK，不碰其他引擎

## 四、风险与回滚

- **风险1**：并行执行可能改 DataFrame 共享状态 → 每个节点独立 DataFrame 副本，避免竞态。
- **风险2**：SINK 真写入后，之前"降级日志"的行为变化 → 无 target 配置时仍走日志（保留），有 target 才真写入。
- **回滚**：T5 改造前 `git tag` 打点，四件类删除 + execute 还原即可。

## 五、工时估算

| Task | 工期 |
|------|:---:|
| T1 失败处理 | 1.5天 |
| T2 并行 | 1.5天 |
| T3 验证 | 1天 |
| T4 溯源 | 1天 |
| T5 接入+JOIN/SINK | 2天 |
| **合计** | **7天** |

## 交付检查清单

| 验收项 | 命令 | 期望 |
|--------|------|------|
| V1 编译 | `env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -pl engine/data-engine/data-engine-impl -am -DskipTests -q'` | BUILD SUCCESS |
| V2 重试 | 构造失败节点（SQL 语法错），查 step_run | 有 retry 记录，重试 N 次后 FAILED |
| V3 并行 | 两条无依赖节点管线，查执行日志时间戳 | 同层并行（时间重叠，非严格串行） |
| V4 验证 | 传循环依赖管线，调 execute | 返回结构错误，拒绝执行 |
| V5 溯源 | 执行后查 `ecos_provenance_entry` | entity_type=pipeline_step 的记录存在 |
| V6 JOIN/SINK | 构造 JOIN 节点 + SINK 目标表，执行 | JOIN 真合并、SINK 真写入目标表 |

## 一句话给 PMO

data-engine 的 Pipeline 现在失败就 throw、串行跑、JOIN/SINK 是空壳——补重试/并行/验证/溯源四件，把 JOIN/SINK 做成真实现，溯源写 PMO-32 已建的同一张表。
