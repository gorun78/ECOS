## 单元测试用例模板

### UT-XXX: {类名}.{方法名}

**PRD 追溯**：PRD-{功能ID}（{功能名称}）
**优先级**：P0 / P1 / P2
**模块**：{module-name}
**对应 API**：{HTTP_METHOD} {PATH}（{operationId}）

**目的**：{测试目的描述}

**前置条件**：
- {前置条件1}
- {前置条件2}

**测试数据**：
```python
valid_request = {
    "field1": "valid_value",
    "field2": 123
}
invalid_request = {
    "field1": "",  # 为空
    "field2": -1   # 负数
}
```

**测试步骤**：

| 步骤 | 操作 | 预期结果 | 对应 PRD |
|------|------|---------|---------|
| 1 | 调用 {method}(valid_request) | 返回 {expected_response} | {功能点} |
| 2 | 验证 {业务逻辑} | {验证条件} | {功能点} |
| 3 | 调用 {method}(invalid_request) | 抛出 {异常类型} | {参数校验} |

**边界条件**：
- {边界条件1} → 对应 {PRD功能点}
- {边界条件2} → 对应 {PRD功能点}

**伪代码示例**：

```python
import pytest
from {module}.{service} import {ServiceClass}

class Test{ServiceClass}:

    def test_{method}_success(self):
        """PRD-{功能ID}: {功能描述} - 正向流程"""
        service = {ServiceClass}()
        result = service.{method}(valid_request)
        assert result.{field} == expected_value

    def test_{method}_validation_error(self):
        """PRD-{功能ID}: {功能描述} - 参数校验"""
        with pytest.raises({ExceptionType}):
            service.{method}(invalid_request)
```

### 单元测试命名规范

| 场景 | 命名格式 | 示例 |
|------|----------|------|
| 正向流程 | `test_{method}_success` | `test_createUser_success` |
| 参数校验 | `test_{method}_validation_error` | `test_createUser_invalid_email` |
| 边界条件 | `test_{method}_boundary_{condition}` | `test_createUser_password_too_short` |
| 异常处理 | `test_{method}_exception` | `test_createUser_duplicate_email` |