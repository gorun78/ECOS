# 服务层 ge (D→I) 接口与验收 flows

> 格物·数据→信息转化 | 源码: `engine/data-engine/data-engine-impl/.../data/transform/` | 宿主 data-engine :18082
> 定位: Wave-2B 收口版本。四转化暂无独立 service module (铁律 §5.1#10)，ge 实际代码寄居 data-engine-impl `transform` 包。

## 接入 flows
client → Gateway :8080 → security (闸门, 无独立 REST 一跳) → `TransformController`(data-engine-impl) → `ITransformService` → 回 `ApiResponse`。
transform 写不动 Pipeline 执行, 对流式 transform 与 PipelineTask 跨模块耦合时发 `PipelineEvent`(COLLECTION_COMPLETED/TRANSFORM_COMPLETED) 到 common-api 事件通道, 不直接 import 其他 engine。

## 主 API (curl)
```bash
curl -s "http://localhost:8080/api/v1/engine/data/transform/meta" -H "Authorization: Bearer $TOKEN"   # 6 类 step 清单
curl -s -X POST "http://localhost:8080/api/v1/engine/data/transform/execute" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"input":{"columns":["a","b"],"rows":[{"a":1,"b":" x"}]},"chain":[{"type":"cleansing","params":{}},{"type":"mapping","params":{"a":"colA"}}]}'
```

## 接 DB 表
transform 为无状态内存计算, ge 不落自有表; 间接读 `ecos_pipeline_task` / `ecos_pipeline_step` 取 YAML 源 (Pipeline 执行链路里 ge step)。

## 别接 (调谁, 已核)
- 不自建 Driver / 不 new PG/Neo4j 连接 — 基础设施访问一律走 `runtime-access`
- 不调 LLM (那是 `llm-gateway` 的事), 不写安全/脱敏逻辑 (那是 security-engine 的事)

## 验收 flows
`GET /api/v1/engine/data/transform/meta` 返回 6 class step registry; `POST /execute` 传 cleansing step, 断言 `output.rows[0].b == "x"` 且 `statistics.filteredCount >= 0`。
发布事件: `curl -s -X POST "http://localhost:8080/api/v1/engine/data/transform/execute" -d {verify 后}` → 验收脚本 `bash ecos_backend/ecos-gen-scratch/wave2b-ge-verify.sh` 输出 `transform verified 6/6`。
