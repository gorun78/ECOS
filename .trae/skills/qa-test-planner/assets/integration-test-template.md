## 集成测试用例模板

### IT-XXX: {HTTP_METHOD} {PATH} {接口名称}

**PRD 追溯**：PRD-{功能ID}（{功能名称}）
**优先级**：P0 / P1 / P2
**对应 API**：{HTTP_METHOD} {PATH}（{operationId}）
**模块**：{module-name}

**目的**：{测试目的描述}

**测试步骤**：

| 步骤 | 操作 | 预期结果 | PRD 映射 |
|------|------|---------|---------|
| 1 | {HTTP_METHOD} {PATH} body={valid_request} | HTTP {status}，body 包含 {response_field} | {功能点} |
| 2 | {HTTP_METHOD} {PATH} body={invalid_data} | HTTP 400，body 包含 {error_code} | {参数校验} |
| 3 | {HTTP_METHOD} {PATH}（无 body） | HTTP 400 | {必填校验} |

**HTTP 状态码规范**：

| 场景 | 状态码 |
|------|--------|
| 成功 | 200 / 201 / 204 |
| 参数校验失败 | 400 |
| 未授权 | 401 |
| 权限不足 | 403 |
| 资源不存在 | 404 |
| 业务冲突 | 409 |
| 服务端错误 | 500 |

**伪代码示例**：

```python
import requests
import pytest

BASE_URL = "https://api.example.com"
API_ENDPOINT = "{PATH}"

class Test{APIEndpoint}:

    def test_{operationId}_success(self):
        """PRD-{功能ID}: {功能描述} - 正向流程"""
        response = requests.{method}(
            f"{BASE_URL}{API_ENDPOINT}",
            json={valid_request},
            headers={"Authorization": "Bearer {token}"}
        )
        assert response.status_code == {expected_status}
        assert response.json()["{field}"] == {expected_value}

    def test_{operationId}_validation_error(self):
        """PRD-{功能ID}: {功能描述} - 参数校验"""
        response = requests.{method}(
            f"{BASE_URL}{API_ENDPOINT}",
            json={invalid_request},
            headers={"Authorization": "Bearer {token}"}
        )
        assert response.status_code == 400
        assert "VALIDATION_ERROR" in response.json()["code"]
```

### 集成测试注意事项

1. 每个测试用例前应清理相关数据
2. 使用独立的测试数据库或事务回滚
3. API 请求应包含完整的请求头（Content-Type、Authorization）
4. 响应断言应验证关键字段，而非全量匹配