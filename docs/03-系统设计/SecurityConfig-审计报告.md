# SecurityConfig 白名单审计报告

> 审计日期：2026-06-18  
> 审计对象：`SecurityConfig.java`  
> 项目：ECOS — DataBridge-GZCM v2 (sys-man-service)  
> Context Path：`/sys-man`  
> 认证方式：JWT Bearer Token，无状态 Session  

---

## 一、当前白名单配置

```java
.requestMatchers(
    "/auth/**",                    // ① 认证接口
    "/api/health",                 // ② 健康检查
    "/sys-man/api/health",         // ③ 健康检查（冗余）
    "/error",                      // ④ Spring Boot 错误页
    "/api/agent-mesh/**",          // ⑤ Agent Mesh API（全部）
    "/sys-man/api/agent-mesh/**"   // ⑥ Agent Mesh API（冗余）
).permitAll()
```

其余所有路径通过 `.anyRequest().authenticated()` 要求认证。

---

## 二、逐项审计

### ① `/auth/**` → ✅ 保持 permitAll

**映射 Controller**：`AuthController`（`@RequestMapping({"/api/v1/auth", "/auth"})`）

**端点**：
- `POST /auth/login` — 用户登录，获取 JWT
- `GET /auth/me` — 获取当前用户信息（依赖 Token 但路径免认证，由 Controller 自行验证）
- `POST /auth/refresh` — 刷新 Token

**判定**：✅ **保留 permitAll**

**理由**：登录和 Token 刷新是认证入口，必须在认证之前可访问。`/auth/me` 虽然涉及用户信息，但 Controller 内部自行校验 Authorization Header，不影响安全性。

---

### ② `/api/health` → ✅ 保持 permitAll

**映射 Controller**：`HealthController`（`@RequestMapping("/api")` → `GET /api/health`）

**端点**：
- `GET /api/health` — 返回 `{"status":"UP","service":"sys-man-service","timestamp":"..."}`

**判定**：✅ **保留 permitAll**

**理由**：健康检查是基础设施端点，供 Kubernetes 探针、负载均衡器、监控系统使用。不暴露敏感数据，无需认证。

---

### ③ `/sys-man/api/health` → ⚠️ 建议移除

**判定**：⚠️ **冗余路径，建议移除**

**理由**：`server.servlet.context-path=/sys-man` 已在 application.yml 中配置。Spring Security 的 `requestMatchers` 匹配的是 **去掉 context-path 后的路径**。因此：

- `"/api/health"` → 匹配请求 `GET /sys-man/api/health` ✅
- `"/sys-man/api/health"` → 匹配请求 `GET /sys-man/sys-man/api/health` ❌（永不发生）

该条目是**死代码**，不会生效。虽然无害，但会造成混淆，且 application.yml 中 `auth.whitelist.paths` 也存在同样的冗余。

---

### ④ `/error` → ✅ 保持 permitAll

**判定**：✅ **保留 permitAll**

**理由**：Spring Boot 的默认错误处理路径。当请求发生 401/403/404/500 等错误时，容器会 forward 到 `/error`。如果该路径要求认证，将导致认证失败时陷入无限重定向或返回空白页。

---

### ⑤ `/api/agent-mesh/**` → ❌ 应改为 authenticated

**映射 Controller**：`AgentMeshController`（`@RequestMapping("/api/agent-mesh")`）

**当前暴露的 10 个端点（全部免认证）**：

| 方法 | 路径 | 操作 | 风险等级 |
|------|------|------|----------|
| `GET` | `/api/agent-mesh/agents` | 列出所有 Agent | 中 |
| `GET` | `/api/agent-mesh/agents/{id}` | 查询单个 Agent | 中 |
| `POST` | `/api/agent-mesh/agents` | **注册 Agent** | 🔴 高 |
| `PUT` | `/api/agent-mesh/agents/{id}` | **更新 Agent** | 🔴 高 |
| `DELETE` | `/api/agent-mesh/agents/{id}` | **删除 Agent** | 🔴 高 |
| `GET` | `/api/agent-mesh/missions` | 列出 Mission | 中 |
| `GET` | `/api/agent-mesh/missions/{id}` | 查询 Mission 详情 | 中 |
| `POST` | `/api/agent-mesh/missions` | **创建 Mission** | 🔴 高 |
| `POST` | `/api/agent-mesh/missions/{id}/execute` | **执行 Mission** | 🔴 高 |
| `GET` | `/api/agent-mesh/missions/{id}/tasks` | 查询 Mission 子任务 | 中 |

**判定**：❌ **应改为 authenticated（或至少区分读写权限）**

**理由**：

1. **Agent 注册/删除是管理操作**：`POST /agents` 注册新 Agent、`DELETE /agents/{id}` 删除 Agent 是典型的后台管理功能，不应对外公开。任何人都可以注册恶意 Agent 或删除合法 Agent。

2. **Mission 创建/执行是运营操作**：`POST /missions` 创建协作任务、`POST /missions/{id}/execute` 触发执行，属于受控操作。无认证的情况下可能被滥用，消耗系统资源。

3. **与项目其他 Agent 端点不一致**：同一项目中，`AgentConfigController` (`/api/v1/agents`)、`AgentCallController` (`/api/v1/agent`)、`AgentProfileController` (`/api/v1/agent/profiles`) 均要求认证（默认由 `anyRequest().authenticated()` 控制），唯独 AgentMesh 全路径放行，存在设计不一致。

4. **P0 级安全风险**：当前配置下，无需任何凭证即可：
   - 注册任意数量的 Agent
   - 删除系统中所有已注册的 Agent
   - 创建并执行任意 Mission（可能触发 LLM 调用，产生费用）

**建议方案**：

```java
// 推荐：全部改为需要认证
.requestMatchers("/api/agent-mesh/**").authenticated()

// 或：精细控制——只读端点 permitAll，写操作 authenticated
// GET 端点免认证（只读），POST/PUT/DELETE 需要认证
// 但 Spring Security requestMatchers 不支持按 HTTP 方法区分放行/认证，
// 如需此粒度，需在 Controller 方法上加 @PreAuthorize 注解
```

**推荐方案**：全部改为 `authenticated()`。Agent Mesh 是内部协作平台，没有必须公开的端点。如果后续有对外暴露场景（如第三方 Agent 注册），应通过 API Gateway 层面的 AppKey/AppSecret 鉴权，而非在 SecurityConfig 中直接放行。

---

### ⑥ `/sys-man/api/agent-mesh/**` → ⚠️ 建议移除

**判定**：⚠️ **冗余路径，建议移除**

**理由**：与条目③同理，context-path 已处理 `/sys-man` 前缀，该条目永远不会匹配到实际请求。

---

## 三、遗漏检查

### 是否需要新增白名单路径？

| 检查项 | 是否存在 | 是否需要放行 | 结论 |
|--------|---------|-------------|------|
| Swagger UI (`/swagger-ui/**`) | ❌ 未引入 springdoc/swagger 依赖 | — | 无需操作 |
| OpenAPI docs (`/v3/api-docs/**`) | ❌ 同上 | — | 无需操作 |
| Actuator (`/actuator/**`) | ❌ 未引入 spring-boot-starter-actuator | — | 无需操作 |
| 静态资源 (`/static/**`, `/public/**`) | — | Spring Security 默认放行 | 无需显式配置 |
| CORS 预检 (`OPTIONS` 请求) | ✅ CorsConfig 配置了 CorsFilter | Filter 级别处理，无需 Security 放行 | 无需操作 |
| WebSocket (`/ws/**`) | ❌ 未发现 STOMP/WebSocket 端点 | — | 无需操作 |

**结论**：当前项目没有遗漏必须放行的公开端点。

---

## 四、application.yml 白名单对比

`application.yml` 中存在独立的 `auth.whitelist.paths` 配置：

```yaml
auth:
  whitelist:
    paths:
      - "/api/health"
      - "/sys-man/api/health"        # 冗余
      - "/sys-man/auth/**"           # 与 SecurityConfig 不一致（带了 context-path）
      - "/api/agent-mesh/**"
      - "/sys-man/api/agent-mesh/**" # 冗余
      - "/error"
```

**不一致点**：
- SecurityConfig 写 `/auth/**`，yml 写 `/sys-man/auth/**`。在 SecurityConfig 中 context-path 已被剥离，正确写法是 `/auth/**`；yml 的写法取决于自定义拦截器如何处理路径。
- yml 缺少 `/auth/**`（登录路径）。若存在自定义拦截器读取该 whitelist，可能导致登录接口也被拦截。

**建议**：统一两处配置的路径格式，避免维护歧义。

---

## 五、审计结论与修正建议

### 推荐白名单配置

```java
.requestMatchers(
    "/auth/**",           // ✅ 认证接口（登录、刷新 Token）
    "/api/health",        // ✅ 健康检查
    "/error"              // ✅ Spring Boot 错误页
).permitAll()
// 删除：
//   "/sys-man/api/health"        — 冗余，context-path 已剥离
//   "/api/agent-mesh/**"         — 应改为 authenticated
//   "/sys-man/api/agent-mesh/**" — 冗余
```

### 风险等级

| 风险 | 等级 | 说明 |
|------|------|------|
| Agent 注册/删除无认证 | 🔴 **P0-严重** | 无需凭证即可注册恶意 Agent、删除合法 Agent |
| Mission 创建/执行无认证 | 🔴 **P0-严重** | 可能被滥用执行任意任务，消耗 LLM 资源 |
| 冗余白名单路径 | 🟡 P3-建议 | 不会造成安全漏洞，但增加维护困惑 |

### 修复优先级

1. **立即**：将 `/api/agent-mesh/**` 从 `permitAll()` 改为 `authenticated()`
2. **同步**：同步修改 `application.yml` 中的 `auth.whitelist.paths`，移除 agent-mesh 路径
3. **清理**：移除两条冗余的 `/sys-man/...` 路径
4. **验证**：修复后验证 Agent Mesh 端点返回 401，带有效 Token 后正常访问

---

*本报告仅作审计分析，不包含代码修改。*
