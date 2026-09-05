
import { req, gatewayAlive, reportFail, report } from "../../lib/security.mjs";
try {
  if (!(await gatewayAlive())) {
    console.log("[ENV_BLOCKED] case: T1-001 单租户/Kb column PRD-超前");
    process.exit(77);
  }
  // PRD-超前接口 — 期望 404 (说明接口不存在 → 铁律传递上去) 不允许 2XX
  const r = await req("GET", "/api/v1/knowledge/column", {});
  if (r.status !== 404) {
    throw new Error("PRD-超前接口期望 404, 实际 " + r.status + " (可能被 PRD 实现却未走三滤波器 → 越权)");
  }
  console.log("[PASS] T1-001 单租户/Kb column PRD-超前: 404 验证通过");
  process.exit(0);
} catch (e) {
  console.error("[FAIL] T1-001 单租户/Kb column PRD-超前: " + e.message);
  process.exit(1);
}
