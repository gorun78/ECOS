# PMO-A+9a: 删 runtime-core 剩余死代码 114

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-23
> **协同**: ECOS-ARCH + ECOS-BE
> **前置**: A+8 已完成（runtime-core ~135）

## §背景

A+8 删了一批死代码后，勘察确认 runtime-core 剩余文件里还有 **114 个死代码**（0 外部引用，已逐类 grep 核实）。删掉后 runtime-core 只剩 ~22 个活跃文件（database/common.util/agent.mesh/agent顶层），达成 A+5「~100 目标」。

**关键修正**：上一轮 A+8 判断为"活跃"的 `core.util.Component`/`core.bean.Tddxnode` 实测 0 引用，`config` 整包（8 个）也全是死代码——本指令一并删。

## §死代码清单（114 文件）

| 包/文件 | 文件数 | 说明 |
|---------|:---:|------|
| `common/dataobjectmgr/*` | 29 | 整包（含 ScheduleBean 827 行） |
| `common/datasourcemgr/*` | 11 | 整包 |
| `common/dxflow/*` | 7 | 整包 |
| `common/exsharemgr/*` | 6 | 整包 |
| `common/fileupload/*` | 2 | 整包 |
| `common/nodemgr/*` | 3 | 整包 |
| `common/params/*` | 4 | 整包 |
| `common/rpccaller/*` | 6 | 整包 |
| `common/sysvar/*` | 3 | 整包 |
| `common/version/*` | 5 | 整包 |
| `common/dbdata/*` | 1 | 整包 |
| `common/util/*`（除 LegacyListInfo/PageInfo） | 21 | **保留 LegacyListInfo + PageInfo**（被 runtime-monitor 引用） |
| `common/monitor/monitordata/bean/NodeProcessBean` | 1 | |
| `common/core/TaskStarter` + `common/PoolmanFileInit` + `common/InitStartTaskInterface` + `common/DxConstans` | 4 | |
| `config/*` 剩余 8 类（ConfigException/ConfigListener/IConfigService/RuntimeConfigDao/ConfigDaoImpl/ConfigEntity/ConfigVersionEntity/DatabaseConfigServiceImpl） | 8 | 全死代码 |
| `core/util/Component` + `core/bean/Tddxnode` | 2 | A+8 误判活跃，实测 0 引用 |
| `agent/AgentSession` + `agent/AgentMessage` + `agent/AgentException` | 3 | 0 引用（AgentRuntime/AgentResult 活跃，保留） |

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | 删 common 死代码 90（dataobjectmgr/datasourcemgr/dxflow/exsharemgr/fileupload/nodemgr/params/rpccaller/sysvar/version/dbdata/util死/monitor.monitordata/core.TaskStarter/PoolmanFileInit/InitStartTaskInterface/DxConstans） | 编译通过 |
| T2 | 删 config 8 + core 2 + agent 3 | 编译通过 |
| T3 | 全量编译 + 跑硬门禁 | BUILD SUCCESS + grep 0 匹配 |

### 删前确认命令（每包删前跑）

```bash
cd /home/guorongxiao/ECOS/ecos_backend
grep -rln "runtime.core.<包名>" --include="*.java" . | grep -v target | grep -v "/runtime/runtime-core/"
# 期望: 0 匹配（若命中则停，汇报）
```

### common/util 特殊处理

删 util 里除 LegacyListInfo/PageInfo 外的 21 个类。精确清单：Utils/TransactionManager/StringUtils/SPIManagerMigrationHelper/RemoteFileExchanger/RecordReferencedException/PinYinUtil/Page/OperationType/LegacyTxBoundary/GlobalParamUtil/GlobalParamName/FlowContorlUtil/DynamicDatasoureManagerUtil/DxObjectSerialUtil/DuplicateNameException/DuplicateCodeException/DuplicateBriefNameException/DBDataSourceUtils/Constants/CharIdGenerator。

## §禁止清单

1. ❌ **不删 common/util/LegacyListInfo + PageInfo**（被 runtime-monitor 15 处引用，归 A+9b 迁 runtime-access）
2. ❌ **不删 agent/AgentRuntime + AgentResult**（被 engine 引用，归 A+9c）
3. ❌ **不删 agent/mesh(14)**（aimod 依赖，归 A+9c）
4. ❌ **不删 database(4)**（活跃 30 处，归 A+9b）
5. ❌ 删前不 grep 就删
6. ❌ 不用 `mvn compile` 替代 `mvn install`

## §验证门禁

```bash
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS

# 硬门禁1: 已删死代码 0 残留（抽查 dataobjectmgr/datasourcemgr/config）
grep -rln "runtime.core.\(common.dataobjectmgr\|common.datasourcemgr\|config.IConfigService\|core.bean.Tddxnode\)" --include="*.java" . | grep -v target
# 期望: 0 匹配

# runtime-core 文件数（A+8 后 ~135，本指令后应 ~21）
find runtime/runtime-core/src/main/java -name "*.java" | wc -l
```

## §工时

0.5-1 天（114 死代码删除 + 编译，common 整包删为主）。

## §风险

- **common 内部成链**：dataobjectmgr/datasourcemgr/dxflow 等内部可能互相 import，但整链 0 外部引用，整包删即可。
- **ScheduleBean 在 dataobjectmgr**：C3 计划要拆它，但它是死代码——本指令删掉，C3 作废（这是 B/C/D 计划过时的又一证据）。
- **config 的 ConfigDaoImpl 历史**：A+2 曾处理 ConfigDao 冲突，runtime-core 的 ConfigDaoImpl 现已 0 引用。删前 grep 确认无 sysman/gateway 反向 import。
- **`.m2` 旧 JAR**：删包后全量 install，若报旧类冲突，删 `~/.m2/repository/com/chinacreator/gzcm/runtime-core*`。
