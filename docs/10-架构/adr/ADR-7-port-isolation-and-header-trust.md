# ADR-7 — Port Isolation 与 Gateway 头信任链（含实现缺口声明）

> 来源: 需求检视报告 §四 G3-1/G3-2（用户批准 Q10：现状声明更正） | 日期: 2026-09-28 | 责任人: AI Agent
> 版本: v1.1（v1.0 为 `current-plan.md` 内联表述，从未独立成文；本文件为其权威载体）
> 裁决: **Accepted（决策成立）+ 实现状态更正为「部分实现」**
> 上游: 架构铁律 v2.0 §0.1/§2.4 + ADR-10（gateway-only）+ PRD-00 REQ-PLT-01/02/20
> 状态: 生效；§4 缺口为本设计周期 P0 交付项

---

## 1. 背景（Context）

微服务 v2 有 7 个 JAR，`gateway :8080` 是唯一对外入口，service 端口（18081~18086、18090）只应内网可达。跨服务调用的**身份上下文**依赖 gateway 校验 JWT 后注入的信任头 `X-ECOS-USER` / `X-ECOS-ROLE` / `X-ECOS-TENANT` 等，由 service 侧还原为安全上下文。

**实现状态核查（2026-09-28 实测，推翻了 PRD 的"已实现"声明）**：

| 环节 | PRD 声明 | 实测 | 证据 |
|:--|:--|:--|:--|
| gateway 校验后 **strip 外部传入的 `X-ECOS-*`** | 已实现（PMO-B） | **未实现**：gateway 只有 4 个过滤器 | `gateway/src/main/java/**/filter/{Quota,RateLimit,SecurityHeaders,VersionPrefixRewrite}Filter.java`；`grep -r "X-ECOS" gateway/src/main/java` = **0 命中** |
| gateway **重新注入** 信任头 | 已实现 | **未实现**（同上，`X-ECOS` 字面量仅出现在 `SecurityPolicyController.java:137` 的注释与判定分支） | 同左 |
| service 侧 **`HeaderAuthInterceptor` 还原身份** | 已实现（"security 不可用默认 DENY"） | **类不存在**；9 处引用全为 "Phase 3 落地" 注释 | `SysmanServiceApplication.java:23`、`DatanetServiceApplication.java:42`、`BuszhiServiceApplication.java:35`、`AimingServiceApplication.java:51`、`DcchengServiceApplication.java:39`、`WorkspaceServiceApplication.java:34`、`AimingInfrastructureConfig.java:15` 等 |
| service 端口内网可达 | 部分 | 端口未做网络层限制，依赖进程边界；**当前 service 端口裸调可用**（无身份还原即按未鉴权处理，实际是否 DENY 取决于各 service 自身 SecurityConfig，仅 sysman 有完整配置） | 见 §3 |

> **结论**：REQ-PLT-02 与 REQ-PLT-20 的"默认 DENY"目前是**设计意图而非既成事实**。所有把安全寄托于"gateway 会挡"的下游设计（含 REQ-AI-01 护栏、REQ-SEC-01 领域链路）都建立在这个空声明之上。

## 2. 决策（Decision）

1. **信任头命名固定**：`X-ECOS-USER` / `X-ECOS-ROLE` / `X-ECOS-TENANT` / `X-ECOS-DOMAIN`（+ `X-Request-Id` 作为 traceId 载体，与信任头正交）。
2. **gateway 必须做「先剥离后注入」**：新增 `TrustHeaderRelayFilter`（`@Order` 早于路由分派、**晚于** `VersionPrefixRewriteFilter`）——无条件 `request.headers.remove("X-ECOS-*")`（外部传入一律丢弃），JWT 校验通过后再注入本跳生成值；校验失败即 401，**不透传任何信任头**。
3. **service 侧必须有独立防线（默认 DENY 的真实落点）**：新增 `HeaderAuthInterceptor`（各 service `WebMvcConfigurer` 注册，覆盖 `/api/**` 与 `/api/v1/**` 双路径）：
   - 无 `X-ECOS-USER` → **403 DENY**（不是放行、不是兜底 anonymous）；
   - 有 `X-ECOS-USER` 但 `X-ECOS-*` 缺失/不完整 → 403；
   - 匿名白名单**只来自配置**（`ecos.security.anonymous-paths`），且该清单为 G5-8「匿名端点登记制度」的载体，登记在 `docs/40-实现/匿名端点登记表.md`；
   - security-engine（sysman:18081）不可用时：**读操作按缓存策略降级、写操作一律 DENY**（对齐铁律 §2.4-6），禁"降级放行"。
4. **纵深而非单点**：即使 gateway 已注入，service 也不得假设"能连上就是内网可信"。网络层隔离（compose/安全组）属运维加固项，不作为唯一防线。
5. **验收机械化**：三滤波器检查 + 头剥离回归纳入 CI（`GatewayRouteIntegrityTest` 扩展 + 新增 `TrustHeaderStripTest`（伪造头注入 → 断言下游收不到）+ 每 service 一条 `HeaderAuthDenyTest`（裸调 `/api/**` → 403））。

## 3. 影响（Consequences）

| 维度 | 影响 |
|:--|:--|
| 安全 | ⚠️ 现网 service 端口存在未鉴权裸调面（本 ADR §4 缺口 = P0）；修复后默认 DENY 才真正成立 |
| 工作量 | `TrustHeaderRelayFilter`（gateway 1 文件）+ `HeaderAuthInterceptor`（common-impl 1 文件 + 6 处注册）+ 3 组测试；不动既有 API 签名（REQ-NF-06） |
| 兼容 | 直连调试（`ECOS_LOCAL_DIRECT_SERVICE_PROXY=true` / vite 直连）会撞上 service DENY → 开发态需带手工注入头或本地 bypass 配置，须在详细设计 00 册写明 |
| 追溯 | REQ-PLT-02/20 的 PRD 现状列在需求基线修订中改为「未实现」，纳入分册 00/01 设计范围（改造项 C4） |

## 4. 缺口清单（本 ADR 的交付内容）

| # | 缺口 | 归属 | 验收 |
|:--|:--|:--:|:--|
| G7-1 | gateway 无 strip/re-inject | 00 册 | `TrustHeaderStripTest`：伪造 `X-ECOS-USER: admin` 经 :8080 → 下游收不到该头 |
| G7-2 | service 无 `HeaderAuthInterceptor` | 00/01 册 | 6 service 各一条裸调 403 测试 |
| G7-3 | 匿名路径无登记表与配置源 | 01 册 | `ecos.security.anonymous-paths` 单源 + 文档登记表齐 |
| G7-4 | security 不可用时的降级语义未定义（读/写分档） | 01 册 | 杀 sysman 后：写全拒、读按策略，实测记录 |
| G7-5 | `X-Request-Id` 未贯穿日志（无 `%X{traceId}`） | 00 册 | 一次跨引擎请求 traceId 全链可串（REQ-NF-04） |

## 5. 关联

ADR-10（入口收敛）、ADR-3（数据隔离）、架构铁律 §2.4（安全八条）、《后端开发规范》三滤波器章节、PMO-B（BFF 收敛）。

## 6. 变更日志

| 日期 | 版本 | 变更 | 责任人 |
|:--|:--|:--|:--|
| 2026-09-28 | v1.1 | 首次独立成文；实测更正"已实现"声明，定义 strip→注入→service DENY 三段责任与服务侧 DENY 落地要求 | AI Agent（需求检视 Q10） |
