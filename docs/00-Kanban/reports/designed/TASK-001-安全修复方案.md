# TASK-001: P0-安全修复方案设计

> **负责人：** ECOS-ARCH
> **状态：** DOING
> **版本：** v1.0
> **日期：** 2026-06-16
> **涉及仓库：** `databridge-v2/databridge-sysman`

---

## 1. P0-01 SQL注入修复

### 风险分析

**位置：** `sysman-impl` — `ObjectController.java`

**风险等级：** 严重（CVSS 9.8）

**根因：** `@PostMapping("/{entityCode}")` 的 `createObject` 和 `@PutMapping("/{entityCode}/{id}")` 的 `updateObject` 方法中，将用户通过 `@RequestBody` 传入的 `Map<String, Object> body` 的 **key（列名）** 直接拼接进 SQL 语句：

```java
// ObjectController.java 第226行 — SQL注入漏洞
for (Map.Entry<String, Object> e : body.entrySet()) {
    sql.append(e.getKey());  // ← 攻击者可传入 "); DROP TABLE demo_customer; --"
    vals.append("?");
    params.add(e.getValue());
}
```

**攻击向量：**
- 攻击者发送 `POST /api/v1/ecos/objects/Customer` 并传入 body key 为 `"name); DROP TABLE demo_customer; --"`
- 最终 SQL 变为 `INSERT INTO demo_customer (name); DROP TABLE demo_customer; --) VALUES (?)`
- 数据库执行 DROP TABLE，造成数据永久丢失

**注：** 虽然 `entityCode` → `table` 经由白名单 `ENTITY_TABLE` 映射（较为安全），但列名/字段名 `e.getKey()` 是直接拼接的，是 SQL 注入的真正入口。

**所有受影响的方法列表：**

| 方法 | URL | 风险级别 | 注入点 |
|------|-----|----------|--------|
| `createObject` | POST `/{entityCode}` | **严重** | `body.keySet()` 直接拼入 INSERT |
| `updateObject` | PUT `/{entityCode}/{id}` | **严重** | `body.keySet()` 直接拼入 SET 子句 |
| `listObjects` | GET `/{entityCode}` | 低 | table 名已白名单校验，keyword 已参数化 |
| `getDetail` | GET `/{entityCode}/{id}` | 低 | table 已白名单，id 已参数化 |
| `search` | GET `/search` | 低 | table 已白名单，q 已参数化 |
| `updateStatus` | PUT `/{entityCode}/{id}/status` | 低 | table 已白名单，参数已参数化 |

---

### 设计方案

**策略：列名白名单校验 + 参数化查询重构**

核心原则：
1. **绝不信任用户输入的列名** — 从 `information_schema.columns` 获取实体允许的列名集合
2. **传入的列名必须在白名单内** — 不在白名单中的 key 直接忽略或抛异常
3. **值部分已经使用参数化占位符 `?`** — 保持现状，不做改动

#### 方案 A（推荐）：动态 Schema 校验

```java
// 1. 新增工具方法 — 获取实体验证通过的列名列表
private List<String> validateColumns(String table, Set<String> inputKeys) {
    // 从缓存或数据库查询 information_schema.columns
    Set<String> allowed = getAllowedColumns(table);
    
    List<String> valid = new ArrayList<>();
    for (String key : inputKeys) {
        if (!allowed.contains(key)) {
            throw new IllegalArgumentException("非法字段: " + key);
        }
        valid.add(key);
    }
    return valid;
}

// 2. 重写 createObject — 仅使用白名单校验后的列名
@PostMapping("/{entityCode}")
public ApiResponse<Map<String, Object>> createObject(
        @PathVariable String entityCode,
        @RequestBody Map<String, Object> body) {
    String table = ENTITY_TABLE.get(entityCode);
    if (table == null) return ApiResponse.notFound("实体 " + entityCode + " 不存在");

    // ★ 关键修复：白名单校验列名
    List<String> validColumns = validateColumns(table, body.keySet());

    StringBuilder sql = new StringBuilder("INSERT INTO " + table + " (");
    StringBuilder vals = new StringBuilder(" VALUES (");
    List<Object> params = new ArrayList<>();

    boolean first = true;
    for (String col : validColumns) {  // ← 只遍历校验后的列名
        if (!first) { sql.append(", "); vals.append(", "); }
        sql.append("\"").append(col.replace("\"", "\"\"")).append("\"");  // 双引号转义
        vals.append("?");
        params.add(body.get(col));
        first = false;
    }
    sql.append(")");
    vals.append(")");
    jdbc.update(sql.toString() + vals.toString(), params.toArray());

    Object idObj = body.get("id");
    if (idObj != null) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM " + table + " WHERE id = ?", idObj.toString());
        if (!rows.isEmpty()) return ApiResponse.success(rows.get(0));
    }
    return ApiResponse.success(body);
}
```

#### 方案 B（轻量）：静态列名白名单

若当前只有 3 个 demo 表，可先硬编码列名白名单，后续接入 `information_schema`：

```java
private static final Map<String, Set<String>> TABLE_COLUMNS = Map.of(
    "demo_customer", Set.of("id","name","industry","region","level","credit_score","status","created_at"),
    "demo_supplier", Set.of("id","name","industry","region","level","supply_capacity","status","created_at"),
    "demo_invoice",  Set.of("id","amount","customer_id","supplier_id","status","created_at")
);
```

**推荐走向：** 方案 A → 缓存 `information_schema.columns` 结果（`LoadingCache` / 定时刷新），支持 Schema 动态变更。

#### 修复后安全控制流

```
用户请求 → entityCode 白名单映射 table → body.keySet() 白名单校验列名 → 
└─ 通过: 构造 SQL（列名加双引号保护 + 值参数化）
└─ 不通过: 返回 400 非法字段
```

---

### 变更文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `sysman-impl/.../controller/ObjectController.java` | 修改 | 新增 `validateColumns()`、重构 `createObject`/`updateObject` |

### 影响范围

| 维度 | 说明 |
|------|------|
| 所在工程 | `sysman-impl`（databridge-sysman） |
| 对外 API | `POST/PUT /api/v1/ecos/objects/{entityCode}` 行为变更 |
| 兼容性 | **不向后兼容** — 之前可传任意字段名，修复后只能传 Schema 定义的字段名 |
| 数据库 | 无变更 |

---

## 2. P0-02 硬编码凭据替换

### 风险分析

**位置：** `sysman-boot` — `AuthController.java`

**风险等级：** 严重（CVSS 8.6）

**根因：** 登录时使用内存硬编码字符串校验用户名密码：

```java
private static final String DEMO_USER = "admin";
private static final String DEMO_PASS = "admin123";
```

**风险明细：**
1. **无可审计性** — 所有登录操作没有记录到 Audit Log
2. **无多用户支持** — 所有用户共享同一个凭据
3. **源码泄露即密码泄露** — 代码仓库暴露密码
4. **无法密码策略** — 无法过期、重置、复杂度校验
5. **无角色差异化** — 所有登录者均为 admin，无法实现最小权限

---

### 设计方案

**策略：数据库用户表认证 + Spring Security PasswordEncoder + 配置分离**

#### 架构变更

```
当前：AuthController.login() ─┬─ 硬编码比较 DEMO_USER/DEMO_PASS
                               └─ 返回 UUID Token

修复后：AuthController.login() ─┬─ UserService.authenticate() → 查数据库
                                ├─ BCryptPasswordEncoder.matches()
                                └─ 返回 JWT Token（见 P0-03）
```

#### 1. 数据库用户表（复用现有或新增）

```sql
-- 若 IAM 模块已有用户表则直接复用，否则新建
CREATE TABLE IF NOT EXISTS sys_user (
    id          VARCHAR(64) PRIMARY KEY,
    username    VARCHAR(128) NOT NULL UNIQUE,
    password    VARCHAR(256) NOT NULL,   -- BCrypt 加密
    real_name   VARCHAR(128),
    email       VARCHAR(256),
    phone       VARCHAR(32),
    status      VARCHAR(16) DEFAULT 'active',
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

-- 角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id VARCHAR(64) NOT NULL REFERENCES sys_user(id),
    role_code VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id, role_code)
);
```

#### 2. UserService 认证逻辑（伪代码）

```java
@Service
public class UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder; // BCryptPasswordEncoder

    public User authenticate(String username, String password) {
        User user = userMapper.findByUsername(username);
        if (user == null || !"active".equals(user.getStatus())) {
            throw new AuthenticationException("用户不存在或已禁用");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            // 记录登录失败审计日志
            auditLogger.log(LoginEvent.failure(username, "密码错误"));
            throw new AuthenticationException("用户名或密码错误");
        }
        // 记录登录成功审计日志
        auditLogger.log(LoginEvent.success(username));
        return user;
    }
}
```

#### 3. AuthController 改造

```java
@PostMapping("/login")
public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
    String username = body.get("username");
    String password = body.get("password");
    
    try {
        User user = userService.authenticate(username, password);
        List<String> roles = userService.getUserRoles(user.getId());
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), roles);
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId());
        
        return ResponseEntity.ok(Map.of(
            "code", 200,
            "message", "登录成功",
            "data", Map.of(
                "accessToken", accessToken,
                "refreshToken", refreshToken,
                "username", user.getUsername(),
                "userId", user.getId(),
                "roles", roles
            )
        ));
    } catch (AuthenticationException e) {
        return ResponseEntity.status(401).body(Map.of(
            "code", 401,
            "message", e.getMessage()
        ));
    }
}
```

#### 4. 配置迁移

`application.yml` 中移除硬编码凭据，仅保留加密配置：

```yaml
auth:
  enabled: true
  # 密码策略
  password:
    min-length: 8
    require-digit: true
    require-lowercase: true
    require-uppercase: true
    require-special: true
```

**初始化种子数据**（仅在首次启动时）：

```sql
-- 密码为 "admin@123" 的 BCrypt 加密值
INSERT INTO sys_user (id, username, password, real_name, status)
VALUES ('u001', 'admin', '$2a$10$...BCryptHash...', '系统管理员', 'active')
ON CONFLICT (username) DO NOTHING;
```

---

### 变更文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `sysman-boot/.../controller/AuthController.java` | 修改 | 移除硬编码，注入 UserService + JwtTokenProvider |
| `sysman-impl/.../service/UserService.java` | **新增** | 用户认证服务 |
| `sysman-impl/.../mapper/UserMapper.java` | **新增** | MyBatis Mapper |
| `sysman-impl/.../entity/User.java` | **新增** | 用户实体 |
| `sysman-boot/src/main/resources/application.yml` | 修改 | 移除 DEMO 用户配置，增加密码策略配置 |
| `sysman-impl/src/main/resources/mapper/UserMapper.xml` | **新增** | MyBatis XML |

---

## 3. P0-03 JWT Token签发

### 风险分析

**位置：** `sysman-boot` — `AuthController.java`

**风险等级：** 严重（CVSS 8.2）

**根因：** 当前使用 `UUID.randomUUID().toString()` 作为 Token：

```java
// AuthController.java 第26-27行
String token = UUID.randomUUID().toString();
tokens.put(token, username);
```

**风险明细：**
1. **无签名校验** — Token 是随机 UUID，服务器必须维护内存 Map 去验证，无法在无状态场景下校验
2. **无过期机制** — Token 永不过期，泄露后无法撤销
3. **无身份载荷** — Token 本身不包含用户信息、角色、租户，每次都要查 Map
4. **内存 OOM 风险** — `ConcurrentHashMap` 无限增长，无过期清理
5. **无法水平扩展** — Token Map 在内存中，多实例间无法共享

---

### 设计方案

**策略：JJWT 0.12.5 签发 RS256 签名 JWT**

项目已引入 `jjwt-api 0.12.5` + `jjwt-impl` + `jjwt-jackson` 依赖，无需新增包。

#### Token 规范

| 参数 | Access Token | Refresh Token |
|------|-------------|---------------|
| 有效期 | 15 分钟 | 30 天 |
| 存储位置 | 客户端内存 | 客户端 HttpOnly Cookie 或 LocalStorage |
| 签名算法 | RS256 | RS256 |
| 吊销方式 | 短过期 + 黑名单 | 黑名单（Redis） |

#### JWT 载荷结构

```json
{
  "sub": "u001",
  "tenant": "t001",
  "roles": ["ADMIN"],
  "type": "access",
  "iat": 1718500000,
  "exp": 1718500900
}
```

| 字段 | 含义 | 来源 |
|------|------|------|
| `sub` | 用户 ID | `sys_user.id` |
| `tenant` | 租户 ID | 请求上下文或用户默认租户 |
| `roles` | 角色列表 | `sys_user_role` |
| `type` | Token 类型：`access` / `refresh` | 内部标记 |
| `iat` | 签发时间 | 系统时间 |
| `exp` | 过期时间 | iat + 有效期 |
| `jti` | Token 唯一标识（用于吊销） | UUID |

---

### Token 校验流程

```
客户端请求（Authorization: Bearer <token>）
         │
         ▼
JwtAuthenticationFilter (OncePerRequestFilter)
         │
         ├─ 1. 从请求头提取 Bearer Token
         ├─ 2. JwtTokenProvider.validateToken(token)
         │      ├─ 解析 JWT → Claims
         │      ├─ RS256 验签（公钥）
         │      ├─ 检查 exp 是否过期
         │      ├─ 检查 type == "access"
         │      └─ 检查是否在黑名单中
         │
         ├─ 3. 通过 → 设置 SecurityContext
         │      └─ 注入 UsernamePasswordAuthenticationToken
         │
         └─ 4. 失败 → 返回 401 Unauthorized
```

#### JwtTokenProvider 核心代码

```java
@Component
public class JwtTokenProvider {

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final long accessTokenExpiration = 15 * 60 * 1000L;      // 15分钟
    private final long refreshTokenExpiration = 30 * 24 * 60 * 60 * 1000L; // 30天

    public JwtTokenProvider(
            @Value("${jwt.private-key}") RSAPrivateKey privateKey,
            @Value("${jwt.public-key}") RSAPublicKey publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
    }

    public String createAccessToken(String userId, String tenantId, List<String> roles) {
        return Jwts.builder()
            .subject(userId)
            .claim("tenant", tenantId)
            .claim("roles", roles)
            .claim("type", "access")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
            .id(UUID.randomUUID().toString())
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();
    }

    public String createRefreshToken(String userId) {
        return Jwts.builder()
            .subject(userId)
            .claim("type", "refresh")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
            .id(UUID.randomUUID().toString())
            .signWith(privateKey, Jwts.SIG.RS256)
            .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
            .verifyWith(publicKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public boolean isAccessToken(String token) {
        return "access".equals(validateToken(token).get("type", String.class));
    }
}
```

#### JwtAuthenticationFilter

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        try {
            String token = authHeader.substring(7);
            Claims claims = jwtTokenProvider.validateToken(token);

            if (!"access".equals(claims.get("type"))) {
                throw new JwtException("Invalid token type");
            }

            // 检查黑名单（可选 Redis）
            if (tokenBlacklistService.isBlacklisted(claims.getId())) {
                throw new JwtException("Token revoked");
            }

            // 构建 Authentication
            List<String> roles = claims.get("roles", List.class);
            List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    claims.getSubject(), null, authorities);
            authentication.setDetails(claims);

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
        }

        chain.doFilter(request, response);
    }
}
```

#### 密钥管理

```
生产环境：RSA 2048 密钥对，Base64 编码后通过环境变量注入
配置文件不存储私钥，仅配置公钥位置（用于第三方验证）

application.yml:
jwt:
  public-key: ${JWT_PUBLIC_KEY}
  # 私钥仅通过环境变量 JWT_PRIVATE_KEY 注入，不写入配置文件
```

---

### 变更文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `sysman-impl/.../security/JwtTokenProvider.java` | **新增** | JWT 签发/验证核心类 |
| `sysman-impl/.../security/JwtAuthenticationFilter.java` | **新增** | Spring Security 过滤器 |
| `sysman-impl/.../security/SecurityConfig.java` | **新增** | Spring Security 配置类 |
| `sysman-boot/.../controller/AuthController.java` | 修改 | 集成 JWT 签发 |
| `sysman-boot/src/main/resources/application.yml` | 修改 | 增加 JWT 配置 |
| `sysman-boot/pom.xml` | 无需变更 | jjwt 依赖已在 sysman-impl 中 |

---

## 4. 依赖分析与执行计划

### 3 个任务的依赖关系

```
P0-01 SQL注入修复                          ← 无外部依赖，可独立执行
    │
P0-02 硬编码凭据替换 ──── 依赖 ──── P0-03 JWT Token签发
    │                                      ↑
    └── UserService 提供 authenticate() ────┘
    └── 需要 JwtTokenProvider 签发 Token
```

**说明：**
- **P0-01（SQL注入）** 与其他两项完全独立，可在任意时间执行
- **P0-02（硬编码替换）** 依赖于 **P0-03（JWT）** 的 `JwtTokenProvider`，因为登录成功后需要签发 JWT
- **P0-03（JWT）** 可以单独先行开发测试

### 建议执行顺序

| 步骤 | 任务 | 并行度 | 前置条件 |
|------|------|--------|----------|
| 1 | P0-03 JWT Token 签发 | 独立执行 | 无 |
| 2 | P0-01 SQL注入修复 | **与步1并行** | 无 |
| 3 | P0-02 硬编码凭据替换 | 串行 | 步骤1完成（JwtTokenProvider） |

```
时间线：
Week1:  ┌──── P0-03 JWT ────┐
        ┌──── P0-01 SQL ────┐
Week2:                       └──── P0-02 凭据 ────┘
```

### 预计工作量

| 任务 | 人天 | 说明 |
|------|------|------|
| P0-01 SQL注入修复 | **0.5 人天** | validateColumns + 单元测试 |
| P0-02 硬编码凭据替换 | **1.5 人天** | UserService + UserMapper + 数据库脚本 + 测试 |
| P0-03 JWT Token签发 | **1.5 人天** | JwtTokenProvider + Filter + SecurityConfig + 集成测试 |
| **合计** | **3.5 人天** | 若 P0-01 与 P0-03 并行，日历时间约 **2 天** |

---

## 附录：涉及工程模块概要

```
databridge-sysman
├── sysman-api          — API 接口定义           ← 无变更
├── sysman-impl         — 实现层                 ← P0-01、P0-03 主要变更
│   ├── controller/     — REST 控制器            ← ObjectController（SQL注入）
│   ├── service/        — 业务服务               ← UserService（新增）
│   ├── mapper/         — MyBatis Mapper        ← UserMapper（新增）
│   └── security/       — 安全组件               ← JwtTokenProvider（新增）
│                                                ← JwtAuthenticationFilter（新增）
│                                                ← SecurityConfig（新增）
└── sysman-boot         — 启动层                 ← P0-02、P0-03 配置变更
    ├── controller/     — 启动层控制器           ← AuthController（改造）
    └── resources/      — 配置文件               ← application.yml（修改）
```
