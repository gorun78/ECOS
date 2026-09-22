# PMO指令: B4/B5 递归与 O(n²) 核实（原计划引用已过时）

> **来源**: 肖国荣 | **日期**: 2026-08-23
> **协同**: ECOS-ARCH + ECOS-BE
> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **铁律**: ①这是「核实 + 仅在确认存在时修复」任务，禁止为凑任务而重构 ②不改 API 签名、不改 Controller 行为 ③核实结论必须如实报告（存在/不存在都要写清）

## 零、背景（重要，先读）

原计划 B4「修复 21 个无保护递归」、B5「消除隐藏 O(n²)」基于 A/A+ 迁移前的旧代码。迁移重写代码后，**原引用的多数问题点已自然消失**。本指令要求重新核实，避免「修一个已不存在的问题」。

已核实过时的引用（无需再查，直接标记作废）：
- `IFieldMappingDao.addMappingRefs` → 已删
- `AgentSessionService.compressHistory` → 重载调用，非自递归
- `AbacPepService.evaluate` → 普通循环，非自递归
- `ClassificationController.synthesizeFromArray` → 单层 for 循环，已无内层 List→Map
- `CausalReasonerServiceImpl` 图遍历 → BFS + visited 防环，O(V+E) 非 O(n²)

## 一、目标状态

核实以下两个文件是否仍存在「无保护递归」或「O(n²)」，**存在则修复，不存在则报告核实结论**（不作为失败）。

## 二、分阶段执行计划

| Task | 文件/路径 | 操作 |
|:-----|----------|------|
| P1-1 | `engine/data-engine/data-engine-impl/.../datasource/storage/adapter/jdbc/BaseJdbcAdapter.java`（897 行） | 核实是否存在自递归（方法体调用同名同参方法）或深递归展开（如嵌套子查询递归）。存在 → 补 base case + 加最大深度保护；不存在 → 报告「无递归」 |
| P1-2 | `engine/cognitive-engine/cognitive-engine-impl/.../cognitive2/service/CausalReasonerServiceImpl.java`（726 行） | 核实第 443 / 522 / 687 / 704 行循环是否存在 O(n²)（嵌套遍历）。存在 → 预聚合（List→Map）降复杂度；不存在 → 报告「无 O(n²)」 |

## 三、禁止清单

- ❌ 为「显得有产出」而对无问题的代码强行重构
- ❌ 改这两个类的对外方法签名
- ❌ 改缓存相关类（那是 B1/B2 指令的范围）
- ❌ 核实结论造假——每个 Task 必须给出「存在/不存在 + 证据（行号 + 代码片段）」

## 四、风险与回滚

- 本指令大概率「核实后无改动」。这是**正确结果**，不是失败。
- 若确认存在递归/O(n²) 且修复，改动须局限在单个方法内，每处单独 commit。

## 五、验证门禁

```bash
# V1: 全量编译（standard profile）
env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -Pstandard'
```

**交付物**：每个 Task 一份核实报告（markdown），格式：
```
## Task X 核实结论
- 结论: 存在递归 / 无递归 / 存在 O(n²) / 无 O(n²)
- 证据: 行号 + 代码片段
- 修复: （若存在）改动说明 + diff 摘要；（若不存在）无需修复
```

## 六、工时估算

P1-1（1h）+ P1-2（1h）≈ **2h**
