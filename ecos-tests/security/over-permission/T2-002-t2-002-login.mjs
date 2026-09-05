
// T5/T2: 密码错应 401
import { login as realLogin, BASE } from "../../lib/security.mjs";
try {
  const r = await fetch(BASE + "/api/v1/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username: "definitely-not-exist", password: "WrongPass!123" }),
  });
  if (r.status !== 401) {
    throw new Error("expect 401 got " + r.status);
  }
  console.log("[PASS] T2-002 单用户/Login 用户名错: 401 验证通过");
  process.exit(0);
} catch (e) {
  console.error("[FAIL] T2-002 单用户/Login 用户名错: " + e.message);
  process.exit(1);
}