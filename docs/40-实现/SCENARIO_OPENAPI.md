# SCENARIO_OPENAPI — 场景工作台新增端点契约片段（F07-15）

> 溯源 · 详细设计-07 场景层 workspace 场景工作台 §D-2（12 条 N1~N12 新增端点，只增不改）。
> 版本 · 1.0 · 修订 · 2026-10-10
> 校验 · 由 `check-scenario-openapi.mjs` 静态解析本文件，断言 12 个 operationId 齐备且 path/method 与 D-2 表格一致。
> 与后端 `ScenarioOpenApi` 反射 mapping 的对齐归 `OpenApiPathParityTest`（F07-15 / D-4.4），本片段是该测试的期望集来源之一。

## 约定

- 所有响应统一 `ApiResponse<T>` 包裹：`{ code:string, message:string, traceId:string, data:T }`（分册 00 定义，本文件不重定义，仅声明 `data` 的 schema）。
- 状态码真值化：4xx/5xx 原样穿透，**禁**把 503/异常包成 HTTP 200（X-16/X-17 修复，登记为 C 系列）。
- 认知端点 N4~N7 的 503 = 引擎禁用/schema 未就绪，前端 `useCognition` 置 `unavailable=true`，只渲染横幅+traceId，**绝不本地合成结论**（F07-12/F07-22）。

## OpenAPI 3.0 片段

```yaml
openapi: 3.0.3
info:
  title: ECOS Scenario Workspace — New Endpoints (D-2 N1~N12)
  version: "1.0"
tags:
  - name: workspace-scenario
  - name: scenario-cognition
  - name: scenario-action
paths:
  /api/v1/workspace/scenarios/{id}/completeness:
    get:
      tags: [workspace-scenario]
      operationId: getScenarioCompleteness
      parameters:
        - $ref: '#/components/parameters/ScenarioIdPath'
      responses:
        '200':
          description: 绑定覆盖度与孤儿探测
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/ApiResponse'
                  - type: object
                    properties:
                      data: { $ref: '#/components/schemas/Completeness' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }
        '500': { $ref: '#/components/responses/ServerError' }

  /api/v1/workspace/binding-catalog:
    get:
      tags: [workspace-scenario]
      operationId: listBindingCatalog
      responses:
        '200':
          description: 六类三层绑定目录
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/ApiResponse'
                  - type: object
                    properties:
                      data:
                        type: array
                        items: { $ref: '#/components/schemas/BindingCatalogItem' }
        '403': { $ref: '#/components/responses/Forbidden' }

  /api/v1/workspace/scenarios/{id}/forecast-scope:
    get:
      tags: [workspace-scenario]
      operationId: getForecastScope
      parameters:
        - $ref: '#/components/parameters/ScenarioIdPath'
      responses:
        '200':
          description: 预测口径与范围
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/ApiResponse'
                  - type: object
                    properties:
                      data: { $ref: '#/components/schemas/ForecastScope' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '404': { $ref: '#/components/responses/NotFound' }

  /api/v1/business/scenarios/{id}/cognition/detect:
    get:
      tags: [scenario-cognition]
      operationId: getCognitionDetect
      parameters:
        - $ref: '#/components/parameters/ScenarioIdPath'
      responses:
        '200':
          description: 异常探测（E1）
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/ApiResponse'
                  - type: object
                    properties:
                      data: { $ref: '#/components/schemas/DetectResult' }
        '400': { $ref: '#/components/responses/BadRequest' }
        '404': { $ref: '#/components/responses/NotFound' }
        '503': { $ref: '#/components/responses/EngineUnavailable' }

  /api/v1/business/scenarios/{id}/cognition/operation-eval:
    get:
      tags: [scenario-cognition]
      operationId: getOperationEval
      parameters:
        - $ref: '#/components/parameters/ScenarioIdPath'
      responses:
        '200':
          description: 运行评价（E2）
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/ApiResponse'
                  - type: object
                    properties:
                      data: { $ref: '#/components/schemas/OperationEval' }
        '400': { $ref: '#/components/responses/BadRequest' }
        '404': { $ref: '#/components/responses/NotFound' }
        '503': { $ref: '#/components/responses/EngineUnavailable' }

  /api/v1/business/scenarios/{id}/cognition/hypotheses:
    get:
      tags: [scenario-cognition]
      operationId: listHypotheses
      parameters:
        - $ref: '#/components/parameters/ScenarioIdPath'
      responses:
        '200':
          description: 假设列表（E3）
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/ApiResponse'
                  - type: object
                    properties:
                      data: { $ref: '#/components/schemas/HypothesesResult' }
        '400': { $ref: '#/components/responses/BadRequest' }
        '404': { $ref: '#/components/responses/NotFound' }
        '503': { $ref: '#/components/responses/EngineUnavailable' }

  /api/v1/business/scenarios/{id}/cognition/beliefs:
    get:
      tags: [scenario-cognition]
      operationId: listBeliefs
      parameters:
        - $ref: '#/components/parameters/ScenarioIdPath'
      responses:
        '200':
          description: 信念列表（E4）
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/ApiResponse'
                  - type: object
                    properties:
                      data: { $ref: '#/components/schemas/BeliefsResult' }
        '400': { $ref: '#/components/responses/BadRequest' }
        '404': { $ref: '#/components/responses/NotFound' }
        '503': { $ref: '#/components/responses/EngineUnavailable' }

  /api/v1/workspace/scenarios/{id}/actions:
    post:
      tags: [scenario-action]
      operationId: createScenarioAction
      parameters:
        - $ref: '#/components/parameters/ScenarioIdPath'
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/CreateActionRequest' }
      responses:
        '200':
          description: 五必填齐备的动作
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/ApiResponse'
                  - type: object
                    properties:
                      data: { $ref: '#/components/schemas/ScenarioAction' }
        '400': { $ref: '#/components/responses/BadRequest' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '503': { $ref: '#/components/responses/EngineUnavailable' }
    get:
      tags: [scenario-action]
      operationId: listScenarioActions
      parameters:
        - $ref: '#/components/parameters/ScenarioIdPath'
        - name: cursor
          in: query
          schema: { type: string }
      responses:
        '200':
          description: 分页 20 的动作列表
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/ApiResponse'
                  - type: object
                    properties:
                      data: { $ref: '#/components/schemas/ActionPage' }
        '403': { $ref: '#/components/responses/Forbidden' }

  /api/v1/workspace/actions/{actionId}/approve:
    post:
      tags: [scenario-action]
      operationId: approveScenarioAction
      parameters:
        - name: actionId
          in: path
          required: true
          schema: { type: string }
      responses:
        '200':
          description: 审批人取自令牌，非请求体
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/ApiResponse'
                  - type: object
                    properties:
                      data: { $ref: '#/components/schemas/ApproveResult' }
        '400': { $ref: '#/components/responses/BadRequest' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '409': { $ref: '#/components/responses/Conflict' }

  /api/v1/workspace/scenarios/{id}/decisions:
    get:
      tags: [scenario-action]
      operationId: listScenarioDecisions
      parameters:
        - $ref: '#/components/parameters/ScenarioIdPath'
      responses:
        '200':
          description: 决策记录列表
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/ApiResponse'
                  - type: object
                    properties:
                      data: { $ref: '#/components/schemas/DecisionPage' }
        '403': { $ref: '#/components/responses/Forbidden' }

  /api/v1/workspace/scenarios/{id}/status:
    patch:
      tags: [workspace-scenario]
      operationId: transitionScenarioStatus
      parameters:
        - $ref: '#/components/parameters/ScenarioIdPath'
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [status]
              properties:
                status: { type: string, enum: [DRAFT, ACTIVE, COMPLETED, SUSPENDED] }
      responses:
        '200':
          description: 迁移结果与后续允许态
          content:
            application/json:
              schema:
                allOf:
                  - $ref: '#/components/schemas/ApiResponse'
                  - type: object
                    properties:
                      data: { $ref: '#/components/schemas/StatusTransition' }
        '403': { $ref: '#/components/responses/Forbidden' }
        '409':
          description: ISLAND_BINDING / ILLEGAL_TRANSITION / NO_FORMAL_RUN / TERMINAL_STATE
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ApiResponse' }

components:
  parameters:
    ScenarioIdPath:
      name: id
      in: path
      required: true
      schema: { type: string }
  responses:
    BadRequest:
      description: 参数非法（如 mind 不属于该场景、五必填缺失）
      content:
        application/json:
          schema: { $ref: '#/components/schemas/ApiResponse' }
    Forbidden:
      description: 无权限（policy evaluate 拒绝）
      content:
        application/json:
          schema: { $ref: '#/components/schemas/ApiResponse' }
    NotFound:
      description: 场景不存在或已软删除
      content:
        application/json:
          schema: { $ref: '#/components/schemas/ApiResponse' }
    Conflict:
      description: 状态冲突（重复审批 / 非法迁移）
      content:
        application/json:
          schema: { $ref: '#/components/schemas/ApiResponse' }
    EngineUnavailable:
      description: 认知引擎禁用或 schema 未就绪（前端只渲染横幅，禁合成）
      content:
        application/json:
          schema: { $ref: '#/components/schemas/ApiResponse' }
    ServerError:
      description: 服务端错误
      content:
        application/json:
          schema: { $ref: '#/components/schemas/ApiResponse' }
  schemas:
    ApiResponse:
      type: object
      required: [code, traceId]
      properties:
        code: { type: string }
        message: { type: string }
        traceId: { type: string }
    Completeness:
      type: object
      properties:
        coverage: { type: number, nullable: true }
        verdict: { type: string }
        requiredEdges: { type: array, items: { type: object } }
        presentEdges: { type: array, items: { type: object } }
        missingEdges: { type: array, items: { type: object } }
        islands: { type: array, items: { type: object } }
        probeErrors: { type: array, items: { type: object } }
    BindingCatalogItem:
      type: object
      properties:
        type: { type: string }
        tier: { type: string }
        labelKey: { type: string }
        iconKey: { type: string }
        linkableTo: { type: array, items: { type: string } }
    ForecastScope:
      type: object
      properties:
        periods: { type: array, items: { type: string } }
        projectIds: { type: array, items: { type: string } }
        departmentIds: { type: array, items: { type: string } }
        caliberId: { type: string }
        coverageRatio: { type: number }
        requiredFactCells: { type: integer }
    DetectResult:
      type: object
      properties:
        anomalies: { type: array, items: { type: object } }
        traceId: { type: string }
    OperationEval:
      type: object
      properties:
        score: { type: number }
        dimensions: { type: array, items: { type: object } }
        narrative: { type: string }
        citations: { type: array, items: { type: object } }
    HypothesesResult:
      type: object
      properties:
        hypotheses: { type: array, items: { type: object } }
    BeliefsResult:
      type: object
      properties:
        beliefs: { type: array, items: { type: object } }
    CreateActionRequest:
      type: object
      required: [owner, dueDate, approver, expectedImpact, controlMetric]
      properties:
        owner: { type: string }
        dueDate: { type: string, format: date }
        approver: { type: string }
        expectedImpact: { type: string }
        controlMetric: { type: string }
    ScenarioAction:
      type: object
      properties:
        actionId: { type: string }
        fiveMandatory: { type: object }
    ActionPage:
      type: object
      properties:
        items: { type: array, items: { type: object } }
        nextCursor: { type: string, nullable: true }
    ApproveResult:
      type: object
      properties:
        status: { type: string }
        approver: { type: string, description: 取自 JWT 令牌，禁请求体伪造 }
    DecisionPage:
      type: object
      properties:
        items: { type: array, items: { type: object } }
    StatusTransition:
      type: object
      properties:
        status: { type: string }
        allowedTransitions: { type: array, items: { type: string } }
```
