# fullstack-impl — 拆分出的模板/示例

> 由 SKILL.md 拆出，SKILL.md 对应位置留有指针。生成相关制品前先读本文件。

## 块 1（原 SKILL.md L174-216）

```python
# OpenAPI 解析结果
api_groups = {
    "user-service": [
        {"method": "POST", "path": "/users", "operationId": "createUser"},
        {"method": "GET", "path": "/users/{id}", "operationId": "getUser"},
        {"method": "PUT", "path": "/users/{id}", "operationId": "updateUser"},
        {"method": "DELETE", "path": "/users/{id}", "operationId": "deleteUser"},
    ],
    "order-service": [
        {"method": "POST", "path": "/orders", "operationId": "createOrder"},
        {"method": "GET", "path": "/orders/{id}", "operationId": "getOrder"},
    ]
}

# 任务拆分
tasks = []
for service, apis in api_groups.items():
    tasks.append({
        "type": "backend",
        "service": service,
        "apis": apis
    })
    tasks.append({
        "type": "frontend",
        "service": service,
        "page": f"{service}-list-page"
    })

# PRD → OpenAPI → SOURCE_PATCH 追溯
traceability = {
    "user-service": {
        "prd_functions": ["F1-用户注册", "F2-登录", "F3-个人中心"],
        "apis": ["createUser", "getUser", "updateUser", "deleteUser"],
        "pages": ["user-list-page", "user-detail-page"]
    },
    "order-service": {
        "prd_functions": ["F4-下单", "F5-支付", "F6-取消退款"],
        "apis": ["createOrder", "getOrder"],
        "pages": ["order-list-page", "order-detail-page"]
    }
}
```

## 块 2（原 SKILL.md L334-378）

```json
{
  "artifact": "SOURCE_PATCH",
  "name": "{项目名称} 源代码",
  "version": "v{version}",
  "hash": "{内容哈希}",
  "status": "APPROVED",
  "workflow_mode": "L3",
  "approvals": [
    {
      "role": "build-verify",
      "result": "APPROVED",
      "timestamp": "{timestamp}",
      "conditions": []
    }
  ],
  "gates_passed": ["FIRST_GATE", "SECOND_GATE", "THIRD_GATE"],
  "deliverable_allowed": true,
  "prd_ref": "PRD@{prd_hash}",
  "arch_ref": "ARCH_SPEC@{arch_hash}",
  "openapi_ref": "OPENAPI@{openapi_hash}",
  "ddl_ref": "DDL@{ddl_hash}",
  "artifacts": {
    "backend": "SOURCE_PATCH@{backend_hash}#backend",
    "frontend": "SOURCE_PATCH@{frontend_hash}#frontend",
    "unit_tests": "UNIT_TEST@{test_hash}"
  },
  "traceability": {
    "user-service": {
      "prd_functions": ["F1", "F2", "F3"],
      "apis": ["createUser", "getUser", "updateUser", "deleteUser"],
      "pages": ["user-list-page", "user-detail-page"]
    }
  },
  "build_approval": {
    "build_hash": "{build_hash}",
    "test_passed": 312,
    "test_failed": 0,
    "coverage_frontend": 0.785,
    "coverage_backend": 0.812
  },
  "prev_version": null,
  "next_version": null
}
```
