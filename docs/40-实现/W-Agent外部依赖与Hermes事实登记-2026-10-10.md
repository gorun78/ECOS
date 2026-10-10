# W Agent 外部依赖与 Hermes 事实登记（W254 / W256 · 2026-10-10）

> 依据 = 《详细设计-10 W Agent 编排与DIK-C赋能-2026-09-29》§七 M0 直改项 **W254**（C236，R-39：附件 A-08"Hermes 仅参考"与实测相反）与 **W256**（C238，R-39：外部依赖许可与供应链审查 0 登记）。
> 本档为**登记性交付**：只做事实核对与缺口登记，不改任何业务代码、不跑 `mvn`（并行会话占构建锁，编译验证由后续终验统一做）、不 `git commit/push`。
> 实测时间戳：设计轮 2026-09-29（X-10~X-14 原载 §1.2 事实表）；本窗复验 2026-10-10（命令见 §六，全部只读）。

---

## 一、X-10 ~ X-14 事实链（then → now）

| # | 设计轮实测（2026-09-29，then） | 本窗复验（2026-10-10，now） | 结论 |
|:--|:--|:--|:--|
| **X-10** | `HermesMCPClient` 被 `CronJobController`（:4/:15/:26-27）与 `SkillController`（:4/:15/:26-27）编译期注入，`@Qualifier("ecosHermesMCPClient")` | 两 Controller 已改经 `AgentRuntimeProvider` SPI 取能力；全仓 `HermesMCPClient` 的**编译期 import 仅存于** `services/agent-service` 的 `AgentRuntimeProviderAdapter`（:22/:67/:75/:143，经 `ObjectProvider` 惰性获取，bean 缺席即抛 capability-unavailable）；ai-engine 侧仅注释文字提及（`CronJobController.java:23`、`SkillController.java:23`） | **已清偿**：compile-time 依赖降为 assembly-time 可选注入 |
| **X-11** | `AgentConfigService.java:178`、`AgentCallController.java:53`、`ClassificationController.java:68` 以"Hermes 引擎未就绪"为失败态；`QuotaFilter.java:207` 含 `/api/hermes/` 前缀；`TokenMeterAspect.java:51/87/95` 默认 model `"hermes-agent"` | 计量硬编码已清除并被门禁固化：`gateway/src/test/.../TokenMeterConfigTest.java`（详细设计-06 §八 接缝义务 5 / X-85）断言剥注释后可执行代码不得出现 `"hermes-agent"/"hermes_agent"/"gemini-1.5-pro"`，回落绑定配置单源 `llm.default-model`。**文字残留（本窗实测，非违例、登记待清）**：① `AgentConfigService.java:179` 仍含文案"Hermes 引擎未就绪…hermes-engine 服务"；② `ClassificationController.java:94` 仍有 `ErrorCodeMapper.Context.llm("hermes-agent")` **上下文标签**（测试门禁只覆盖 TokenMeterAspect，未覆盖此处）；③ `QuotaFilter.java:197` 仍保留 `/api/hermes/` 前缀匹配（配额面防御性多前缀匹配，兼容旧路由） | **部分清偿**：计量单源已闭环；①②③ 属文字/标签级残留，随卷 10 R2 批（#129/#130 端点实现）统一清理，不在本 M0 批范围 |
| **X-12** | `AgentLoopService.java:32` 自称"替代 Hermes Agent Loop 的纯 Java 实现"；`ModuleDependencyArchTest.java:275` 承认两套 Agent Runtime 并存 | 同进程并存问题经 SPI 收敛为**单实现装配**：契约在 `runtime/llm-gateway`（`com.chinacreator.gzcm.runtime.llm.agent.spi`，实测 9 接口 + 异常 + model 包：`AgentRuntimeProvider`/`AgentRuntimeCapability`/`AgentMcpCapability`/`AgentMemoryCapability`/`AgentPlannerCapability`/`AgentExecutorCapability`/`AgentEvolutionCapability`/`AgentToolRouterCapability`/`AgentRuntimeCapabilityUnavailableException`）；实现由 `services/agent-service` 的 `AgentRuntimeProviderAdapter` + `AgentRuntimeSpiMapping` 提供；方向红线由 `gateway/src/test/.../ModuleDependencyArchTest.java:366 engineMustNotDependOnServices` 把关（engine/runtime 一律禁依赖 `services..`，不留豁免；该用例 :352-363 Javadoc 明载此收敛史） | **已清偿（结构性）**：ai-engine 只认识 SPI，不认识 Hermes 类型 |
| **X-13** | 定时任务与 Skill 能力经 Hermes MCP `cronjob_*` 外部通道，违铁律 §2.5"调度一律走 runtime-task" → W255 | W255 本窗裁决 = **登记性注释**（Option B）：`CronJobController.create()` 已写入 W255 裁决 Javadoc——外部 provider 存在时即唯一执行器，若再经 `ITaskManagementService.submitTask` 注册 runtime-task 任务定义将造成同一 cron 表达式**双调度**；且 `TaskDescription` 为一次性任务模型（无周期字段），不具备承接条件；wagent 编排侧调度桥接归并行任务 #130（F10-15 `wait_task`）。详见设计册 §七 W255 行回写（随 R4 终验批） | **已裁决并登记**，硬接线随 R2-B/#130 |
| **X-14** | 外部依赖的许可与供应链审查记录**全仓 0**（附件 §14.1 自列威胁） | 本档 §三 即补建的首个登记；**许可实体仍未取得**（见 §三 逐项"未取得"），登记缺口从"无档"收敛为"有档、缺证" | **登记已建，许可证据待供给方提供** |

### 1.1 SPI 收口位置索引（now 权威路径）

| 角色 | 路径 |
|:--|:--|
| 契约（六能力 + 枚举 + 异常） | `ecos_backend/runtime/llm-gateway/src/main/java/com/chinacreator/gzcm/runtime/llm/agent/spi/`（含 `model/`） |
| 实现适配 | `ecos_backend/services/agent-service/src/main/java/com/chinacreator/gzcm/services/agent/runtime/spi/AgentRuntimeProviderAdapter.java`、`AgentRuntimeSpiMapping.java` |
| Hermes 桥（仅 service 内） | `.../services/agent/runtime/mcp/`：`HermesMCPClient.java`（`@Component("ecosHermesMCPClient")`，:22）、`HermesDelegationAdapter/MemoryAdapter/SessionAdapter.java` |
| stdio 对端脚本 | `tools/ecos-mcp-server.py`（"ECOS Hermes MCP Server" v1.0.0，MCP JSON-RPC 2.0 / protocolVersion 2024-11-05，暴露 memory/session/delegate/cronjob/skill 共 10 工具） |
| 方向门禁 | `ecos_backend/gateway/src/test/java/com/chinacreator/gzcm/gateway/ModuleDependencyArchTest.java:366`；唯一性 `.../gateway/AgentRuntimeUniquenessTest.java`；计量单源 `.../gateway/TokenMeterConfigTest.java` |
| 消费端（ai-engine） | `.../engine/ai/controller/{CronJob,Skill,AgentChat,Evolution,AgentProvider}Controller.java` |

### 1.2 附件 A-08 作废声明

**附件《Agent 协同/编排》A-08 前提"Hermes 仅为设计参考、不嵌入代码"已被 2026-09-29 实测推翻**（X-10~X-13：7 文件接线 + 计量默认值 + ArchUnit 承认并存），设计册 §〇（:19）已声明本册不以 A-08 为前提。**自 2026-10-10 起追加**：随 X-10/X-12 清偿，Hermes 的真实地位 = **"经 `AgentRuntimeProvider` SPI 接入的可选外部 provider"**（`AgentMcpCapability.java:8-11` Javadoc 原文口径），既非"仅参考"、亦非"编译期必需"。后续文档一律按本口径引用，不得复述 A-08 原文。
（旁证澄清：仓库根 `.hermes/` 目录（`config.json`/`env.md`）是**编码工具的工程画像配置**，与运行期 Hermes 引擎无关，勿混。）

---

## 二、MCP 通道风险与 fail-closed 行为（W254 影响面）

风险面（stdio JSON-RPC 到本机 Python 进程）：
1. **进程耦合**：`HermesMCPClient` 启动 `python3 tools/ecos-mcp-server.py`（stdio 一对一线条协议），失败仅 `log.warn` 后不带病上线（`HermesMCPClient.java:71`）→ bean 存在但通道未就绪；
2. **无鉴权/无 TLS**：stdio 通道本机进程级，无身份绑定；工具面含 `cronjob_create/pause`、`delegate_task`、`memory_store` —— 属**可写、可调度**高权限工具；
3. **许可/来源不明**（见 §三）：一旦外供引擎为闭源或 GPL 系，供应链合规风险由部署方承担；
4. **调度越权**（X-13）：`cronjob_*` 即"绕过 runtime-task 的第二调度面"，与铁律 §2.5 冲突——这正是 W255 只裁决不硬接线的根因之一。

fail-closed 实测口径（**provider 缺席/能力不可用 ⇒ 显式 501，绝不 200+空数据**）：
- `AgentChatController.java`：`CODE_RUNTIME_NOT_ASSEMBLED = 501`（:48）；bean 缺席 :112-117、能力不可用 :121-126；回显体 `{stub:true, capability, reason}`（:130-135）；
- `EvolutionController.java`：:23 类注释 + :36 常量 + :87-92 stub 体（多回显 `provider:"AgentRuntimeProvider"`）；
- `AgentProviderController.java`：目录不可得/为空 → 501+`stub=true`（:31/:50/:56/:90-99）；
- `SkillController.java`：:29 注释明载"现改为 code=501 + data.stub=true"；
- SPI 前置闸：`AgentMcpCapability.isAvailable()` 为 false 时调用方不得调 `callTool`（:22 及其 Javadoc :8-11：**许可+供应链审查（C238）是启用该 provider 的前置件**）；`AgentRuntimeProvider.java:98-102` 对 MCP 能力专门经 `isAvailable()` 判定。

---

## 三、W256 许可与供应链登记表（C238）

| 依赖对象 | 形态 | 许可证据 | 供应链审查 | 处置 |
|:--|:--|:--|:--|:--|
| **Hermes Agent 引擎本体** | 仓内**无**其源码/发行物（本窗 `find -maxdepth 3 -iname *hermes*` 实测仅 `.hermes/` 工具配置目录）；引擎以何进程/介质提供 = **未证** | **未取得，登记缺口**（无 LICENSE、无来源 URL、无版本锚定） | 未做 | **启用前置件**：供给方须交来源+许可+版本 SBOM；未交前 `isAvailable()` 必须保持 false |
| `tools/ecos-mcp-server.py` | 仓内自研桥脚本（v1.0.0） | 文件**无许可/版权头**（:1-13 实测无 SPDX）——仓内代码默认归属随项目 CLA，但**脚本若派生自第三方须声明**：未证，登记缺口 | 未做 | 补许可头 + 依赖清单（本窗实测其 import 仅 `json/sys/os` 标准库，无第三方 pip 依赖 ⇒ 供应链面小） |
| **Java 侧 MCP SDK** | **未引入**：`services/agent-service` pom 实测 0 命中 `modelcontextprotocol/mcp`（`HermesMCPClient` 为手写 JSON-RPC over stdio，仅依赖 jackson/slf4j/spring） | 不适用（无第三方库） | 不适用 | 若后续换用官方 MCP SDK，须按本表重新登记 |
| MCP 协议规范 | 规范文本（protocolVersion `2024-11-05`），非代码制品 | 规范本身不产许可义务 | 不适用 | 留档版本号即可 |
| 通道运行时（Python 解释器 + OS） | 部署环境自带 | 随部署方环境 | 未做 | 部署基线登记归运维册（非本卷） |

**缺口总结（如实）**：W256 要求的是"登记"而非"取得许可"。本窗可交付 = 上表 + 前置件规则（未登记 ⇒ fail-closed）；**Hermes 引擎本体许可与来源证明在仓内任何位置均不存在**，不得宣称"合规已达成"，只能宣称"缺口已显式化且默认关闭"。

---

## 四、与 W248/W261 的边界

本档不重复 DDL 订正内容（`ecos_agent`→`ecos_ai` 逐处记录、agent_cost 分区定性见《DDL迁移脚本落地登记-2026-09-30》§十一，2026-10-10 追加批）。

## 五、遗留项索引

1. X-11 文字残留三处（§一 表）→ 随 R2 批清理，建议清理时将 `TokenMeterConfigTest` 的禁字面量断言扩展至 `ClassificationController` 所在包；
2. Hermes 引擎许可证据 → 待供给方；
3. W255 硬桥接（wagent `wait_task` → runtime-task）→ 归 #130（F10-15）；
4. A-08 相关 PRD 文字回写 → 随 R4 终验批设计册 v1.4 回写。

## 六、复验命令（只读）

```bash
# ① 编译期 Hermes 依赖是否仍收口在 agent-service（期望：import 仅 Adapter + 自身包，Controller 侧 0 import）
grep -rn "import .*runtime\.mcp\.HermesMCPClient" ecos_backend/
grep -rn "HermesMCPClient" ecos_backend/engine/            # 期望：仅注释文字命中

# ② 计量模型名门禁在位
grep -n "hermes-agent" ecos_backend/gateway/src/test/java/com/chinacreator/gzcm/gateway/TokenMeterConfigTest.java

# ③ 方向门禁在位
grep -n "engineMustNotDependOnServices" ecos_backend/gateway/src/test/java/com/chinacreator/gzcm/gateway/ModuleDependencyArchTest.java

# ④ fail-closed 回显（期望 501 + stub 命中多文件）
grep -rn "CODE_RUNTIME_NOT_ASSEMBLED\|stub\", true\|stub=true" ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java

# ⑤ MCP SDK 未引入（期望 0 命中）
grep -rin "modelcontextprotocol" ecos_backend/services/agent-service/pom.xml ecos_backend/pom.xml

# ⑥ 仓内 Hermes 实体文件盘点（期望仅 .hermes/ 工具配置 + tools/ecos-mcp-server.py）
find . -maxdepth 3 -iname "*hermes*" -not -path "./.qoder/*" -not -path "./.git/*"

# ⑦ 桥脚本第三方依赖面（期望仅标准库）
grep -n "^import\|^from" tools/ecos-mcp-server.py
```

---

**登记人**：R1-B M0 清偿窗（2026-10-10）。**本窗未执行**：`mvn`（并行会话占构建锁）、`psql`、`git commit/push`；以上复验命令均为只读 grep/find，实测结果已内嵌于表中。
