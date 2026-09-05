---
id: API-{seq}
summary: >
  {一句话描述API设计内容，包括核心接口、协议、认证方式。}
scope:
  - {范围1}
  - {范围2}
not_for:
  - Architecture design
  - Database schema
  - UI implementation
read_when:
  - Designing APIs
  - Frontend integration
  - API review
related:
  - docs/02设计阶段/02-01架构设计/{doc-name}.md
  - docs/02设计阶段/02-02数据库设计/{doc-name}.md
status: draft
---

# API设计文档 | {系统名称} | v{version} | {status} | ARCH_SPEC@{arch_hash}

**版本**: v{version}
**状态**: {DRAFT | APPROVED | REJECTED}
**Hash**: {内容哈希}
**关联制品**: ARCH_SPEC@{arch_hash}, PRD@{prd_hash}
**评审记录**: 见本文档末尾 APPROVAL_RECORD

---

## 前置校验

收到 API 设计请求，校验以下前提条件：

1. [ ] ARCH_SPEC 状态为 APPROVED ✅
2. [ ] ARCH_SPEC 有批准记录 ✅
3. [ ] ARCH_SPEC 的 deliverable_allowed = true ✅

当前 ARCH_SPEC：
- 版本：{version}
- Hash：{hash}
- 状态：APPROVED
- 模块数量：{n} 个

→ ARCH_SPEC 校验通过，可开始 API 设计

---

## API → 模块 → PRD 追溯表

| API Path | Method | Summary | 所属模块 | 覆盖 PRD 功能 |
|----------|--------|---------|---------|-------------|
| /{resource} | GET | {描述} | {module-name} | F{id1}, F{id2} |
| /{resource} | POST | {描述} | {module-name} | F{id} |
| /{resource}/{id} | GET | {描述} | {module-name} | F{id} |
| /{resource}/{id} | PUT | {描述} | {module-name} | F{id} |
| /{resource}/{id} | DELETE | {描述} | {module-name} | F{id} |

---

## 命名规范（RESTful 原则）

```
资源命名：
- 使用名词，而非动词：/users 而非 /getUsers
- 复数形式：/users 而非 /user
- 小写 + 连字符：/user-profiles 而非 /userProfiles
- 层级嵌套表示关系：/users/{id}/orders

HTTP 方法映射：
GET    - 查询（幂等，不修改数据）
POST   - 创建（非幂等）
PUT    - 全量更新（幂等）
PATCH  - 部分更新（幂等）
DELETE - 删除（幂等）
```

---

## OpenAPI 3.0 规范

```yaml
openapi: 3.0.3
info:
  title: {系统名称} API
  version: {version}
  description: {系统名称} RESTful API 规范
  x-arch-ref: ARCH_SPEC@{arch_hash}
  x-prd-ref: PRD@{prd_hash}

servers:
  - url: https://api.example.com/v1
    description: 生产环境
  - url: https://staging-api.example.com/v1
    description: 预发布环境

paths:
  /{resource}:
    get:
      summary: 查询{资源}列表
      description: 支持分页、过滤、排序
      operationId: list{Resource}
      tags:
        - {module-name}
      x-module: {module-name}
      x-prd-functions: [F{id1}, F{id2}]
      parameters:
        - name: page
          in: query
          schema:
            type: integer
            default: 1
          description: 页码
        - name: size
          in: query
          schema:
            type: integer
            default: 10
          description: 每页条数
        - name: keyword
          in: query
          schema:
            type: string
          description: 关键字搜索
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/{Resource}ListResponse'
        '401':
          $ref: '#/components/responses/Unauthorized'
        '403':
          $ref: '#/components/responses/Forbidden'

    post:
      summary: 创建{资源}
      description: 创建新的{资源}
      operationId: create{Resource}
      tags:
        - {module-name}
      x-module: {module-name}
      x-prd-functions: [F{id}]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/{Resource}CreateRequest'
      responses:
        '201':
          description: 创建成功
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/{Resource}Response'
        '400':
          $ref: '#/components/responses/BadRequest'

  /{resource}/{id}:
    get:
      summary: 获取{资源}详情
      description: 根据ID获取{资源}详情
      operationId: get{Resource}ById
      tags:
        - {module-name}
      x-module: {module-name}
      x-prd-functions: [F{id}]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
      responses:
        '200':
          description: 成功
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/{Resource}Response'
        '404':
          $ref: '#/components/responses/NotFound'

    put:
      summary: 更新{资源}
      description: 更新指定ID的{资源}
      operationId: update{Resource}ById
      tags:
        - {module-name}
      x-module: {module-name}
      x-prd-functions: [F{id}]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/{Resource}UpdateRequest'
      responses:
        '200':
          description: 更新成功
        '404':
          $ref: '#/components/responses/NotFound'

    delete:
      summary: 删除{资源}
      description: 删除指定ID的{资源}
      operationId: delete{Resource}ById
      tags:
        - {module-name}
      x-module: {module-name}
      x-prd-functions: [F{id}]
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
      responses:
        '204':
          description: 删除成功
        '404':
          $ref: '#/components/responses/NotFound'

components:
  schemas:
    {Resource}ListResponse:
      type: object
      properties:
        code:
          type: integer
          example: 0
        message:
          type: string
          example: success
        data:
          type: object
          properties:
            list:
              type: array
              items:
                $ref: '#/components/schemas/{Resource}Response'
            total:
              type: integer
              example: 100

    {Resource}Response:
      type: object
      properties:
        id:
          type: string
          example: xxx
        name:
          type: string
        status:
          type: string
        createdAt:
          type: string
          format: date-time
        updatedAt:
          type: string
          format: date-time

    {Resource}CreateRequest:
      type: object
      required:
        - name
      properties:
        name:
          type: string
          description: 名称

    {Resource}UpdateRequest:
      type: object
      properties:
        name:
          type: string
          description: 名称

  responses:
    BadRequest:
      description: 参数错误
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'

    Unauthorized:
      description: 未认证
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'

    Forbidden:
      description: 无权限
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'

    NotFound:
      description: 资源不存在
      content:
        application/json:
          schema:
            $ref: '#/components/schemas/ErrorResponse'

    ErrorResponse:
      type: object
      properties:
        code:
          type: integer
          example: 400
        message:
          type: string
          example: 参数错误
        data:
          type: null
```

---

## 错误码规范

| 错误码 | HTTP Status | 说明 |
|--------|-------------|------|
| SUCCESS | 200 | 成功 |
| INVALID_PARAMETER | 400 | 请求参数错误 |
| UNAUTHORIZED | 401 | 未认证 |
| FORBIDDEN | 403 | 无权限 |
| NOT_FOUND | 404 | 资源不存在 |
| CONFLICT | 409 | 资源冲突 |
| INTERNAL_ERROR | 500 | 内部错误 |

错误响应格式：
```json
{
  "code": "NOT_FOUND",
  "message": "用户不存在",
  "data": null
}
```

---

### API 设计评审确认门

请确认以下内容：
1. 所有 API 都有模块归属（tags 对应 ARCH_SPEC 模块）？
2. 所有 API 都有 PRD 功能来源追溯（x-prd-functions）？
3. RESTful 命名规范是否遵守？
4. 认证/授权定义是否完整？
5. 错误码规范是否统一？
6. OpenAPI 文档是否通过 swagger-cli validate？

**审批人**：技术负责人

**批准结果**：
- APPROVED → 状态改为 APPROVED，生成 Hash，分发给 Fullstack
- REJECTED → 打回修改

---

## APPROVAL_RECORD

```json
{
  "artifact": "OPENAPI",
  "name": "{系统名称} API 规范",
  "version": "v{version}",
  "hash": "{内容哈希}",
  "status": "APPROVED",
  "approvals": [
    {
      "role": "技术负责人",
      "result": "APPROVED",
      "timestamp": "{timestamp}",
      "conditions": []
    }
  ],
  "gates_passed": ["Gate-1"],
  "deliverable_allowed": true,
  "arch_ref": "ARCH_SPEC@{arch_hash}",
  "prd_ref": "PRD@{prd_hash}",
  "api_counts": {
    "{module-name}": {n}
  },
  "prev_version": null,
  "next_version": null
}
```

---

## 验证步骤

1. [ ] ARCH_SPEC 校验通过（APPROVED + APPROVAL_RECORD）
2. [ ] 每个 API 有 `x-module` 字段指向 ARCH_SPEC 模块
3. [ ] 每个 API 有 `x-prd-functions` 字段指向 PRD 功能
4. [ ] OpenAPI 通过 swagger-cli validate
5. [ ] 评审确认门已通过（Gate-1 APPROVED）
6. [ ] OPENAPI_APPROVAL_RECORD 已生成，deliverable_allowed=true
7. [ ] API 数量与 ARCH_SPEC 模块功能匹配（无遗漏模块）
8. [ ] RESTful 命名规范 100% 遵守