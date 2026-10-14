# ECOS 年度经营预测 Agent：只读依赖核查记录

- 核查日期：2026-10-14
- 代码基线：PR 的 master base SHA `cf1721f41ff6558d23e898e36ca4289d23b08c06`
- 核查方法：读取仓库源代码、需求规格和测试定义；**未访问正在运行的开发/验收环境**。
- 结论等级：静态代码/文档核查，不等同于运行态验证。

## 已确认的代码契约

| 能力 | 代码证据 | 静态核查结论 |
|---|---|---|
| 预测 run 创建 | `workspace/.../controller/ForecastRunController.java` | `POST /api/v1/workspace/forecast-runs`；请求由 `ForecastRunRequestValidator` 校验场景、runType、口径 ID/版本、asOf、snapshot、formulaVersion、幂等键与内容指纹 |
| run 状态/结果/证据 | 同一 `ForecastRunController` | 有状态、结果、证据读取端点；读取路径调用 `ScenarioReadGuard` |
| run 依赖接缝 | `workspace/.../forecast/ForecastRunGateway.java`、`ForecastRunOrchestrationService.java` | Gateway 是 SPI 抽象；服务在依赖未装配时按契约返回未上线语义，禁止模拟成功 |
| 预测范围 | `workspace/.../controller/ForecastScopeController.java`、`scenario/ForecastScopeService.java` | `GET /api/v1/workspace/scenarios/{id}/forecast-scope` 存在；`ScopeDefaultsProvider` 未装配时返回空默认值，因此 2xx 本身不能证明范围数据齐全 |
| 确定性产物 | cognitive engine 的 `DeterministicForecastCalculator.java` | 组装已有快照中的数值；缺失值拒绝；不负责创造新预测数值 |
| 认知预测 API | cognitive engine 的 `ForecastController.java` | `POST /api/v1/cognitive/forecast` 与模型注册表读写端点在代码中声明；声明不证明服务已部署或运行 |
| 场景运行 | `workspace/.../controller/ScenarioRunController.java` | 有 `POST/GET /api/v1/workspace/scenarios/{id}/runs`；必须继续核查其下游实际装配与真实数据依赖 |

## PRD 明确记录的验收缺口

PRD-09 v1.3（2026-10-12）说明：
- H-1~H-8 的相关平台前置达到“代码在位 + 单元门禁实跑”层级，不等于业务验收通过。
- V277/V278/V279/V280 四支迁移脚本尚未对 `ecos-postgres` 实跑。
- 六项业务演练尚未执行。
- 前端四源尚未实测串通。
- REQ-FC-01~07 均仍为“待验收”。

本试点不得把以上状态改写为“已完成”。

## 当前阻塞与只读检查要求

1. 本次会话没有提供开发/验收环境的 `ECOS_API` 地址、有效测试凭据或已确认的场景 ID，因此不能诚实地报告真实 HTTP 状态、当前数据库记录数、口径审批状态、数据质量状态或 SPI Bean 装配状态。
2. 下一步使用 `annual-forecast-agent-preflight.spec.ts` 执行 GET-only 探测。所有状态码与响应体摘要需作为运行证据保存。
3. 若预测范围端点返回 2xx，仍须检查响应中的 periods、projectIds、departmentIds、caliberId、coverage 等字段是否真实、完整；空默认不是通过。
4. 必须通过真实口径接口读取已审批版本；不能把固定的 `MANAGEMENT_FORECAST` 路径或示例场景 ID 当成该环境一定存在的数据。
5. 只有在环境所有者确认允许后，才考虑创建 sandbox run。用户当前授权为只读，故本阶段不调用任何 POST/PUT/PATCH/DELETE 业务端点。

## 运行只读预检

在 `ecos-tests` 目录配置已有验收环境的 `.env`，至少包括 `ECOS_API`、`ECOS_USER`、`ECOS_PASS`，并设置环境中已确认存在的 `ECOS_PILOT_SCENARIO_ID`。

```bash
npm run test:api -- tests/api/annual-forecast-agent-preflight.spec.ts
```

预检只发 GET 请求。401/403 表示认证/权限阻塞，404 表示场景或路由不存在/不匹配，501/503 表示能力未就绪，5xx 表示服务端异常；不得将这些状态归一成成功。
