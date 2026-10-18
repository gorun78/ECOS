/**
 * Login page — username/password authentication with token fallback.
 * Calls POST /api/auth/login, stores accessToken in localStorage.
 * @license SPDX-License-Identifier: Apache-2.0
 */
import React, { useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import {
  ShieldCheck,
  Loader2,
  LogIn,
  KeyRound,
  ChevronDown,
} from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { authLogin, isErrorResponse, setAuthGracePeriod } from "../api";
import { fetchAuthMeWithToken } from "../services/auth";

export default function Login() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [token, setToken] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [showAdvanced, setShowAdvanced] = useState(false);

  // Where to redirect after successful login.
  // 落地根必须是「真实存在的路由根」：index 根自 2026-10-14 起为
  // <Navigate to="/decision" replace/>（用户裁决：首页直接进决策面板），
  // 而 `/app` 在全仓路由表里不存在（main.tsx 无 app 根）→ 登录后落到 404 兜底页
  // （检视报告 §4.1 P0 / §一 S6）。RequireAuth 传入的 from 仍优先。
  const from =
    (location.state as { from?: { pathname: string } })?.from?.pathname ||
    "/";

  const inputCls = `w-full px-4 py-2.5 ${styles.inputBg} ${styles.inputBorder} border rounded-lg text-sm ${styles.inputText} transition`;

  // ── Username / Password login ─────────────────────────────
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (!username.trim()) {
      setError(t("login.empty"));
      return;
    }
    if (!password) {
      setError(t("login.empty"));
      return;
    }

    setLoading(true);
    try {
      const data = await authLogin({ username: username.trim(), password });

      // Persist auth data
      localStorage.setItem("token", data.accessToken);
      if (data.username)
        localStorage.setItem("username", data.username);
      if (data.roles)
        localStorage.setItem("roles", JSON.stringify(data.roles));

      // 开 30s 宽限期：消化 Topbar/RequireAuth/several-mount 时对 P3 端点的初次探测
      // （例如 /api/v1/security-profiles/user/{id}），避免在本次 mount 高峰期里任意一个偶发 401
      // 触发 handleAuthExpired 把刚登录的 token 清掉并跳回 #/login。
      setAuthGracePeriod();

      // Fetch and persist userId for security profile API
      try {
        const meJson = (await fetchAuthMeWithToken(data.accessToken)) as { data?: { userId?: string } };
        if (meJson?.data?.userId) {
          localStorage.setItem("ecos_user_id", meJson.data.userId);
        }
      } catch { /* non-critical */ }

      navigate(from, { replace: true });
    } catch (err: unknown) {
      // 错误分类（报告 §五 L3：原实现只判 "Failed to fetch" 字面量，5xx 与密码错误
      // 同文案 → 用户不知道「该重试」还是「该找管理员」）。传输层失败已由 authLogin
      // 广播 ecos-network-down，这里补一条可重试口径。
      const info = isErrorResponse(err);
      if (info.kind === "network") {
        setError(t("login.noNetwork"));
      } else if (info.status && info.status >= 500) {
        setError(t("login.fail.server", { status: info.status }));
      } else {
        setError(err instanceof Error && err.message ? err.message : t("login.fail"));
      }
    } finally {
      setLoading(false);
    }
  };

  // ── Token login (advanced / fallback) ─────────────────────
  const handleTokenSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (!token.trim()) {
      setError(t("login.empty"));
      return;
    }

    localStorage.setItem("token", token.trim());
    navigate(from, { replace: true });
  };

  return (
    <div className={`min-h-screen flex items-center justify-center ${styles.appBg}`}>
      <div className="w-full max-w-md mx-4">
        {/* ── Brand ──────────────────────────────────────── */}
        <div className="text-center mb-8">
          <div className={`w-16 h-16 mx-auto mb-4 rounded-2xl flex items-center justify-center shadow-lg ${styles.accentBg}`}>
            <ShieldCheck className={`w-8 h-8 ${styles.accentSolidText}`} />
          </div>
          <h1 className={`text-2xl font-bold ${styles.text}`}>ECOS Platform</h1>
          <p className={`text-sm mt-1 ${styles.muted}`}>
            {t("login.subtitle")}
          </p>
        </div>

        {/* ── Login card ─────────────────────────────────── */}
        <div className={`rounded-2xl shadow-xl border p-8 ${styles.cardBg} ${styles.cardBorder}`}>
          <form onSubmit={handleSubmit} className="space-y-5">
            {/* Username */}
            <div>
              <label
                htmlFor="username"
                className={`block text-sm font-medium mb-1.5 ${styles.text}`}
              >
                {t("login.username")}
              </label>
              <input
                id="username"
                type="text"
                value={username}
                onChange={(e) => {
                  setUsername(e.target.value);
                  setError("");
                }}
                placeholder={t("login.username.placeholder")}
                className={inputCls}
                autoFocus
                disabled={loading}
              />
            </div>

            {/* Password */}
            <div>
              <label
                htmlFor="password"
                className={`block text-sm font-medium mb-1.5 ${styles.text}`}
              >
                {t("login.password")}
              </label>
              <input
                id="password"
                type="password"
                value={password}
                onChange={(e) => {
                  setPassword(e.target.value);
                  setError("");
                }}
                placeholder={t("login.password.placeholder")}
                className={inputCls}
                disabled={loading}
              />
            </div>

            {/* Error message */}
            {error && (
              <div className="rounded-lg px-4 py-3 border border-red-500/50 bg-red-500/10">
                <p className="text-red-500 text-sm">{error}</p>
              </div>
            )}

            {/* Submit button */}
            <button
              type="submit"
              disabled={loading}
              className="w-full py-2.5 px-4 text-white font-medium rounded-lg shadow-md transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed active:scale-[0.98] inline-flex items-center justify-center gap-2 cursor-pointer"
              style={{ backgroundColor: "var(--accent)" }}
            >
              {loading ? (
                <>
                  <Loader2 className="animate-spin h-4 w-4" />
                  {t("login.loading")}
                </>
              ) : (
                <>
                  <LogIn className="h-4 w-4" />
                  {t("login.login")}
                </>
              )}
            </button>
          </form>

          {/* ── Advanced: Token login ────────────────────── */}
          <div className={`mt-6 pt-4 border-t ${styles.divider}`}>
            <button
              type="button"
              onClick={() => setShowAdvanced(!showAdvanced)}
              className={`w-full text-xs flex items-center justify-center gap-1 transition cursor-pointer ${styles.muted} hover:opacity-80`}
            >
              {showAdvanced ? t("login.advanced.collapse") : t("login.advanced.open")}
              <ChevronDown
                className={`w-3 h-3 transition-transform ${
                  showAdvanced ? "rotate-180" : ""
                }`}
              />
            </button>

            {showAdvanced && (
              <form onSubmit={handleTokenSubmit} className="mt-3 space-y-3">
                <div>
                  <label
                    htmlFor="token"
                    className={`block text-sm font-medium mb-1.5 ${styles.text}`}
                  >
                    {t("login.token.label")}
                  </label>
                  <input
                    id="token"
                    type="password"
                    value={token}
                    onChange={(e) => {
                      setToken(e.target.value);
                      setError("");
                    }}
                    placeholder={t("login.token.placeholder")}
                    className={inputCls}
                  />
                </div>
                <button
                  type="submit"
                  className={`w-full py-2 px-4 rounded-lg transition text-sm font-medium inline-flex items-center justify-center gap-1.5 cursor-pointer ${styles.cardTextMuted} bg-white/5 dark:bg-white/5 hover:bg-white/10 dark:hover:bg-white/10`}
                >
                  <KeyRound className="h-3.5 w-3.5" />
                  {t("login.token.submit")}
                </button>
              </form>
            )}
          </div>
        </div>

        {/* ── Footer ─────────────────────────────────────── */}
        <p className={`text-center text-xs mt-6 ${styles.muted}`}>
          {t("login.footer")}
        </p>
      </div>
    </div>
  );
}
