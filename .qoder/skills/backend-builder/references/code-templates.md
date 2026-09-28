# backend-builder — 拆分出的模板/示例

> 由 SKILL.md 拆出，SKILL.md 对应位置留有指针。生成相关制品前先读本文件。

## 块 1（原 SKILL.md L297-388）

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

## 块 2（原 SKILL.md L394-448）

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

## 块 3（原 SKILL.md L494-596）

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
