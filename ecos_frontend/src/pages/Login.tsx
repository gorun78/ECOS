/**
 * Login page — username/password authentication with token fallback.
 * Calls POST /api/auth/login, stores accessToken in localStorage.
 * @license SPDX-License-Identifier: Apache-2.0
 */
import React, { useState } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { useLanguage } from "../components/LanguageContext";
import { authLogin } from "../api";

export default function Login() {
  const { t } = useLanguage();
  const navigate = useNavigate();
  const location = useLocation();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [token, setToken] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [showAdvanced, setShowAdvanced] = useState(false);

  // Where to redirect after successful login
  const from =
    (location.state as { from?: { pathname: string } })?.from?.pathname ||
    "/app";

  // ── Username / Password login ─────────────────────────────
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (!username.trim()) {
      setError("请输入用户名");
      return;
    }
    if (!password) {
      setError("请输入密码");
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

      navigate(from, { replace: true });
    } catch (err: any) {
      if (err.message === "Failed to fetch" || err.name === "TypeError") {
        setError("网络连接失败，请检查网络后重试");
      } else {
        setError(err.message || "登录失败，请重试");
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
      setError("请输入 Access Token");
      return;
    }

    localStorage.setItem("token", token.trim());
    navigate(from, { replace: true });
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 via-white to-blue-50">
      <div className="w-full max-w-md mx-4">
        {/* ── Brand ──────────────────────────────────────── */}
        <div className="text-center mb-8">
          <div className="w-16 h-16 mx-auto mb-4 rounded-2xl bg-gradient-to-br from-blue-600 to-indigo-700 flex items-center justify-center shadow-lg shadow-blue-200/60">
            <svg
              className="w-8 h-8 text-white"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"
              />
            </svg>
          </div>
          <h1 className="text-2xl font-bold text-gray-800">ECOS Platform</h1>
          <p className="text-gray-500 mt-1 text-sm">
            Enterprise Cognitive Operating System
          </p>
        </div>

        {/* ── Login card ─────────────────────────────────── */}
        <div className="bg-white rounded-2xl shadow-xl shadow-slate-200/60 border border-slate-100 p-8">
          <form onSubmit={handleSubmit} className="space-y-5">
            {/* Username */}
            <div>
              <label
                htmlFor="username"
                className="block text-sm font-medium text-gray-700 mb-1.5"
              >
                用户名
              </label>
              <input
                id="username"
                type="text"
                value={username}
                onChange={(e) => {
                  setUsername(e.target.value);
                  setError("");
                }}
                placeholder="请输入用户名"
                className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-lg text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent focus:bg-white transition"
                autoFocus
                disabled={loading}
              />
            </div>

            {/* Password */}
            <div>
              <label
                htmlFor="password"
                className="block text-sm font-medium text-gray-700 mb-1.5"
              >
                密码
              </label>
              <input
                id="password"
                type="password"
                value={password}
                onChange={(e) => {
                  setPassword(e.target.value);
                  setError("");
                }}
                placeholder="请输入密码"
                className="w-full px-4 py-2.5 bg-gray-50 border border-gray-200 rounded-lg text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent focus:bg-white transition"
                disabled={loading}
              />
            </div>

            {/* Error message */}
            {error && (
              <div className="bg-red-50 border border-red-200 rounded-lg px-4 py-3">
                <p className="text-red-700 text-sm">{error}</p>
              </div>
            )}

            {/* Submit button */}
            <button
              type="submit"
              disabled={loading}
              className="w-full py-2.5 px-4 bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white font-medium rounded-lg shadow-md shadow-blue-200/60 transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed active:scale-[0.98]"
            >
              {loading ? (
                <span className="flex items-center justify-center gap-2">
                  <svg
                    className="animate-spin h-4 w-4"
                    viewBox="0 0 24 24"
                  >
                    <circle
                      className="opacity-25"
                      cx="12"
                      cy="12"
                      r="10"
                      stroke="currentColor"
                      strokeWidth="4"
                      fill="none"
                    />
                    <path
                      className="opacity-75"
                      fill="currentColor"
                      d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
                    />
                  </svg>
                  登录中...
                </span>
              ) : (
                "登录"
              )}
            </button>
          </form>

          {/* ── Advanced: Token login ────────────────────── */}
          <div className="mt-6 pt-4 border-t border-gray-100">
            <button
              type="button"
              onClick={() => setShowAdvanced(!showAdvanced)}
              className="w-full text-xs text-gray-400 hover:text-gray-600 transition flex items-center justify-center gap-1"
            >
              {showAdvanced ? "收起高级选项" : "高级选项"}
              <svg
                className={`w-3 h-3 transition-transform ${
                  showAdvanced ? "rotate-180" : ""
                }`}
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M19 9l-7 7-7-7"
                />
              </svg>
            </button>

            {showAdvanced && (
              <form onSubmit={handleTokenSubmit} className="mt-3 space-y-3">
                <div>
                  <label
                    htmlFor="token"
                    className="block text-sm font-medium text-gray-700 mb-1.5"
                  >
                    Access Token
                  </label>
                  <input
                    id="token"
                    type="password"
                    value={token}
                    onChange={(e) => {
                      setToken(e.target.value);
                      setError("");
                    }}
                    placeholder="粘贴 Access Token..."
                    className="w-full px-4 py-2 bg-gray-50 border border-gray-200 rounded-lg text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-gray-300 focus:border-transparent focus:bg-white transition text-sm"
                  />
                </div>
                <button
                  type="submit"
                  className="w-full py-2 px-4 bg-gray-100 hover:bg-gray-200 text-gray-700 font-medium rounded-lg transition text-sm"
                >
                  Token 登录
                </button>
              </form>
            )}
          </div>
        </div>

        {/* ── Footer ─────────────────────────────────────── */}
        <p className="text-center text-xs text-gray-400 mt-6">
          Secure platform access &bull; All activity is audited
        </p>
      </div>
    </div>
  );
}
