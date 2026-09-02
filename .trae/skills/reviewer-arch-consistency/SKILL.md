---
name: reviewer-arch-consistency
description: "Reviewer 架构一致性检查 Skill：验证代码实现是否符合 ARCH_SPEC/OpenAPI/DDL 的约束，包括模块边界、接口契约、数据模型一致性。接收 open-code-review 的输出，对 OCR 未覆盖的架构领域做补充检查。PM 向 Reviewer 分发任务或用户说'架构一致性'时触发。"
version: 3.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos, windows]
metadata:
  hermes:
    tags: [reviewer, arch-consistency, api-contract, schema-consistency, ocr]
    related_skills: [open-code-review, reviewer-code-review, reviewer-security-audit]
    artifact_type: ARCH_CONSISTENCY
    workflow_modes: [L3]
---

# Reviewer Arch Consistency Skill (OCR 增强版)

## 触发条件

- Reviewer Code Review Skill 调用（内部触发，作为 Step 5 执行）
- PM 向 Reviewer 分发任务时指定一致性检查
- 用户说"架构一致性"、"验证 API 契约"、"检查接口一致性"、"检查模块边界"

## 输入

- **必需**：ARCH_SPEC、OpenAPI 规范、DDL、源代码变更
- **可选**：OCR 预览结果（`ocr review --preview` 的文件打包信息）、OCR JSON 结果
- **固定约束**：技术栈约束、模块边界约束

## 输出制品

- **ARCH_CONSISTENCY**：架构一致性评估报告
  - 模块边界检查结果（OCR 预览 + 交叉验证）
  - 接口契约检查结果
  - 数据模型一致性检查结果
  - 违规清单

## 执行步骤

### Step 1: 读取 OCR 预览（文件打包分析）

> OCR 的 `--preview` 模式提供文件打包信息，同一 bundle 内的文件应属于同一业务模块。复用这个信息辅助模块边界检查。

```bash
# 获取 OCR 文件打包预览
cd {repo_dir}
ocr review \
  --from "{base_ref}" \
  --to "{head_ref}" \
  --preview \
  --format text
```

OCR 预览输出示例：
```
Files to review (bundled):
  [Bundle 1] UserController.java, UserService.java, UserRepository.java, UserResponse.java
  [Bundle 2] OrderController.java, OrderService.java, OrderRepository.java, OrderResponse.java
  [Bundle 3] ProductController.java, ProductService.java, ProductRepository.java, ProductResponse.java
```

### Step 2: 读取架构制品

```python
# 读取架构制品
arch_spec = read_file("docs/architecture/ARCH_SPEC_v1.md")
openapi = read_file("docs/architecture/openapi_v1.yaml")
ddl = read_file("docs/architecture/ddl_v1.sql")

# 提取关键约束
constraints = {
    "module_boundaries": {
        "user-service": ["UserController", "UserService", "UserRepository"],
        "order-service": ["OrderController", "OrderService", "OrderRepository"]
    },
    "api_contracts": {
        "/api/v1/users": ["GET", "POST"],
        "/api/v1/users/{id}": ["GET", "PUT", "DELETE"]
    },
    "data_models": {
        "User": ["id", "email", "name", "status", "createdAt", "updatedAt"]
    }
}
```

### Step 3: 模块边界检查（OCR 预览 + 交叉验证）

#### 3.1 基于 OCR Bundle 的模块验证

```python
def check_bundle_consistency(ocr_preview_output, arch_spec):
    """
    验证 OCR 的文件打包是否与架构模块边界一致
    """
    # 从 OCR 预览解析 bundle
    bundles = parse_ocr_bundles(ocr_preview_output)
    # 例如：Bundle 1 = [UserController, UserService, UserRepository, UserResponse]

    violations = []

    for bundle in bundles:
        # 检查同一 bundle 内是否有跨模块文件
        modules = infer_modules(bundle.files)
        if len(modules) > 1:
            violations.append({
                "type": "CROSS_MODULE_BUNDLE",
                "bundle": bundle.files,
                "modules": list(modules),
                "message": f"Bundle 包含多个模块的文件：{modules}"
            })

        # 检查 Bundle 是否与 arch_spec 中的 module 定义匹配
        expected_module = find_expected_module(bundle.files)
        if expected_module and not matches_module(bundle, expected_module):
            violations.append({
                "type": "BUNDLE_SCOPE_MISMATCH",
                "bundle": bundle.files,
                "expected": expected_module,
                "message": f"Bundle 范围与架构设计不符"
            })

    return violations
```

#### 3.2 跨模块直接依赖检查

即使 OCR Bundle 验证通过，仍需检查跨模块直接调用：

```python
def check_cross_module_imports(changed_files, source_code):
    """
    检查是否存在跨模块直接依赖
    OCR 主要检测代码质量问题，此处专门检查架构层面的模块依赖
    """
    violations = []

    # 从 ARCH_SPEC 提取模块边界约束
    module_constraints = get_module_constraints()

    for file in changed_files:
        imports = extract_imports(file)
        file_module = get_module(file)

        for imp in imports:
            target_module = get_module(imp)
            if target_module and target_module != file_module:
                # 检查是否通过允许的接口调用（如 Facade）
                if not is_allowed_cross_module_call(file, imp, module_constraints):
                    violations.append({
                        "file": file,
                        "type": "CROSS_MODULE_DIRECT_CALL",
                        "import": imp,
                        "target_module": target_module,
                        "message": f"{file} 直接依赖 {target_module} 的 {imp}，违反模块边界"
                    })

    return violations
```

**跨模块违规示例**：

```java
// ❌ 违规：OrderService 直接引用 UserRepository
@Service
public class OrderServiceImpl {
    @Autowired
    private UserRepository userRepository; // 跨模块直接依赖

    public void createOrder(String userId) {
        User user = userRepository.findById(userId); // 直接操作 User 数据
    }
}

// ✅ 正确：通过 UserService Facade 调用
@Service
public class OrderServiceImpl {
    @Autowired
    private UserService userService; // 通过接口调用

    public void createOrder(String userId) {
        User user = userService.getUser(userId); // 封装调用
    }
}
```

#### 3.3 DTO 直接暴露 Entity 检查

```java
// ❌ 违规：直接返回 Entity
@GetMapping("/users/{id}")
public User getUser(@PathVariable String id) {
    return userRepository.findById(id); // 返回 Entity，暴露内部结构
}

// ✅ 正确：返回 DTO
@GetMapping("/users/{id}")
public UserResponse getUser(@PathVariable String id) {
    User user = userRepository.findById(id);
    return UserResponse.fromEntity(user);
}
```

### 模块边界检查结果

```markdown
## 模块边界检查结果

| 模块 | Bundle 内文件数 | 违规数 | 状态 |
|------|----------------|--------|------|
| user-service | 4 | 0 | ✅ 通过 |
| order-service | 5 | 1 | ❌ 违规 |
| product-service | 3 | 0 | ✅ 通过 |

### 违规详情

| 违规 ID | 模块 | 文件 | 违规类型 | 说明 |
|---------|------|------|---------|------|
| MB-001 | order-service | OrderServiceImpl.java | 跨模块直接调用 | 直接引用 UserRepository |
| MB-002 | order-service | OrderController.java | DTO 缺失 | 返回 Entity 而非 DTO |
```

### Step 4: API 契约检查

```python
def check_api_contract(openapi_spec, source_code, ocr_findings):
    """
    检查代码实现的 API 是否与 OpenAPI 规范一致
    OCR 可能标记 PARAM_MISMATCH / RESPONSE_MISMATCH，此处做系统性验证
    """
    violations = []

    # 从 OpenAPI 提取期望的接口
    expected_apis = parse_openapi(openapi_spec)

    for api in expected_apis:
        # 1. 检查接口是否存在
        if not api_exists(source_code, api):
            violations.append({
                "type": "API_NOT_IMPLEMENTED",
                "api": f"{api.method} {api.path}",
                "message": "OpenAPI 定义了但代码未实现"
            })

        # 2. 检查请求参数
        implemented = get_implemented_api(source_code, api)
        if implemented:
            param_diff = set(api.params) - set(implemented.params)
            if param_diff:
                violations.append({
                    "type": "PARAM_MISMATCH",
                    "api": f"{api.method} {api.path}",
                    "missing_params": list(param_diff),
                    "message": "参数不一致"
                })

            # 3. 检查响应格式
            if not response_matches(api.response, implemented.response):
                violations.append({
                    "type": "RESPONSE_MISMATCH",
                    "api": f"{api.method} {api.path}",
                    "message": "响应格式与 OpenAPI 不一致"
                })

    return violations
```

### API 契约检查结果

```markdown
## API 契约检查结果

| 检查项 | 总数 | 通过 | 失败 | 通过率 |
|--------|------|------|------|--------|
| 接口存在性 | 24 | 24 | 0 | 100% |
| 请求参数 | 24 | 22 | 2 | 91.7% |
| 响应格式 | 24 | 23 | 1 | 95.8% |
| HTTP 状态码 | 24 | 24 | 0 | 100% |

### 违规详情

| 违规 ID | API | 类型 | 说明 |
|---------|-----|------|------|
| API-001 | GET /api/v1/users/{id} | 参数缺失 | 缺少 pageSize 参数（分页接口） |
| API-002 | POST /api/v1/orders | 响应格式 | 返回字段与 OpenAPI 不一致 |
```

### Step 5: 数据模型一致性检查

```python
def check_data_model(ddl, source_code):
    """
    检查代码中的数据模型是否与 DDL 一致
    这是 OCR 未覆盖的领域，必须显式检查
    """
    violations = []

    # 从 DDL 提取表结构
    tables = parse_ddl(ddl)

    # 从代码提取 Entity 定义
    entities = extract_entities(source_code)

    for table_name, table_schema in tables.items():
        entity = find_entity(entities, table_name)

        if not entity:
            violations.append({
                "type": "ENTITY_NOT_FOUND",
                "table": table_name,
                "message": "DDL 定义了表但代码中没有 Entity"
            })
            continue

        # 检查字段一致性
        ddl_fields = set(table_schema.fields)
        entity_fields = set(entity.fields)

        missing = ddl_fields - entity_fields
        extra = entity_fields - ddl_fields

        if missing:
            violations.append({
                "type": "FIELD_MISSING",
                "table": table_name,
                "missing_fields": list(missing),
                "message": "Entity 缺少 DDL 中定义的字段"
            })

        if extra:
            violations.append({
                "type": "FIELD_EXTRA",
                "table": table_name,
                "extra_fields": list(extra),
                "message": "Entity 有 DDL 中没有的字段"
            })

    return violations
```

### 数据模型检查结果

```markdown
## 数据模型一致性检查结果

| 检查项 | 总数 | 通过 | 失败 |
|--------|------|------|------|
| 表-Entity 映射 | 8 | 8 | 0 |
| 字段一致性 | 8 | 7 | 1 |
| 字段类型 | 8 | 8 | 0 |
| 约束一致性 | 8 | 6 | 2 |

### 违规详情

| 违规 ID | 表 | 类型 | 说明 |
|---------|---|------|------|
| DM-001 | ord_orders | 字段类型 | order_no VARCHAR(32)，Entity 中未限制长度 |
| DM-002 | ord_orders | 约束缺失 | 缺少 UNIQUE 约束 on order_no |
| DM-003 | ord_order_items | 字段缺失 | 缺少 subtotal 字段 |
```

### Step 6: 生成 ARCH_CONSISTENCY 报告

```markdown
# 架构一致性评估报告（OCR 增强版）

**任务 ID**: {task_id}
**执行时间**: {timestamp}
**审查范围**：{n} 个文件
**OCR 预览**：已获取文件打包信息

---

## 汇总

| 检查类型 | 检查项 | 通过 | 失败 | 通过率 |
|---------|--------|------|------|--------|
| 模块边界 | 3 个模块 | 2 | 2 | 66.7% |
| API 契约 | 24 个 API | 22 | 2 | 91.7% |
| 数据模型 | 8 个表 | 6 | 2 | 75% |

## 违规汇总

| 严重程度 | 数量 | 说明 |
|---------|------|------|
| 阻断（P0） | 0 | 无 |
| 严重（P1） | 2 | 模块边界违规（MB-001）、响应格式不一致（API-002） |
| 警告（P2） | 4 | 字段缺失、约束缺失 |

## 风险评估

**模块边界违规（MB-001）**：OrderService 直接依赖 UserRepository，违反微服务解耦原则。
- 影响：user-service 修改可能影响 order-service
- 建议：通过 UserService Facade 调用

**API 响应格式不一致（API-002）**：POST /api/v1/orders 返回格式与 OpenAPI 不一致。
- 影响：前端无法正确解析订单数据
- 建议：统一使用 OrderResponse DTO

## 结论

**一致性评估**：⚠️ 基本一致，有 P1 违规需修复

- 模块边界：有 2 个违规（MB-001 跨模块调用、MB-002 DTO 缺失），需 Fullstack 修复
- API 契约：有 2 个违规，需修复 API-001、API-002
- 数据模型：有 2 个违规，需修复 DM-001、DM-002、DM-003

**建议**：修复 P1 违规后重新审查
```

## PM 回复模板

### 架构一致性检查完成

```
✅ 架构一致性检查完成：{task_id}

检查结果：
  模块边界：2 个违规（MB-001、MB-002）
  API 契约：2 个违规（API-001、API-002）
  数据模型：2 个违规（DM-001、DM-002、DM-003）

风险评估：⚠️ 存在 P1 违规，需修复

评估报告：docs/03开发阶段/{NN}-审查报告/{task_id}_arch_consistency.md
```

## 验证步骤

1. [ ] OCR preview 已获取文件打包信息
2. [ ] 所有变更文件已检查模块边界（Bundle 验证 + 跨模块依赖）
3. [ ] 所有 API 与 OpenAPI 规范一一对应
4. [ ] 所有数据模型与 DDL 一致
5. [ ] 违规有明确的文件位置和修复建议
6. [ ] 报告格式正确，包含汇总和详情

## 常见陷阱

1. **只依赖 OCR preview**：OCR bundle 可能跨模块，需人工核对 arch_spec
2. **API 路径写错**：没发现 `/users/{id}` 和 `/users/{userId}` 路径不一致
3. **字段类型忽略**：DDL 是 INT，Entity 是 Long，不认为是问题
4. **模块边界模糊**：不清楚"跨模块调用"和"正常调用"的界限
5. **不验证数据库迁移**：代码能跑但 DDL 迁移脚本没更新

## ARCH_CONSISTENCY_APPROVAL_RECORD 格式

本 skill 输出的 `ARCH_CONSISTENCY` 报告，会汇总到 `reviewer-code-review` skill 的 `REVIEW_REPORT_APPROVAL_RECORD`。

```json
{
  "artifact": "ARCH_CONSISTENCY",
  "name": "{项目名称} 架构一致性评估",
  "version": "v{version}",
  "status": "{PASS / FAIL}",
  "workflow_mode": "L3",
  "checks": {
    "module_boundary": {"total": 3, "violations": 2, "p0": 0, "p1": 2},
    "api_contract": {"total": 24, "violations": 2, "p0": 0, "p1": 1},
    "data_model": {"total": 8, "violations": 2, "p0": 0, "p1": 0}
  },
  "arch_violations_summary": {
    "p0_arch_violations": 0,
    "p1_arch_violations": 3
  },
  "deliverable_allowed": true,
  "source_patch_ref": "SOURCE_PATCH@{hash}"
}
```

ARCH_GATE 判定：P0/P1 架构违规数 > 0 → FAIL，deliverable_allowed=false。