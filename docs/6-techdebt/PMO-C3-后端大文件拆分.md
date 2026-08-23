# PMO指令: C3 后端大文件拆分（CausalReasonerServiceImpl + buildWhereClause）

> **来源**: 肖国荣 | **日期**: 2026-08-23
> **协同**: ECOS-BE
> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **铁律**: ①不改对外方法签名、不改 Controller 行为、不改 curl 响应结构 ②拆分后单文件 < 400 行 ③禁止跨 Task 预创建文件

## 零、现状摸底（已核实）

| 文件 | 路径 | 行数 |
|------|------|------|
| CausalReasonerServiceImpl | `engine/cognitive-engine/cognitive-engine-impl/.../cognitive2/service/CausalReasonerServiceImpl.java` | 726 行 |
| buildWhereClause | `engine/data-engine/data-engine-impl/.../datasource/storage/adapter/jdbc/BaseJdbcAdapter.java` 第 663 行起 | 递归 if-else，B3 已加 `MAX_FILTER_DEPTH=20` 深度保护 |

## 一、目标状态

1. CausalReasonerServiceImpl 按职责拆 3 类 + 主类编排。
2. buildWhereClause 从递归 if-else 拆为操作符策略模式，单方法复杂度 ≤10。

## 二、分阶段执行计划

| Task | 文件 | 操作 |
|:-----|------|------|
| P1-1 | `CausalReasonerServiceImpl.java` | 拆 3 个职责类 + 主类保留 `diagnose`/`inferCausalGraph`/`estimateCausalEffect` 编排：①`CausalDetector`（KG 因果链遍历：`traverseKgChain` + `getNodeDescription`）②`RootCauseAnalyzer`（根因识别：`identifyRootCauseAndSuggestions` + `tryLlGenerateRootCause` + `ruleBasedExpansion`）③`SuggestionBuilder`（建议生成：`tryLlGenerateSuggestions` + `buildLlmPrompt` + `callLlm` + `parseLlmCausalChain` + `buildExistingChainSummary`）。工具方法（`extractJson`/`clampConfidence`/`getIntValue`/`getDoubleValue`）放 `CausalReasonerUtils` 或保留主类 |
| P1-2 | `BaseJdbcAdapter.buildWhereClause` | 拆操作符策略：`WhereClauseStrategy` 接口 + 各操作符实现（EQ/NE/LIKE/IN/GT/LT/BETWEEN 等）+ 逻辑分支（AND/OR/NOT 递归处理）。保留 `MAX_FILTER_DEPTH` 深度保护，复杂度降到 ≤10 |

**实现顺序**：P1-1 → P1-2（互不依赖，可并行）。

## 三、禁止清单

- ❌ 改 `diagnose`/`inferCausalGraph`/`estimateCausalEffect` 的返回结构（下游 Controller 依赖）
- ❌ 改 `buildWhereClause` 生成的 SQL 语义（拆分前后生成的 WHERE 子句必须一字不差）
- ❌ 引入新依赖（如 Drools、新的 SQL builder 库）
- ❌ 超过 400 行不拆到底

## 四、风险与回滚

- **SQL 语义漂移**：buildWhereClause 拆分是高风险点，拆分后必须用同一组 FilterCondition 对比拆分前后的 SQL 字符串（单测断言）。建议先写「拆分前 SQL 快照」再拆。
- **循环依赖**：拆出的 CausalDetector/RootCauseAnalyzer/SuggestionBuilder 若互相调用，用 `@Lazy` 或构造注入打破循环。
- **回滚**：每 Task 单独 commit。

## 五、验证门禁

```bash
# V1: 全量编译
env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -Pstandard'

# V2: 拆分后单文件行数
wc -l engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/CausalReasonerServiceImpl.java
wc -l engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/CausalDetector.java 2>/dev/null
# 期望: 每个 < 400 行

# V3: buildWhereClause 策略模式落地
grep -n "WhereClauseStrategy\|case .*:\|if.*equals" engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/datasource/storage/adapter/jdbc/BaseJdbcAdapter.java
# 期望: 命中 WhereClauseStrategy，if-else 链已拆散

# V4: 残留递归保护仍在
grep -n "MAX_FILTER_DEPTH" engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/datasource/storage/adapter/jdbc/BaseJdbcAdapter.java
# 期望: 命中（B3 的深度保护不能丢）
```

## 六、工时估算

P1-1（3h）+ P1-2（4h）≈ **7h**
