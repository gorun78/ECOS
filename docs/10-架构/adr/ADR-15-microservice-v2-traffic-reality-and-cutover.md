# ADR-15 — 微服务 v2 流量现状口径与切流序列（Monolith-Authoritative）

> 来源: 详细设计 00 册 §1.2（实测发现） | 日期: 2026-09-28 | 责任人: AI Agent（架构治理）
> 版本: v1.1
> 裁决: **Accepted**（2026-09-29 随 ARCH_SPEC **Gate-1 第 8 项**由用户批量确认，凭证 = 需求检视报告 §十四；本 ADR 的"制品口径／承流口径分离 + 逐域 S0→S4 门禁"自此为全工程强制口径）
> 上游: ADR-7（端口隔离与信任链）、ADR-10（gateway-only 对外）、架构铁律 v2.0 §0.1/§0.2、`docs/10-架构/backend-module-inventory.md`
> 状态: 生效，全工程强制（各分册不得假设自身 service 独立承流）

---

## 1. 背景（Context）

`agents.md`、`ecos_backend/AGENTS.md`、`backend-module-inventory.md` 与 ARCH_SPEC §2.2 都以「微服务 v2 = 7 个独立可部署 JAR」为口径描述当前架构。详细设计 00 册编写时做源码实测（2026-09-28），结果与文档口径存在系统性偏差：

| 实测项 | 证据 | 结果 |
|:--|:--|:--|
| gateway 组件扫描范围 | `gateway/GatewayApplication.java:35-49` | 扫入 `gateway/common/sysman/runtime/buszhi/workspace/engine/**`（全部引擎 impl） |
| 引擎侧 Controller 总数 | `find engine/*/*-impl -name '*Controller.java'` | **154**（data 37 / ontology 34 / ai 32 / kb 21 / cognitive 17 / security 13 / workspace-impl 17），全部由 gateway 宿主 |
| 5 个 v2 service 自身 Controller | `find services/*/src/main -name '*Controller.java'` | sysman **0** / buszhi **0** / aiming **0** / dccheng **0** / datanet **3**（`DataLake`、`DqDashboard`、`Git`） |
| 切流开关 | `grep -rn ECOS_ROUTE` | `ECOS_ROUTE_datanet`、`ECOS_ROUTE_sysman` **仅出现在两处 javadoc 注释**，无任何读取代码 |
| 前端流量入口 | `ecos_frontend/server.ts:32,41,116` | 默认（`ECOS_LOCAL_DIRECT_SERVICE_PROXY=false`）全部 `/api/*` → `GATEWAY_URL:8080` |
| 跨 JVM 信任链 | `find -name 'HeaderAuth*'`、`grep -rn X-ECOS-` | 拦截器类不存在；`X-ECOS-USER/TENANT/DOMAIN` 零生产代码 |

**结论**：`7 JAR` 是**构建制品口径**，不是承流口径。实际承流 = **1 个 gateway 单体 fat-JAR**；5 个 service 处于「可构建、可启动、不承流」的空壳态（datanet 有 3 个 controller 与 gateway 双跑）。

这个偏差如果不在架构层面固化，会产生三类具体损害：

1. **设计悬空**：各引擎分册按"我的 service 独立承流"设计路由与鉴权，落地时发现流量根本不走它，或切流后 401/404。
2. **安全错觉**：ADR-7 的信任头链在单体态下"看起来不需要"（同 JVM 无 HTTP 跳），团队会误判 W02（`HeaderAuthInterceptor` 缺失）为低优先级，导致切流即越权可达。
3. **验收失真**：把"7 JAR 都能启动"当作微服务化完成的证据（`_win_tasks/start-backend.ps1 -Modules` 13 选 N 恰好助长这种误读）。

## 2. 决策（Decision）

### 2.1 双口径强制分离

- **制品口径**（构建/CI/部署产物）：`7 JAR` = gateway + services/{sysman,datanet,buszhi,aiming,dccheng} + workspace/workspace-service，保持不变，仍是 reactor 白名单与"不新增 Maven 模块"基线的依据。
- **承流口径**（运行时流量）：必须以 `docs/10-架构/refs/route-manifest.json` 的 `mode` 字段为唯一事实源，取值 `monolith`（gateway 宿主）或 `service`（独立承流）。文档、AGENTS.md、分册描述承流时**必须引用 mode**，不得笼统写"微服务 v2 已落地"。

### 2.2 切流序列 S0→S4（每个 service 独立走，逐步有门禁）

| 步 | 动作 | 前置 | 退出判据 |
|:--:|:--|:--|:--|
| **S0** | 现状基线：gateway 承全部流量 | — | 全量 E2E + 冒烟绿 |
| **S1** | service 侧补齐自身 controller（引擎 impl 的 `@RestController` **迁包**至 service 扫描范围，禁止复制类造成双实现） | 该引擎分册 E 章数据契约定稿 | `mvn -f ecos_backend/pom.xml -pl services/{x} -am install` 通过；service 单启后本域端点 `curl` 无 404 |
| **S2** | **信任链就位**：gateway `EcosTrustHeaderFilter`（strip→校验→re-inject）+ service `HeaderAuthInterceptor` + 服务间凭证 `X-ECOS-SERVICE` | ADR-7 §2.2/2.3；分册 00 W02/W05/W06 关闭 | `TrustHeaderStripTest` + `HeaderAuthDenyTest`（该 service）绿 |
| **S3** | 开切流开关 `ecos.route.<service>=service`（`ServiceEndpointResolver` 唯一实现，禁注释级"开关"），按前缀灰度 | S1+S2 完成，`route-manifest` 与本域前缀一致 | 双跑一致性：同一请求两态响应逐字段相等（除 `timestamp`）；审计与 traceId 在两态均贯通 |
| **S4** | gateway 从 `@ComponentScan` 移除该引擎 basePackages，fat-JAR 不再宿主该域 | S3 稳定 ≥1 迭代 | ArchUnit 新增规则「gateway 不得 import 已切流引擎 impl」绿；`backend-module-inventory.md` 承流矩阵更新 |

### 2.3 硬禁令

- **禁**跳过 S2 直接进入 S3/S4 —— 独立 service 无身份还原 = 越权面暴露（等同 ADR-7 的空声明风险）。
- **禁**在 S4 前删除 gateway 扫描条目 —— 端点直接 404。
- **禁**在 service 内复制引擎 Controller 类以"快速承流" —— 双实现必然漂移（PMO-57 已有 workflow 副本清理先例）。
- **禁**把 `vite.config.ts` 的 18 条 dev 直连当作生产承流证据（ADR-10 §2.2：dev 特权，生产只走 BFF→gateway）。
- **禁**在文档中把"可启动"写作"已微服务化"。

### 2.4 两态寻址（切流期的强制设计约束）

任何跨服务调用必须同时支持两态并由**同一配置项**切换：

```
monolith 态: http://127.0.0.1:8080/api/...   （gateway 宿主自身，同 JVM 时优先本地 Bean）
service  态: http://<host>:180xx/api/...      （由 route-manifest 解析）
唯一出口: runtime-access ServiceEndpointResolver#resolve(service, mode)
现存散落 @Value("${ecos.datanet.base-url:http://localhost:18082}") 保留为 override 通道（签名不改），默认值改由 manifest 生成
```

### 2.5 回退策略

- 切流后出现故障：置 `ecos.route.<service>=monolith` 回到 gateway 宿主态（S3 前 gateway 扫描未删，回退零成本）；回退动作记入本 ADR 变更日志并 48h 内给出修复。
- **不允许**的回退方式：在 BFF 侧加直连 service 端口的路由来"绕过 gateway"（违反 ADR-10）。

## 3. 后果（Consequences）

| 维度 | 影响 |
|:--|:--|
| 认知一致性 | ✅ 消除"7 JAR = 已微服务化"的误读，承流与制品分离，分册不再基于假底座排期 |
| 安全 | ✅ 把信任链（S2）设为切流前置，杜绝"拆了但没有身份链"的越权窗口 |
| 工期 | ⚠️ 每个域的 S1~S4 是真实工作量（154 controller 的迁包与双跑验证），必须逐域排期，不可整体切换 |
| 测试 | ⚠️ S3 需要双跑一致性对比工具与 E2E 工程（分册 00 C.8）先行，否则无验收抓手 |
| 文档 | ⚠️ `agents.md`、`ecos_backend/AGENTS.md`、`backend-module-inventory.md`、PRD-01 需按本 ADR 回写承流口径（任务 #26） |

## 4. 关联

- ADR-7：S2 是本 ADR 的强制前置，二者互为约束（端口隔离提供"能切"的物理条件，本 ADR 提供"何时可切"的次序条件）。
- ADR-10：对外入口唯一；本 ADR 管内部承流，两者不冲突但需分别表述。
- ADR-3/ADR-12：切流不改 schema 归属（单库 5+1 + Mapper 限定名），改的是流量位置。
- 详细设计 00 册 §1.2（本 ADR 的实测来源与工程化落点）、ARCH_SPEC §2.2（需增承流矩阵）、分册 01~07（各域 S1 清单）。

## 5. 变更日志

| 日期 | 版本 | 变更 | 责任人 |
|:--|:--|:--|:--|
| 2026-09-28 | v1.0 | 初版：登记微服务 v2 承流现状（1 承流 + 5 空壳 + 154 controller 由 gateway 宿主），确立制品/承流双口径与 S0→S4 切流门禁 | AI Agent（详细设计 00 册实测） |
| 2026-09-29 | v1.1 | 随需求检视报告 §十四 批量批准：裁决 **Proposed → Accepted**（Gate-1 第 8 项）。正文口径与决策未变，仅状态行定版；切流执行本身仍属"改业务代码／改部署"档，需逐项再授权（报告 §14.4） | AI Agent |
