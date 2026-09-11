# sysman-impl (系统管理·实现层) 接口与验收 flows

> 系统管理·IAM/字典/配置/租户/聚合查询 | 宿主 gateway :8080 (无独立 Fat-JAR) | 顶层: 见 sysman/ 既有 AGENTS.md
> 源码: UserController / RoleController / DictController / SysConfigController / TenantController / MonitoringController / CausalController / WorldModelGraphController / SystemParamController / PermissionController / OrganizationController / EntityTableMappingController; GlobalExceptionHandler 在 sysman-boot

## 接入 flows
client → Gateway :8080 → sysman-impl controller group (iam / dict / system-config / tenant) → service/Dao → 回 `ApiResponse`。
跨引擎聚合 (Causal/Monitoring/WorldModel) 只经 REST 打引擎 (:18086-18089), 不 import 引擎 impl — 全仓跨引擎 import 0 命中已核 (Wave-5.3 T-15 反证)。

## 主 API (curl)
```bash
curl -s "http://localhost:8080/api/v1/system/users?pageNum=1&pageSize=20" -H "Authorization: Bearer $TOKEN"
curl -s -X POST "http://localhost:8080/api/v1/system/config" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"key":"ecos_demo_mode","value":"false"}'
curl -s "http://localhost:8080/api/v1/system/tenants" -H "Authorization: Bearer $TOKEN"
```

## 接 DB 表
`sysman_user` / `sysman_role` / `sysman_dict` / `ecos_tenant` (V37 统一租户) / `ecos_sys_config` (V13 + V40 enhance); 异常统一 `GlobalExceptionHandler` (sysman-boot), 返回 `ApiResponse` 标准体。

## 别接 (调谁, 已核)
- 安全裁决不在此出: 登录锁定/审计链/ABAC 全打 security-engine REST (铁律 2.4), sysman 只做 IAM 账户/角色 CRUD 与查询
- 跨引擎查询走 REST: `CausalController` → cognitive `/api/v1/cognitive/*`; `MonitoringController` → 本机 runtime-monitor
- 不建 Driver, 不落引擎表 (只读交叉查询, 不写 kb/cognitive 表 — 铁律 3.3)

## 验收 flows
`GET /api/v1/system/users` 分页 200 + `tenants` 列表可见多租户 (V37 后 tenantId 返回);
`POST /api/v1/system/config` 后 `GET /api/v1/system/config/{key}` 回显新值; 跨引擎 `GET /api/v1/ecos/{causal|world-model}/*` 经 Rest_template 打引擎时, 引擎 :18086/:18089 健康否则 503 (显式错误码不裸 500)。
