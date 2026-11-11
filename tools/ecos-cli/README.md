# ecos-cli — ECOS 统一 CLI

领域 API 的**调用入口**，不承载业务逻辑。严格只经 gateway 发 HTTP，每条命令映射一个 OpenAPI `operationId`。

> 依据：ADR-21（Accepted）、详细设计第 16 篇、PRD-01 `REQ-PLT-26/27`、合并需求册 `REQ-CLI-01~14`。
> 状态登记：`docs/40-实现/CLI-MVP落地登记-2026-11-09.md`（唯一口径）。

## 构建与运行

```bash
# 构建（独立 Maven 工程，不在 ecos_backend reactor 内；不新增模块/容器）
mvn -o -f tools/ecos-cli/pom.xml package

java -jar tools/ecos-cli/target/ecos-cli.jar --help
java -jar tools/ecos-cli/target/ecos-cli.jar capabilities list
java -jar tools/ecos-cli/target/ecos-cli.jar doctor
java -jar tools/ecos-cli/target/ecos-cli.jar agent inspect --run-id <runId> --dry-run
```

要求 JDK 17+。`JAVA_HOME` 失效时可用 `ECOS_JAVA` 指定解释器。

## 安装（jar + 包装器，不改 PATH）

```bash
bash tools/ecos-cli/dist/install.sh          # Linux/macOS/Git Bash
powershell -NoProfile -ExecutionPolicy Bypass -File tools/ecos-cli/dist/install.ps1   # Windows
```

安装到 `~/.ecos-cli/`（`lib/ecos-cli.jar` + `bin/ecos`），最后**只打印** PATH 指引，不自动修改用户环境。

## 补全（由命令树生成，禁手改）

```bash
ecos --completion bash > tools/ecos-cli/dist/completions/ecos.bash   # source 后生效
ecos --completion ps1  > tools/ecos-cli/dist/completions/ecos.ps1    # dot-source 后生效
```

`dist/completions/` 下两份文件是**生成产物**：命令树增删后必须重新生成，`CompletionScriptTest` 会逐字比对仓库文件与生成结果，过期即红。未知 shell 返回 `USAGE`(64)。

## 容器（独立构建，不进 compose 基线）

```bash
docker build -f tools/ecos-cli/Dockerfile --build-arg BUILD_ID=$(git rev-parse --short=8 HEAD) -t ecos-cli:local .
docker run --rm -e ECOS_CLI_BASE_URL=http://host.docker.internal:8080 -e ECOS_TOKEN=... ecos-cli:local doctor
```

多阶段构建，运行镜像仅含 JRE 17 且为非 root；镜像内**不含任何凭据**。

## 凭据

顺序：`--token` → `ECOS_TOKEN` → `--token-file <path>` → `--profile <name>`
（profile 文件：`~/.config/ecos/cli-<name>.properties`，键 `baseUrl` / `token`）。
**凭据永不回显**：输出、错误消息、日志（`~/.cache/ecos-cli/logs/*.jsonl`）统一走 `Redactor`，日志不落 token/请求体/响应体。

## 配置分层

优先级（高 → 低）：`--flag` → `ECOS_CLI_*` 环境变量 → `~/.config/ecos/cli-<profile>.properties` → 内置默认。
`ecos --show-config` 打印生效配置（token 只显示是否已配置）。

环境变量：`ECOS_CLI_BASE_URL` / `ECOS_TOKEN` / `ECOS_CLI_OUTPUT` / `ECOS_CLI_TIMEOUT_MS` /
`ECOS_CLI_PROFILE` / `ECOS_CLI_TRACE_ID` / `ECOS_CLI_PAGE_SIZE` / `ECOS_CLI_LOG_LEVEL`。

## 统一参数

| 参数 | 说明 |
|:--|:--|
| `--output json\|table` | 默认 `json`（面向智能体） |
| `--base-url URL` | 默认 `http://localhost:8080`；**禁指向 service 端口**（门禁扫描） |
| `--trace-id ID` | 全链 trace 透传（无则生成 `cli-<uuid>`），后端响应头 `X-Trace-Id` 回读 |
| `--timeout MS` | 1~120000，默认 30000 |
| `--dry-run` | 只打印调用计划，不发请求 |
| `--no-retry` | 关闭自动重试（默认 GET 最多 3 次，指数退避 + 抖动，429 认 `Retry-After`；**429 且 `errorCode=E-WA-BUDGET` 一律不重试**——预算已耗尽，重试只会继续消耗配额，见设计册 §10.3.4） |
| `--all` / `--page-size` / `--max-pages` | 列表全量翻页（单页上限 500） |
| `--idempotency-key` | 写命令幂等键（缺省 `cli-<uuid>` 并回显）。**只有显式传入才允许自动重试**——缺省键服务端无从去重（仅 `startRun` 有幂等通道），拿它重试会重复落库 |
| `--approval-token` | L3 命令审批凭证（服务端校验；缺省即 `BLOCKED`） |
| `--body-json JSON` | 写命令补充请求体（字段形态由属主契约定义） |
| `--follow` | SSE 跟随：`agent inspect --follow` 走 `streamRunEvents`；服务端通道未就绪时如实 `UNAVAILABLE`（hint 指向轮询） |
| `--last-event-id` | SSE 续传游标（配合 `--follow`） |

**未知参数一律 `USAGE`(64) 拒绝**：解析器会与实际命令的声明参数（含全局参数、路径参数、体参数）做差集，拼错即报错并点名参数。
这不是形式主义——`--dry-run` 误拼成 `--dry-runn` 若被静默忽略，L3 写命令会带着审批凭证真发货。

## 输出契约

所有命令输出固定六字段：`ok / status / data / error / trace_id / source_refs`。
`status` ∈ `SUCCESS | FAILED | BLOCKED | UNAVAILABLE | PARTIAL`；`ok` 只表示调用完成，不等于业务条件满足。
`doctor` 的逐项检查为 `PASS | FAIL | BLOCKED | UNKNOWN`（禁空默认值）。

退出码：`0 SUCCESS / 1 FAILED / 2 BLOCKED / 3 UNAVAILABLE / 4 PARTIAL / 64 用法错误`。

## 命令清单（14 条设计命令 + 2 条 MVP 命令）

`⏳` = 已登记但执行面未接入（契约 `status=planned`），运行时输出 `UNAVAILABLE` + `source_refs`，**绝不**返回空集合伪装成功。

| 命令 | L | operationId | 状态 |
|:--|:--:|:--|:--|
| `doctor`（别名 `runtime doctor`） | L0 | `getGatewayHealth`/`listCognitiveModels`/`getMonitorHealth`/`getCurrentUserProfile` | 已接 |
| `capabilities list` | L0 | — | 已接 |
| `agent run` | L1 | `startRun` | 已接 |
| `agent inspect` | L0 | `getRun` | 已接 |
| `agent stop` | L1 | `cancelRun` | 已接 |
| `agent readiness` | L0 | `getReadiness` | 已接 |
| `ontology validate` | L1 | `validateOntologyCandidate` | 已接 |
| `ontology publish` | **L3** | `publishCandidate` | 已接（凭证消费 + 安全裁决 + Git 归档 + 落库） |
| `knowledge extract` | L2 | `createWagentCandidate` | 已接（初始 `generated`，不直写 KB） |
| `knowledge publish` | **L3** | `publishCandidate` | 已接（同 `ontology publish`） |
| `cognition model validate` | L1 | `validateCognitiveModel` | 已接（四类发布门禁真实裁决） |
| `cognition model run`（别名 `cognition run`） | L1 | `startCognitiveModelRun` | ⏳ 端点未实装 |
| `cognition models list` | L0 | `listCognitiveModels` | 已接 |
| `action commit` | **L3** | `commitAction` | 已接（凭证消费 + 安全裁决 + 属主 Engine 派发 + 落库） |
| `model list` | L0 | `listLlmModels` | 已接 |
| `model route` | L0 | `resolveModelRoute` | ⏳ 端点未实装 |

## 契约清单（禁手写 URL）

`src/main/resources/contract/ops.json` 由契约生成，命令只持 `operationId`。契约变更后重新生成并提交：

```bash
mvn -o -f tools/ecos-cli/pom.xml test -Dcli.gen=true
```

清单与契约的绑定由两处门禁守护：

- `tools/ecos-cli` → `ContractManifestTest`（生成/比对/query·path·body 参数溯源）
- `ecos_backend/gateway` → `CliContractParityTest`（命令↔operationId、禁 service 端口、契约路径↔Controller、清单↔契约逐条同源）

CI：`.github/workflows/ecos-cli-ci.yml`（`cli-build` / `cli-contract` / `cli-e2e` 三 job；E2E 仅 main push）。
E2E：`ecos-tests/tests/api/cli-l3-approval.spec.ts`。

## 红线

- 不新建 Maven 模块 / Docker 容器（本 pom 不在 reactor；CLI 镜像独立构建，不进 `ecos-docker` compose 基线）
- 只经 gateway；出现 service 端口或直连库串即门禁 FAIL
- 运行期同样受约束：`--base-url` 指向 18xxx（service）/19xxx（调试 boot）或存储端口（PG/Redis/Kafka/Neo4j/MinIO/ZK）一律 `USAGE`(64) 拒绝，不能靠传参绕过 gateway 的鉴权与审计
- L3 写命令必须 `--approval-token` 且服务端校验（一次性消费 + 四眼 + 过期）；缺凭证即 `BLOCKED`（exit 2），无效/越权/自审批即 `FAILED`（exit 1）
- CLI 内不得出现口径/评分/路由等业务计算；未接命令禁伪造结果
