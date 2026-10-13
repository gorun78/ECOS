/**
 * RequireAuth — route guard (fail-closed, W203/C185).
 * 1. No token → redirect to /login. No data request is ever issued.
 * 2. Token present → verify via backend GET /api/v1/auth/me.
 *    - 401 → redirect to /login (token expired).
 *    - 403 → redirect to /no-access (token valid, insufficient permission).
 *    - 200 → render children.
 *    - 其它非 2xx（5xx / 4xx / 探测响应不可解析）→ DENY：跳 /no-access 并携带
 *      `reason: "unavailable"`，由 NoAccess 页给出「安全服务不可用 + 重试」口径。
 *      The frontend must not synthesize a pass when the security engine is
 *      unreachable — the "security unavailable → default DENY" iron rule
 *      outranks optimism (报告 §4.8：原实现 else 分支把 5xx 当 ok 放行 = P0 fail-open).
 *      checkedRef stays false so the next navigation re-probes.
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */
import { useEffect, useRef, useState, type ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useTheme } from "./ThemeContext";
import { authMeProbe, getAuthToken } from "../services/auth";

type AuthState = "loading" | "ok" | "no-access" | "login";

/** /no-access 页的两种成因口径（NoAccess 据此选文案与是否给重试）。 */
type DenyReason = "forbidden" | "unavailable";

export default function RequireAuth({ children }: { children: ReactNode }) {
  const location = useLocation();
  const { styles } = useTheme();
  const token = getAuthToken();
  const [state, setState] = useState<AuthState>("loading");
  const [denyReason, setDenyReason] = useState<DenyReason>("forbidden");
  const checkedRef = useRef(false);

  useEffect(() => {
    if (!token) {
      setState("login");
      return;
    }
    // G2-C-001: the flag is set ONLY after the /auth/me request settles —
    // setting it before the fetch would make a re-render's cleanup-abort
    // (AbortError) look like a settled check, permanently skipping retries
    // and, via the old blanket catch, fail-open past 401/403.
    if (checkedRef.current) return;

    const ctrl = new AbortController();
    authMeProbe(token, ctrl.signal)
      .then((res) => {
        // Settle: auth decision is final — record the check.
        checkedRef.current = true;
        // fail-closed 判据：只有「显式 ok」才放行；401/403 各自的语义照旧，
        // 其余任何状态（含 5xx / 3xx / 解析失败 / res 为 null）一律 DENY。
        if (res?.status === 401) {
          setState("login");
          return;
        }
        if (res?.status === 403) {
          setDenyReason("forbidden");
          setState("no-access");
          return;
        }
        if (res?.ok) {
          setState("ok");
          return;
        }
        console.warn(`[RequireAuth] /auth/me returned non-OK (${res?.status ?? "no response"}) — defaulting to DENY (fail-closed).`);
        setDenyReason("unavailable");
        setState("no-access");
      })
      // AbortError = re-render cancelled the in-flight fetch: keep "loading"
      // so the re-run re-issues the request. Real network error = the decision
      // cannot be verified → fail-closed to "no-access" (W203/C185): children
      // (and their data fetches) are never mounted. Not marked as checked, so
      // the next navigation re-probes against a recovered backend.
      .catch((e: unknown) => {
        if (checkedRef.current) return;
        if (e instanceof DOMException && e.name === "AbortError") return; // keep loading; re-render retries
        if (ctrl.signal.aborted) return;
        console.warn("[RequireAuth] /auth/me transport error — defaulting to DENY (fail-closed):", e);
        setDenyReason("unavailable");
        setState("no-access");
      });
    return () => ctrl.abort();
  }, [token]);

  if (!token) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (state === "loading") {
    // 注意：styles.muted 是 Tailwind 类名字符串（如 "text-slate-500"），
    // 只能进 className；旧实现把它塞进 style={{color}} = 无效 CSS（报告 §4.8）。
    return (
      <div
        className={`min-h-screen flex items-center justify-center ${styles.appBg}`}
      >
        <div className={`text-sm animate-pulse ${styles.muted}`}>
          …
        </div>
      </div>
    );
  }

  if (state === "login") return <Navigate to="/login" state={{ from: location }} replace />;
  if (state === "no-access") {
    // 携带成因 + 被拦路径，NoAccess 页据此区分「无权限」与「安全服务不可用（可重试）」。
    // 只放可结构化克隆的字符串：history.replaceState 不接受函数（DataCloneError）。
    return (
      <Navigate
        to="/no-access"
        state={{ denyReason, blockedPath: location.pathname }}
        replace
      />
    );
  }

  return <>{children}</>;
}
