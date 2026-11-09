# ecos-cli — ECOS 统一 CLI（只读 MVP）

领域 API 的**调用入口**，不承载业务逻辑。严格只经 gateway 发 HTTP，每条命令映射一个 OpenAPI `operationId`。

> 依据：ADR-21（Accepted）、详细设计第 16 篇、PRD-01 `REQ-PLT-26/27`、合并需求册 `REQ-CLI-01~14`（R-71 ① 首批只读）。
> 状态登记：`docs/40-实现/CLI-MVP落地登记-2026-11-09.md`（唯一口径）。

## 构建与运行

```bash
# 构建（独立 Maven 工程，不在 ecos_backend reactor 内；不新增模块/容器）
mvn -o -f tools/ecos-cli/pom.xml package

java -jar tools/ecos-cli/target/ecos-cli.jar --help
java -jar tools/ecos-cli/target/ecos-cli.jar capabilities list
java -jar tools/ecos-cli/target/ecos-cli.jar doctor
java -jar tools/ecos-cli/target/ecos-cli.jar cognition models list --model-kind CM-03
```

要求 JDK 17+。`JAVA_HOME` 失效时可用 `ECOS_JAVA` 指定解释器。

## 凭据

顺序：`--token` → `ECOS_TOKEN` → `--token-file <path>` → `--profile <name>`
（profile 文件：`~/.config/ecos/cli-<name>.properties`，键 `baseUrl` / `token`）。
**凭据永不回显**：输出与错误消息统一走 `Redactor`。

## 统一参数

| 参数 | 说明 |
|:--|:--|
| `--output json\|table` | 默认 `json`（面向智能体） |
| `--base-url URL` | 默认 `http://localhost:8080`；**禁指向 service 端口**（门禁扫描） |
| `--trace-id ID` | 全链 trace 透传（无则生成 `cli-<uuid>`） |
| `--timeout MS` | 1~120000，默认 30000 |
| `--dry-run` | 只打印调用计划，不发请求 |

## 输出契约

所有命令输出固定六字段：`ok / status / data / error / trace_id / source_refs`。
`status` ∈ `SUCCESS | FAILED | BLOCKED | UNAVAILABLE | PARTIAL`；`ok` 只表示调用完成，不等于业务条件满足。
`doctor` 的逐项检查为 `PASS | FAIL | BLOCKED | UNKNOWN`（禁空默认值）。

退出码：`0 SUCCESS / 1 FAILED / 2 BLOCKED / 3 UNAVAILABLE / 4 PARTIAL / 64 用法错误`。

## 命令清单（首批，全部只读）

| 命令 | operationId | 说明 |
|:--|:--|:--|
| `doctor` | `getGatewayHealth` + `listCognitiveModels` + `getMonitorHealth` + `getCurrentUserProfile` | 网关/认知读面/监控/凭据四项自检 |
| `cognition models list` | `listCognitiveModels` | 认知模型注册表只读列表 |
| `capabilities list` | — | 命令发现与自描述（含操作映射与实现状态） |

## 契约清单（禁手写 URL）

`src/main/resources/contract/ops.json` 由契约生成，命令只持 `operationId`。契约变更后重新生成并提交：

```bash
mvn -o -f tools/ecos-cli/pom.xml test -Dcli.gen=true
```

清单与契约的绑定由两处门禁守护：

- `tools/ecos-cli` → `ContractManifestTest`（生成/比对/参数名溯源）
- `ecos_backend/gateway` → `CliContractParityTest`（命令↔operationId、禁 service 端口、契约路径↔Controller、清单↔契约逐条同源）

E2E：`ecos-tests/tests/api/cli-l3-approval.spec.ts`（只读守卫 + 活体 doctor/models + 凭据不回显）。

## 红线

- 不新建 Maven 模块 / Docker 容器（本 pom 不在 reactor，两个计数门禁不受影响）
- 只经 gateway；出现 service 端口或直连库串即门禁 FAIL
- L3 写命令必须 `--approval-token` 且服务端 ABAC 复核；**当前无写命令**
- CLI 内不得出现口径/评分/路由等业务计算
