# PMO-A+8: runtime-core 收尾（删死代码 47 + i18n 迁 runtime-access）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **来源**: 肖国荣 | **日期**: 2026-08-23
> **协同**: ECOS-ARCH + ECOS-BE
> **前置**: A+6/A+7 已完成（runtime-core 194 文件）

## §背景

A+5 目标是 runtime-core 从 388 降到 ~100。A+6/A+7 后降到 194。勘察确认剩余 194 文件分三类：

1. **死代码 47 文件**（0 外部引用，已逐包 grep 核实）——本指令删
2. **i18n(5)**——R3 时因被 runtime-core 死代码（dataaccess/datasource）依赖而回退保留，现在依赖方已删，**可迁 runtime-access**
3. **活跃的 common 历史遗留（~90 文件，dataobjectmgr/datasourcemgr/dxflow 等被 sysman/engine 引用）**——本指令**不碰**，归 A+9 迁移到 data-engine

## §迁移三动作铁律

迁移 = 移动不是复制。删原类 + 改消费方 import + 硬门禁 grep 旧 package 0 匹配。

## §死代码删除清单（47 文件，整包/整文件删）

| 包/文件 | 文件数 | 依据 |
|---------|:---:|------|
| `core/license/*` | 6 | 0 外部引用 |
| `core/rpcpip/*`（ZLibUtils/Utils/RpcCallResult/IRpcCallTool/RpcPipManager） | 5 | 0 外部引用 |
| `core/controlcenter/NeighborNodeCache` | 1 | 0 外部引用 |
| `core/TaskLockService` + `core/NodePropties` | 2 | 0 外部引用 |
| `core/util/DesbUtil` + `core/util/DateUtil` + `core/util/ComponentFactory` | 3 | 0 外部引用（ComponentFactory 是 D2 计划要"修"的，实测死代码，删不修） |
| `common/dataclean/*` | 2 | 0 外部引用 |
| `common/datamusterdefine/*` | 6 | 0 外部引用 |
| `common/media/*` | 3 | 0 外部引用 |
| `common/logupload/*` | 1 | 0 外部引用 |
| `common/flowmonitor/*` | 2 | 0 外部引用 |
| `common/shareservicemgr/*` | 2 | 0 外部引用 |
| `common/rmiservice/*` | 1 | 0 外部引用 |
| `common/permissions/*` | 2 | 0 外部引用 |
| `common/nodeversionmgr/*` | 1 | 0 外部引用 |
| `common/executelog/*` | 1 | 0 外部引用 |
| `common/beanfactory/*` | 1 | 0 外部引用 |
| `common/monitor/utils/ServiceCallConstans` | 1 | 0 外部引用 |
| `agent/AgentService` + `AgentServiceImpl` + `AgentProfile` + `SysManAgentProfile` + `AgentSessionInfo` | 5 | 0 外部引用（**agent 顶层 10 文件，只删这 5 个死代码，活跃的 AgentMessage/AgentResult/AgentRuntime/AgentSession/AgentException 保留**） |
| `config/ConfigServiceImpl` + `config/ConfigServiceFactory` | 2 | 0 外部引用 |

## §i18n 迁移（5 文件 → runtime-access）

迁 `runtime.core.i18n.*`（I18nUtils/I18nMessageSource/LocaleResolver/SpringMessageSourceImpl/I18nMessageSourceImpl）→ `runtime.access.util.i18n.*`，改 package + 消费方 import（`sysman/config/I18nConfig.java`，grep 兜底）。

## §Task

| Task | 内容 | 验收 |
|:--|------|------|
| T1 | 删 core 死代码 17 文件（license 6 + rpcpip 5 + controlcenter 1 + TaskLockService/NodePropties 2 + util 3） | 编译通过 |
| T2 | 删 common 死代码 23 文件（dataclean/datamusterdefine/media/logupload/flowmonitor/shareservicemgr/rmiservice/permissions/nodeversionmgr/executelog/beanfactory/monitor.utils） | 编译通过 |
| T3 | 删 agent 顶层 5 死代码 + config 2 死代码 | 编译通过 |
| T4 | 迁 i18n 5 文件到 runtime-access，改消费方 import | 编译通过 |
| T5 | 全量编译 + 跑硬门禁 | BUILD SUCCESS + grep 0 匹配 |

### 删前确认命令（T1-T3 通用，每个包删前跑）

```bash
cd /home/guorongxiao/ECOS/ecos_backend
grep -rln "runtime.core.<包名>" --include="*.java" . | grep -v target | grep -v "/runtime/runtime-core/"
# 期望: 0 匹配（若命中则停，该包是活跃的，汇报）
```

## §禁止清单

1. ❌ **不删活跃的 common 历史遗留**（dataobjectmgr/datasourcemgr/dxflow/exsharemgr/fileupload/nodemgr/params/rpccaller/sysvar/version/dbdata/util/PageInfo/util/LegacyListInfo 等，被 sysman/engine 引用）——归 A+9
2. ❌ **不整包删 agent/**（只删列出的 5 个死代码文件，AgentMessage/AgentResult/AgentRuntime/AgentSession/AgentException 是活跃的）
3. ❌ **不删 agent/mesh(14)**（aimod 还依赖，A+3 遗留的循环依赖，单独处理）
4. ❌ 不碰 database(4)/config 活跃部分（IConfigService/ConfigDaoImpl 等）
5. ❌ 删前不 grep 就删
6. ❌ 不用 `mvn compile` 替代 `mvn install`

## §验证门禁

```bash
env -i HOME=/home/guorongxiao \
  PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin \
  JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
# 期望: BUILD SUCCESS

# 硬门禁1: 已删死代码 0 残留（抽查 license/dataclean/beanfactory）
grep -rln "runtime.core.\(core.license\|common.dataclean\|common.beanfactory\|core.controlcenter\)" --include="*.java" . | grep -v target
# 期望: 0 匹配

# 硬门禁2: i18n 已迁走
grep -rln "runtime.core.i18n" --include="*.java" . | grep -v target | grep -v "/runtime/runtime-access/"
# 期望: 0 匹配

# runtime-core 文件数（A+6/A+7 后 194，本指令后应 ~142）
find runtime/runtime-core/src/main/java -name "*.java" | wc -l
```

## §工时

0.5-1 天（47 死代码删除 + 5 i18n 迁移 + 消费方改写 + 编译）。

## §风险

- **死代码内部成链**：license/rpcpip/datamusterdefine 等包内部可能互相 import（整链 0 外部引用），删时整包删即可，不要只删部分。
- **agent 顶层边界**：只删 5 个列出的死代码，误删 AgentRuntime/AgentSession 会导致 ai-engine 编译失败（它们被 ai-engine 活跃 import）。
- **i18n 消费方**：迁走后 sysman/I18nConfig 要改 import 到 runtime.access.util.i18n，漏改编译报错。
- **`.m2` 旧 JAR**：删包后全量 install，若报旧类冲突，删 `~/.m2/repository/com/chinacreator/gzcm/runtime-core*`。
