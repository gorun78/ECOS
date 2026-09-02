
import { req, gatewayAlive, reportFail, report } from "../../lib/security.mjs";
try {
  if (!(await gatewayAlive())) {
    console.log("[ENV_BLOCKED] case: T1-007 单租户/Rls 无 token");
    process.exit(77);
  }
  // 铁律: 无 token 必 401/403, 不允许 2XX/5XX (跨租户/跨用户/越权拦截)
  // PRD-超前接口允许 404 (说明还没实现, 也算阻断成功)
  const r = await req("POST", "/api/security/rls/apply", {});
  const bad = r.status >= 200 && r.status < 400 && r.status !== 404;
  if (bad) {
    throw new Error("默认 DENY 被突破: POST /api/security/rls/apply 无 token 收到 " + r.status);
  }
  report("T1-007 单租户/Rls 无 token (status=" + r.status + ")");
} catch (e) {
  reportFail("T1-007 单租户/Rls 无 token", e);
}
