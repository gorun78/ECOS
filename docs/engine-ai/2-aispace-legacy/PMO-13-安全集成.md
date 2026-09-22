# PMO指令：Phase2-2-安全集成 — Ontology × Security 深层集成

> 来源: 完善计划 Phase 2-2 T5 | 工期: 1周 | 范围: ontology-engine + security-engine | 依赖: PMO-12 + Phase 1 security-engine完成

---

## §背景

security-engine已有RLS/CLS/ABAC/脱敏端点，ontology-engine有对象查询。但两者当前独立——Ontology查询不自动注入安全策略。需要深度集成：每次Ontology查询自动调RLS/CLS/ABAC。

---

## §禁止清单

1. ❌ 不新增REST端点（纯AOP切面实现，透明注入）
2. ❌ 安全过滤失败=返回空数据（不暴露数据结构）→ 日志记WARN
3. ❌ 安全引擎不可用时：默认DENY（宁误拒不误放）

---

## §Task

### T5-1: Ontology查询拦截器（3天）

**文件**: 新建 `ontology-engine-impl/.../interceptor/OntologySecurityInterceptor.java`

**实现**: Spring AOP切面，拦截所有 `OntologyObjectService.query/list/get` 方法

**注入点1 — 查询前注入RLS**:
```java
@Before("execution(* com.chinacreator.gzcm.engine.ontology.service.OntologyObjectService.*(..))")
public void beforeQuery(JoinPoint jp) {
    // 1. 获取当前 userId (从UserContext)
    // 2. 调 POST /api/security/rls/apply → 获取 rlsCondition
    // 3. 将 rlsCondition 附加到查询参数的 filter 中
}
```

**注入点2 — 查询后注入CLS+脱敏**:
```java
@AfterReturning(pointcut = "...", returning = "result")
public void afterQuery(Object result) {
    // 1. 调 POST /api/security/cls/columns → 获取可见列
    // 2. 过滤result中的字段，移除blocked列
    // 3. 对敏感字段调 /api/security/mask
}
```

### T5-2: ActionType执行前权限校验（2天）

**文件**: 修改 `PreconditionEngine.java` (PMO-12创建)

**注入点 — ActionType.execute前调ABAC**:
```java
// 在 execute() 开始时
Map<String, Object> abacResult = securityPolicyClient.evaluate(Map.of(
    "subject", Map.of("userId", userId, "role", getUserRole(userId)),
    "resource", Map.of("type", "action", "id", actionType.getId()),
    "action", "execute"
));
if (!abacResult.get("allow")) {
    throw new ForbiddenException("无权执行此操作");
}
```

**验收**:
```bash
# 1. 查询本体对象 → 自动过滤
curl -X GET "/api/v1/ontology/objects/PurchaseOrder?userId=analyst_001" \
  -H "Authorization: Bearer $TOKEN"
# 期望: 返回列表自动应用RLS条件(只返回analyst所在租户的订单)
# 且字段自动去除了CLS屏蔽列

# 2. ActionType执行 → 自动权限检查
curl -X POST /api/v1/ontology/actions/delete_order/execute \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"objectId":"po_999","context":{"userId":"viewer_001"}}'
# 期望: 403 "无权执行此操作"（viewer角色无权执行delete）
```
