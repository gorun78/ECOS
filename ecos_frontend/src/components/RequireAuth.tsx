/**
 * RequireAuth — route guard.
 * 1. No token → redirect to /login.
 * 2. Token present → verify via backend GET /api/v1/auth/me.
 *    - 401 → redirect to /login (token expired).
 *    - 403 → redirect to /no-access (token valid, insufficient permission).
 *    - 200 → render children.
 *    - network error → render children optimistically (fail-open for network).
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */
import { useEffect, useRef, useState, type ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useTheme } from "./ThemeContext";

type AuthState = "loading" | "ok" | "no-access" | "login";

export default function RequireAuth({ children }: { children: ReactNode }) {
  const location = useLocation();
  const { styles } = useTheme();
  const token = localStorage.getItem("token");
  const [state, setState] = useState<AuthState>("loading");
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
    fetch("/api/v1/auth/me", {
      headers: { Authorization: `Bearer ${token}` },
      signal: ctrl.signal,
    })
      .then((res) => {
        // Settle: auth decision is final — record the check.
        checkedRef.current = true;
        if (res.status === 401) setState("login");
        else if (res.status === 403) setState("no-access");
        else setState("ok");
      })
      // AbortError = re-render cancelled the in-flight fetch: keep "loading"
      // so the re-run re-issues the request. Real network error stays
      // fail-open ("ok") per the header contract — an unreachable backend
      // must not lock users out of the shell (auth re-checks on next nav).
      .catch((e: unknown) => {
        if (checkedRef.current) return;
        if (e instanceof DOMException && e.name === "AbortError") return; // keep loading; re-render retries
        if (ctrl.signal.aborted) return;
        setState("ok"); // real network error — fail open (see header)
      });
    return () => ctrl.abort();
  }, [token]);

  if (!token) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (state === "loading") {
    return (
      <div
        className={`min-h-screen flex items-center justify-center ${styles.appBg}`}
      >
        <div className="text-sm animate-pulse" style={{ color: styles.muted }}>
          …
        </div>
      </div>
    );
  }

  if (state === "login") return <Navigate to="/login" state={{ from: location }} replace />;
  if (state === "no-access") return <Navigate to="/no-access" replace />;

  return <>{children}</>;
}
