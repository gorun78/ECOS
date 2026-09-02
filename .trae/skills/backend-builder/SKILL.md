---
name: backend-builder
description: "后端开发 Skill：基于 OpenAPI 规范实现 Spring Boot/FastAPI 接口，包含业务逻辑、事务管理、数据访问。必须追踪每个 API 到 OpenAPI operationId，单元测试通过后才可报告 TASK_DONE。当用户说'做后端'、'写 API'、'后端开发'时触发。"
version: 2.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [backend, spring-boot, fastapi, api, business-logic, database, traceability, unit-test-gate]
    related_skills: [fullstack-impl, frontend-builder]
    artifact_type: SOURCE_PATCH
    workflow_modes: [L1, L2, L3]
---

# Backend Builder Skill (v2 — OpenAPI 追溯 + 单元测试强制门禁版)

## 核心原则

每个 Controller 方法必须有 `@Operation(operationId="xxx")` 与 OpenAPI operationId 一一对应。代码完成后必须有单元测试通过，单元测试是强制门禁（无测试不得报告 TASK_DONE）。

## 关键机制

### OpenAPI operationId 追溯

每个实现的 API 必须能追溯到 OpenAPI 定义：

```java
// UserController.java
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserService userService;

    // @Operation(operationId) 必须与 OpenAPI 规范中的 operationId 完全一致
    @PostMapping
    @Operation(operationId = "createUser")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        // 实现追踪：createUser → OpenAPI POST /users → user-service → F1-用户注册
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{userId}")
    @Operation(operationId = "getUser")
    public ResponseEntity<UserResponse> getUser(@PathVariable String userId) {
        // 实现追踪：getUser → OpenAPI GET /users/{userId} → user-service
        UserResponse response = userService.getUser(userId);
        return ResponseEntity.ok(response);
    }
}
```

追溯表：

```markdown
## API → OpenAPI 追溯表

| Controller 方法 | operationId | HTTP Method | OpenAPI Path | 对应模块 | PRD 功能 |
|----------------|-------------|-------------|-------------|---------|---------|
| createUser | createUser | POST | /users | user-service | F1-用户注册 |
| getUser | getUser | GET | /users/{userId} | user-service | F1-用户注册, F3-个人中心 |
| listUsers | listUsers | GET | /users | user-service | F1-用户注册, F2-登录 |
```

### 单元测试强制门禁

| 门禁 | 条件 | 未通过处理 |
|------|------|-----------|
| **单元测试门禁** | 核心 Service 方法必须有单元测试 | 无测试代码 → 阻断 TASK_DONE |

**硬门槛**：核心业务逻辑（Service 层）必须有单元测试，否则禁止报告 TASK_DONE。

## 触发条件

- Fullstack Impl Skill 调用（内部触发）
- 用户说"做后端"、"写 API"、"后端开发"
- 需要基于 OpenAPI 规范实现接口时

## 输入

- **必需**：OpenAPI 规范（YAML/JSON，artifact_ref）
- **必需**：OpenAPI 已 APPROVED（deliverable_allowed=true）
- **可选**：DDL 建议、现有代码、架构设计
- **固定约束**：技术栈（Spring Boot 或 FastAPI）、编码规范

## 输出制品

- 后端代码文件（Java / Python）
- Controller / Handler（含 operationId 追溯）
- Service（含单元测试）
- Repository / Mapper
- Entity / Model
- DTO / Request/Response
- 配置类
- **UNIT_TEST**：单元测试代码

## 执行步骤

### Step 0: 前置校验 — OpenAPI 批准记录检查

```python
def validate_openapi_for_backend(openapi_ref):
    """后端开发前，必须校验 OpenAPI 已 APPROVED"""
    approval_record = read_artifact_approval_record(openapi_ref)
    if not approval_record:
        raise ValueError(f"OpenAPI {openapi_ref} 无批准记录，后端开发禁止开始")
    if approval_record["status"] != "APPROVED":
        raise ValueError(f"OpenAPI 状态为 {approval_record['status']}，必须 APPROVED 才能开始开发")
    if not approval_record.get("deliverable_allowed"):
        raise ValueError("OpenAPI deliverable_allowed=false，禁止开始开发")
    return {
        "openapi_version": approval_record["version"],
        "openapi_hash": approval_record["hash"],
        "api_counts": approval_record["api_counts"],
        "modules": extract_modules(openapi_ref)
    }
```

```markdown
## OpenAPI 校验

收到后端开发请求，校验以下前提条件：

1. [ ] OpenAPI 状态为 APPROVED ✅
2. [ ] OpenAPI 有批准记录 ✅
3. [ ] OpenAPI 的 deliverable_allowed = true ✅

当前 OpenAPI：
- 版本：{version}
- Hash：{hash}
- 状态：APPROVED
- API 数量：{n} 个

→ OpenAPI 校验通过，可开始后端开发
```

---

### Step 1: 解析 OpenAPI 生成代码骨架

从 OpenAPI 规范解析出接口定义，生成代码骨架：

```python
# OpenAPI 解析结果 → 代码结构
apis = [
    {
        "service": "user-service",
        "endpoints": [
            {
                "method": "POST",
                "path": "/users",
                "operationId": "createUser",   # ← 必须保留，用于追溯
                "requestBody": "CreateUserRequest",
                "response": "User"
            },
            {
                "method": "GET",
                "path": "/users/{userId}",
                "operationId": "getUser",
                "pathParams": [{"name": "userId", "type": "string"}],
                "response": "User"
            }
        ]
    }
]

# 生成代码结构
project = {
    "java": {
        "basePackage": "com.example.app",
        "structure": [
            "src/main/java/com/example/app/controller/UserController.java",
            "src/main/java/com/example/app/service/UserService.java",
            "src/main/java/com/example/app/service/impl/UserServiceImpl.java",
            "src/main/java/com/example/app/repository/UserRepository.java",
            "src/main/java/com/example/app/entity/User.java",
            "src/main/java/com/example/app/dto/CreateUserRequest.java",
            "src/main/java/com/example/app/dto/UserResponse.java",
        ]
    }
}
```

---

### Step 2: 实现 Entity（数据模型）

```java
// User.java
@Entity
@Table(name = "usr_users")
public class User {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "status", nullable = false)
    private Integer status; // 1: active, 2: inactive

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // Getters and Setters
}
```

---

### Step 3: 实现 Repository（数据访问）

```java
// UserRepository.java
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    Optional<User> findByIdAndDeletedAtIsNull(String id);

    Page<User> findByDeletedAtIsNull(Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL " +
           "AND (:keyword IS NULL OR u.name LIKE %:keyword% OR u.email LIKE %:keyword%)")
    Page<User> searchUsers(@Param("keyword") String keyword, Pageable pageable);

    boolean existsByEmailAndIdNot(String email, String id);
}
```

---

### Step 4: 实现 DTO（数据传输对象）

```java
// CreateUserRequest.java
public class CreateUserRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "姓名不能为空")
    @Size(min = 1, max = 100, message = "姓名长度 1-100")
    private String name;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, message = "密码至少 8 位")
    private String password;
}

// UserResponse.java
public class UserResponse {

    private String id;
    private String email;
    private String name;
    private String status;
    private String createdAt;
    private String updatedAt;

    // 静态工厂方法
    public static UserResponse fromEntity(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setName(user.getName());
        response.setStatus(user.getStatus() == 1 ? "active" : "inactive");
        response.setCreatedAt(user.getCreatedAt().toString());
        response.setUpdatedAt(user.getUpdatedAt().toString());
        return response;
    }
}
```

---

### Step 5: 实现 Service（业务逻辑）

```java
// UserService.java
public interface UserService {

    UserResponse createUser(CreateUserRequest request);

    UserResponse getUser(String id);

    Page<UserResponse> listUsers(int page, int pageSize, String keyword);

    UserResponse updateUser(String id, UpdateUserRequest request);

    void deleteUser(String id);
}

// UserServiceImpl.java
@Service
@Transactional
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BusinessException("USER_EMAIL_EXISTS", "邮箱已被注册");
        }

        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
        return UserResponse.fromEntity(user);
    }

    @Override
    public UserResponse getUser(String id) {
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "用户不存在"));
        return UserResponse.fromEntity(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(int page, int pageSize, String keyword) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<User> userPage = StringUtils.hasText(keyword)
                ? userRepository.searchUsers(keyword, pageable)
                : userRepository.findByDeletedAtIsNull(pageable);
        return userPage.map(UserResponse::fromEntity);
    }

    @Override
    public UserResponse updateUser(String id, UpdateUserRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "用户不存在"));

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.existsByEmailAndIdNot(request.getEmail(), id)) {
                throw new BusinessException("USER_EMAIL_EXISTS", "邮箱已被使用");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getName() != null) {
            user.setName(request.getName());
        }
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);
        return UserResponse.fromEntity(user);
    }

    @Override
    public void deleteUser(String id) {
        User user = userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "用户不存在"));
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);
    }
}
```

---

### Step 6: 实现 Controller（带 operationId 追溯）

```java
// UserController.java
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping
    @Operation(operationId = "createUser")
    // 追溯：createUser → POST /users → user-service → F1-用户注册
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{userId}")
    @Operation(operationId = "getUser")
    // 追溯：getUser → GET /users/{userId} → user-service → F1/F3
    public ResponseEntity<UserResponse> getUser(@PathVariable String userId) {
        UserResponse response = userService.getUser(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(operationId = "listUsers")
    // 追溯：listUsers → GET /users → user-service → F1/F2
    public ResponseEntity<Page<UserResponse>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        Page<UserResponse> response = userService.listUsers(page, pageSize, keyword);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{userId}")
    @Operation(operationId = "updateUser")
    // 追溯：updateUser → PUT /users/{userId} → user-service → F3
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserRequest request) {
        UserResponse response = userService.updateUser(userId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{userId}")
    @Operation(operationId = "deleteUser")
    // 追溯：deleteUser → DELETE /users/{userId} → user-service → F1
    public ResponseEntity<Void> deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
```

---

### Step 7: 全局异常处理

```java
// GlobalExceptionHandler.java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        ErrorResponse error = new ErrorResponse();
        error.setCode(ex.getCode());
        error.setMessage(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        ErrorResponse error = new ErrorResponse();
        error.setCode(ex.getCode());
        error.setMessage(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        ErrorResponse error = new ErrorResponse();
        error.setCode("VALIDATION_ERROR");
        error.setMessage("参数校验失败");
        error.setDetails(ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(fe.getField(), fe.getDefaultMessage()))
                .toList());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
}
```

---

### Step 8: 编写单元测试（强制门禁）

**硬门槛**：每个 Service 方法必须有单元测试，无测试代码禁止报告 TASK_DONE。

```java
// UserServiceTest.java
@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void should_create_user_successfully() {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("test@example.com");
        request.setName("Test User");
        request.setPassword("password123");

        UserResponse response = userService.createUser(request);

        assertNotNull(response.getId());
        assertEquals("test@example.com", response.getEmail());
        assertEquals("Test User", response.getName());
        assertEquals("active", response.getStatus());
    }

    @Test
    void should_throw_exception_when_email_exists() {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("existing@example.com");
        request.setName("Existing User");
        request.setPassword("password123");

        userService.createUser(request);

        CreateUserRequest duplicate = new CreateUserRequest();
        duplicate.setEmail("existing@example.com");
        duplicate.setName("Another User");
        duplicate.setPassword("password456");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.createUser(duplicate));
        assertEquals("USER_EMAIL_EXISTS", ex.getCode());
    }

    @Test
    void should_get_user_by_id() {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("get@example.com");
        request.setName("Get User");
        request.setPassword("password123");
        UserResponse created = userService.createUser(request);

        UserResponse found = userService.getUser(created.getId());

        assertEquals(created.getId(), found.getId());
        assertEquals("get@example.com", found.getEmail());
    }

    @Test
    void should_throw_not_found_when_user_not_exists() {
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> userService.getUser("non-existent-id"));
        assertEquals("USER_NOT_FOUND", ex.getCode());
    }

    @Test
    void should_list_users_with_pagination() {
        for (int i = 0; i < 5; i++) {
            CreateUserRequest request = new CreateUserRequest();
            request.setEmail("page" + i + "@example.com");
            request.setName("Page User " + i);
            request.setPassword("password123");
            userService.createUser(request);
        }

        Page<UserResponse> page = userService.listUsers(1, 3, null);

        assertEquals(3, page.getContent().size());
        assertEquals(5, page.getTotalElements());
    }

    @Test
    void should_delete_user_softly() {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("delete@example.com");
        request.setName("Delete User");
        request.setPassword("password123");
        UserResponse created = userService.createUser(request);

        userService.deleteUser(created.getId());

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> userService.getUser(created.getId()));
        assertEquals("USER_NOT_FOUND", ex.getCode());
    }
}
```

---

### Step 9: 单元测试门禁判定

```python
def validate_unit_test_gate(service_class, test_class):
    """
    单元测试强制门禁：核心 Service 方法必须有单元测试
    """
    service_methods = extract_public_methods(service_class)
    test_methods = extract_test_methods(test_class)

    untested = [m for m in service_methods if m not in test_methods]

    if untested:
        raise UnitTestGateException(
            f"单元测试门禁未通过：以下方法缺少测试 → {untested}\n"
            f"禁止报告 TASK_DONE，请先补充单元测试"
        )

    return {
        "gate": "UNIT_TEST_GATE",
        "status": "PASSED",
        "service_methods_tested": len(service_methods),
        "test_methods_count": len(test_methods)
    }
```

---

### Step 10: 生成 API 追溯表

```markdown
## API → OpenAPI 追溯表

| Controller 方法 | operationId | HTTP Method | OpenAPI Path | 对应模块 | PRD 功能 |
|----------------|-------------|-------------|-------------|---------|---------|
| createUser | createUser | POST | /users | user-service | F1-用户注册 |
| getUser | getUser | GET | /users/{userId} | user-service | F1-用户注册, F3-个人中心 |
| listUsers | listUsers | GET | /users | user-service | F1-用户注册, F2-登录 |
| updateUser | updateUser | PUT | /users/{userId} | user-service | F3-个人中心 |
| deleteUser | deleteUser | DELETE | /users/{userId} | user-service | F1-用户注册 |
```

---

## 代码规范

### 包结构（Spring Boot）

```
com.example.app
├── controller/
│   └── UserController.java  ← operationId 追溯
├── service/
│   ├── UserService.java (interface)
│   └── impl/
│       └── UserServiceImpl.java  ← 单元测试必须覆盖
├── repository/
│   └── UserRepository.java
├── entity/
│   └── User.java
├── dto/
│   ├── CreateUserRequest.java
│   ├── UpdateUserRequest.java
│   └── UserResponse.java
├── exception/
│   ├── NotFoundException.java
│   └── BusinessException.java
└── config/
    └── SecurityConfig.java
```

### 安全规范

```java
// 密码加密：必须使用 BCrypt
@Autowired
private PasswordEncoder passwordEncoder;

String hashed = passwordEncoder.encode(rawPassword);

// SQL 注入防护：使用参数化查询（JPA 自动处理）
userRepository.findByEmail(email);

// XSS 防护：@RequestBody 自动反序列化，Spring Boot 默认有防护
```

---

## 验证步骤

1. [ ] OpenAPI 校验通过（APPROVED 状态）
2. [ ] 每个 Controller 方法有 @Operation(operationId) 与 OpenAPI 一致
3. [ ] API 追溯表完整（所有 API 都能追溯到 OpenAPI）
4. [ ] 核心 Service 方法都有单元测试（单元测试门禁通过）
5. [ ] 参数校验正确（@Valid 注解）
6. [ ] 事务管理正确（@Transactional）
7. [ ] 异常处理统一（全局异常处理器）
8. [ ] 密码加密（BCrypt）
9. [ ] 软删除实现正确
10. [ ] 所有单元测试通过

## 常见陷阱

1. **跳过 OpenAPI 校验**：直接使用未批准版本
2. **operationId 不匹配**：Controller 方法名与 OpenAPI operationId 不一致
3. **事务遗漏**：增删改操作没有 @Transactional
4. **硬编码 SQL**：拼接 SQL 而不用 JPA 方法
5. **密码明文**：密码直接存储不加密
6. **无单元测试报告 TASK_DONE**：核心 Service 方法没有测试代码就交付
7. **异常不处理**：catch 后不抛异常也不记录
8. **DTO 与 Entity 混用**：把 Entity 直接返回给前端（暴露内部结构）