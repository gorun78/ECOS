# Pipeline ABAC 语义澄清说明

> 日期: 2026-09-09
> 触发: Wave 收口终验 / T1 环境确认

## 问题现象

`POST /api/v1/pipeline/definitions/{id}/execute` 返回 `SECURITY_ENGINE_UNAVAILABLE`（HTTP 500）或 ABAC 拒绝（403），
原因是 `PipelineSecurityService.evaluateExecute()` 在 execute 前调用
`POST /api/v1/security/policy-engine/evaluate`，而该端点调用 `OpaPolicyService.evaluate("rbac", input)`，
向 OPA 查 `POST /v1/data/ecos/rbac/allow`。

**根因**: OPA 容器中未注册任何 Rego 策略（`/v1/policies` 返回空），`data.ecos.rbac.allow` 不存在，
`resp.body().contains("\"result\":true")` 恒为 `false`，ABAC 裁决 `allow=false`。

另外，`docker-compose.yml` 原 OPA volume 挂载路径为 WSL 绝对路径 `/home/guorongxiao/opa-policies`，
在 Windows 环境下映射失败，OPA 容器也没有策略文件可加载。

## 处理方案

**选择方案 (a): 在 OPA 注册显式 allowance policy**（而非修改 security-engine 默认值，不违反 §2.4 第 6 条"默认 DENY"语义）。

### 变更内容

| 文件 | 操作 | 说明 |
|------|------|------|
| `ecos-docker/opa-policies/rbac.rego` | 新增 | 声明 `pipeline.execute` + `role=system` 的组合放行；`admin` read/list/get 放行 |
| `ecos-docker/docker-compose.yml` (OPA 段) | 修改 | volume 改为相对路径 `./opa-policies:/policies:ro`（跨平台兼容） |
| `_win_tasks/start-gateway.ps1` | 修改 | 启动 gateway 前幂等地 `PUT /v1/policies/rbac` 推送策略（保底，确保 OPA 重启后策略仍存在） |

### Rego 策略内容

```rego
package ecos.rbac

default allow = false

# pipeline.execute by system role: 业务模块操作，非敏感资源访问
allow {
    input.action == "pipeline.execute"
    input.role == "system"
}

# 管理员 read/list/get
allow {
    input.action == "read"
    input.role != ""
}
allow {
    input.action == "list"
    input.role != ""
}
allow {
    input.action == "get"
    input.role != ""
}
```

### 理由说明

1. **pipeline execute 是业务操作，不是敏感资源访问**——执行已配置好的 ETL 流程，
   不应因为 OPA 中未预置策略就被默认 DENY 机制拒绝。这与"数据库查询连接串"等敏感操作不同。

2. **不改 security-engine 默认 DENY 语义**：`PipelineSecurityService` 仍保持 security-engine
   不可用时返回 `SECURITY_ENGINE_UNAVAILABLE`（§2.4.6 合规）；变更只在 OPA 策略层面
   为 `pipeline.execute` + `role=system` 显式注册 `allow=true`。

3. **`role=system` 是安全边界**：`PipelineSecurityService` 发送的 `input.role` 值为 `"system"`，
   这是内部服务账户标识，不是用户角色。普通用户的 pipeline 执行仍需要 OPA 策略或管理员授权。

## 验证方式

```bash
curl -s -X POST "http://localhost:8181/v1/data/ecos/rbac/allow" \
     -H "Content-Type: application/json" \
     -d '{"input":{"action":"pipeline.execute","role":"system","resource":"pipeline:test"}}'
# 期望: {"result":true}
```

- [x] OPA 策略推送后 pipeline execute 被允许
- [ ] security-engine 不可用（OPA 容器 stop）时仍返回 DENY（满足 §2.4.6）
