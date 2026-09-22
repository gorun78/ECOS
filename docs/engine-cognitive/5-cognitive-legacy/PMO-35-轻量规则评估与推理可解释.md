# PMO指令: ECOS 轻量规则评估 + 推理可解释（借鉴 Semantica · P1-B）

> **来源**: 肖国荣 | **日期**: 2026-08-20
> **协同**: ECOS-PM（cognitive-engine 主责）
> **架构铁律**: 必须遵循 [ECOS架构铁律](../ARCHITECTURE-RULES.md)
> **关联**: 方案 `../ECOS-借鉴Semantica-完整方案.md`、差距分析 `../ECOS-借鉴Semantica-差距分析.md`

## 零、现状摸底（关键事实）

1. **ECOS 后端没有 Drools**（全仓 `org.kie`/`drools` 引用 = 0）。你记忆里的 Drools 是 `/mnt/d/javaprojects/incubator-kie-drools`、`masterrule` 两个独立项目。
2. cognitive-engine `KnowledgeReasonerService.evaluateCondition()`(L253) 是**字符串 `contains` 匹配**，代码注释自己写着"后续可升级为 SpEL 表达式引擎"：

```java
// 现状：简单字符串匹配，不是真规则评估
for (String key : facts.keySet()) {
    if (condition.contains(key)) {
        if (factValue != null && condition.contains(String.valueOf(factValue))) return true;
    }
}
```

3. `ReasonerResult.reasoningChain` 已有雏形（`List<Map>` 存 ruleId/ruleName/condition/action/satisfied），但不是结构化的推理路径，无法回答"为什么这么判"。

## 一、目标架构

1. 用 **SpEL**（Spring 原生，零新增依赖）替换字符串匹配，做真规则条件评估。
2. 借鉴 Semantica `explanation_generator` 的 **ReasoningStep/ReasoningPath/Justification** 三件套，把规则裁决升级成可解释的逐步推理路径。

**明确不引入**：Drools/KIE（重）、Semantica Rete（12867 行自研推理网络，对 ECOS 仍偏重）。

**KAG 定位**：本指令是 KAG `RULE_CHECK`（规则推理）的条件评估增强——KAG 的规则条件目前是字符串匹配（`evaluateCondition`），升级为 SpEL 后仍是 KAG 混合推理的一路，**不替代 KAG**。

## 二、分阶段执行计划（4 个 Task）

| Task | 文件/路径 | 操作 | 工期 |
|:-----|----------|------|:---:|
| T1 | `engine/cognitive-engine/cognitive-engine-impl/.../cognitive2/service/SpelConditionEvaluator.java` | SpEL 条件评估器（`StandardEvaluationContext` + 注入 facts，返回 boolean + 命中详情） | 1天 |
| T2 | `engine/cognitive-engine/cognitive-engine-impl/.../cognitive2/service/KnowledgeReasonerService.java` | `evaluateCondition()` 用 SpelConditionEvaluator 替换字符串匹配 | 0.5天 |
| T3 | `engine/cognitive-engine/cognitive-engine-api/.../cognitive2/model/ReasoningStep.java` + `ReasoningPath.java` + `Justification.java` | 推理路径三件套模型 | 0.5天 |
| T4 | `engine/cognitive-engine/cognitive-engine-impl/.../cognitive2/service/ReasoningPathBuilder.java` | 从规则裁决结果生成 ReasoningStep/ReasoningPath，接入 `executeRuleCheck` | 1天 |

### T1 SpelConditionEvaluator 契约

```java
// 规则 condition 存 SpEL 表达式，facts 作为变量注入
// 例：condition = "#amount > 500000 && #creditScore < 650"
public class EvalResult {
    boolean satisfied;      // 是否满足
    String detail;          // 命中详情（哪个变量、什么值触发）
    Map<String,Object> evaluatedVars;  // 各变量求值结果（供推理路径引用）
}
public EvalResult evaluate(String expression, Map<String,Object> facts);
// 兼容旧格式：condition 不含 SpEL（纯 key=value 或纯文本）时降级为原 contains 匹配，不破坏存量规则
```

### T3 模型（对齐 Semantica explanation_generator）

```java
// ReasoningStep：单步推理（规则应用）
class ReasoningStep {
    String stepId; String description;
    String ruleApplied;              // 命中的规则
    Map<String,Object> inputFacts;   // 输入事实
    Object outputFact;               // 输出结论
    double confidence;
}
// ReasoningPath：完整推理路径（逐步）
class ReasoningPath {
    List<ReasoningStep> steps;
    String conclusion;               // 最终结论
    String justification;            // 证成说明（为什么这么判）
}
// Justification：证成（结论 + 路径 + 证据）
class Justification {
    String conclusion; ReasoningPath path; List<String> evidence;
}
```

### T4 接入点

`executeRuleCheck()` 里，`evaluateCondition` 返回 EvalResult 后，用 `ReasoningPathBuilder.build(...)` 生成结构化推理路径，写入 `ReasonerResult`（新增字段，向后兼容保留旧 reasoningChain）。

## 三、禁止清单

1. **禁止引入 Drools / KIE / Semantica Rete** — 用 SpEL（Spring 原生，`org.springframework.expression`）
2. **禁止新建 Maven 模块**
3. **禁止修改现有 API 路径或签名** — `executeRuleCheck` 内部升级，对外不变
4. **禁止破坏存量规则** — 不含 SpEL 的旧 condition 降级为原 contains 匹配（见 T1 兼容说明）
5. **禁止跨 Phase 预创建文件** — 只做 SpEL + 可解释，非结构化解析/Pipeline 四件留其他指令

## 四、风险与回滚

- **风险1**：存量规则 condition 是自然语言/纯文本，SpEL 解析会抛异常 → T1 兼容降级，纯文本走原 contains 匹配。
- **风险2**：SpEL 表达式注入安全 → 只允许 facts 变量访问，禁用类型引用/构造器调用（`StandardEvaluationContext` 默认限制 + 白名单变量）。
- **回滚**：`evaluateCondition` 还原为原字符串匹配，新增类删除即可。

## 五、工时估算

| Task | 工期 |
|------|:---:|
| T1 SpEL 评估器 | 1天 |
| T2 替换 | 0.5天 |
| T3 模型 | 0.5天 |
| T4 路径构建 | 1天 |
| **合计** | **3天** |

## 交付检查清单

| 验收项 | 命令 | 期望 |
|--------|------|------|
| V1 编译 | `env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -pl engine/cognitive-engine/cognitive-engine-impl -am -DskipTests -q'` | BUILD SUCCESS |
| V2 SpEL 评估 | 构造规则 condition=`#amount > 100 && #type == 'A'`，facts=`{amount:200,type:'A'}`，调 RULE_CHECK | satisfied=true，detail 含变量命中 |
| V3 兼容降级 | 旧规则 condition=`"最大金额限制"`（纯文本），确认不报错走 contains 匹配 | 不抛 SpEL 解析异常 |
| V4 可解释 | 调 RULE_CHECK，检查返回的 reasoningPath | 含 ReasoningStep 数组 + conclusion + evidence |

## 一句话给 PMO

cognitive-engine 的规则评估现在是字符串 contains 匹配——用 SpEL 换成真条件评估，再把裁决结果做成"为什么这么判"的逐步推理路径。别引入 Drools。
