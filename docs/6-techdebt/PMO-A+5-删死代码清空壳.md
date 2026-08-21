# PMO-A+5: 删死代码 + 清空壳（runtime-core 瘦身）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-21
> **协同**: ECOS-ARCH + ECOS-BE
> **铁律**: ①删前必须 grep 确认 0 外部引用（runtime-core 之外的模块不 import）②runtime-monitor 是「器·全局监控」，**保留不删** ③每 Task 独立 commit ④软删除优先：先移 module，目录物理删除等全量验证后

## §背景

runtime-core 388 文件里混杂大量 0 外部引用的死代码 + runtime-datanet 空壳。A+5 把它们清掉，让 runtime-core 退化为纯「器」。目标是 runtime-core 从 388 降到 ~100 文件（runtime-access 迁走 37 基础工具 + 本指令删死代码 + A+1~A+3 迁走安全/数据/agent.mesh 后）。

## §删除清单

### 明确死代码（23 文件，直接删）

| 包 | 文件数 | 说明 |
|------|-----:|------|
| `agent/tool/` | 8 | 旧 Agent 工具实现，0 引用 |
| `agent/impl/` | 5 | 旧 Agent 实现，0 引用 |
| `agent/llm/` | 4 | 旧 LLM 封装，0 引用 |
| `legacy/` | 6 | 历史遗留，0 引用 |

### 空壳（移除 module）

| 模块 | 文件数 | 动作 |
|------|-----:|------|
| `runtime-datanet` | 0 | `runtime/pom.xml` 移除 `<module>runtime-datanet</module>` |

### 待判断（数据工程 99 文件，0 外部引用）

dataaccess 33 / datadescription 19 / format 15 / metadata 10 / lineage 3 / kettle 6 / bigdataengine 8 / modelaccess 5。这些 0 外部引用，但**内部可能互相引用**。判断流程见 T3。

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | grep 确认 agent/tool + agent/impl + agent/llm + legacy 共 23 文件 0 外部引用后删除 | `mvn install -DskipTests` 通过 |
| T2 | `runtime/pom.xml` 移除 `runtime-datanet` module | `mvn validate` 通过 |
| T3 | 判断数据工程 99 文件：grep 确认 0 外部引用 → 对比 data-engine 是否有对应功能 → 有对应则删，无对应则标记 `// TODO 待迁data-engine` 暂留 | 见下 |
| T4 | 全量编译 + 三版本 profile validate | BUILD SUCCESS |

### T3 判断流程（数据工程 99 文件）

```bash
cd /home/guorongxiao/ECOS/ecos_backend
# 1. 确认 0 外部引用（runtime-core 之外无 import）
for pkg in dataaccess datadescription format metadata lineage kettle bigdataengine modelaccess; do
  echo "=== $pkg ==="
  grep -rln "runtime.core.$pkg" --include="*.java" . | grep -v target | grep -v "/runtime/runtime-core/"
done
# 2. 若全部 0 匹配 → 这些包无活跃消费方
# 3. 对比 data-engine 现有实现（data-engine 已有 MetadataController/QualityController/DataLineageController/QueryController 等）
# 4. 判定：data-engine 已有对应功能 → 删（旧重复实现）；无对应 → 标记 TODO 暂留
```

**判定原则**：0 外部引用 = 无活跃消费方 = 删除安全（git 历史可找回）。除非明确是 data-engine 缺的能力（执行时对比 data-engine Controller 清单确认），否则删。

## §禁止清单

1. ❌ 不删 runtime-monitor（57 文件，器·全局监控，保留）
2. ❌ 不删 agent 顶层包（AgentService/AgentRuntime/AgentSession 等 9 文件，可能被 agent.mesh/ai-engine 用，先 grep 确认）
3. ❌ 不删 runtime-task / llm-gateway（器·调度/网关，保留）
4. ❌ 删前不 grep 就删（必须先证明 0 引用）

## §验证门禁

```bash
# V1: 全量编译
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS

# V2: 已删包无残留引用（以 legacy 为例）
grep -rln "runtime.core.legacy" /home/guorongxiao/ECOS/ecos_backend --include="*.java" | grep -v target
# 期望: 0 匹配

# V3: runtime-core 文件数下降（基线 388）
find runtime/runtime-core/src/main/java -name "*.java" | wc -l
# 期望: 显著下降（A+1~A+5 全部完成后目标 ~100）
```

## §工时

1 天（23 明确死代码删除 0.5 天 + 数据工程 99 文件判断 0.5 天）。

## §风险

- **"内部互相引用"陷阱**：数据工程 99 文件可能 dataaccess→datadescription→... 内部成链，但整条链 0 外部引用。判断时以"链的外部引用"为准，整链 0 外部引用则整链删。
- **agent 顶层包的边界**：agent/ 顶层 9 文件（AgentService 等）不是死代码（可能被 ai-engine 用），T1 只删 tool/impl/llm 子目录，别误删顶层。删前 `grep -rn "runtime.core.agent.AgentService"` 确认。
- **`.m2` 旧 JAR**：删包后全量 install，若仍报旧类冲突，删 `~/.m2/repository/com/chinacreator/gzcm/runtime-core*` 重建。
- **软删除**：本指令的"删"也分两步——先确认 0 引用 + git commit，物理删除走 `git rm` 保留历史。runtime-datanet 空壳（0 文件）可直接移除 module。
