/**
 * NoAccess — dedicated 403 "you don't have permission" page.
 * Reached when: (a) RequireAuth 探测到 403，(b) apiFetch 命中 403（token 有效、
 * 资源越权 —— token 绝不清除），或 (c) RequireAuth fail-closed 的「安全服务不可用」
 * 分支（`location.state.denyReason === "unavailable"`，5xx/传输失败）。
 *
 * Listens for the `ecos-no-access` CustomEvent dispatched by api.ts so
 * any in-app operation that receives a 403 can route the user here without
 * logging them out.
 *
 * @license Apache-2.0
 */

import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ShieldAlert, ShieldQuestion, ArrowLeft, Home, RefreshCw } from 'lucide-react';
import { useLanguage } from '../components/LanguageContext';
import { useTheme } from '../components/ThemeContext';
import { NO_ACCESS_EVENT } from '../api';
import { performLogout } from '../services/auth';

/** RequireAuth 通过 location.state 传来的拒绝成因（只允许可克隆的字符串字段）。 */
interface NoAccessLocationState {
  denyReason?: "forbidden" | "unavailable";
  blockedPath?: string;
  path?: string;
}

export default function NoAccess() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();
  const state = (location.state ?? {}) as NoAccessLocationState;
  const unavailable = state.denyReason === "unavailable";
  const blockedPath = typeof state.blockedPath === "string" && state.blockedPath.startsWith("/")
    ? state.blockedPath
    : "";
  const [affectedPath, setAffectedPath] = useState<string | null>(
    typeof state.path === "string" ? state.path : null,
  );

  // Listen for 403 events dispatched by api.ts — show affected path.
  useEffect(() => {
    const onNoAccess = (e: Event) => {
      const detail = (e as CustomEvent<{ path?: string }>).detail || {};
      if (detail.path) setAffectedPath(detail.path);
    };
    window.addEventListener(NO_ACCESS_EVENT, onNoAccess);
    return () => window.removeEventListener(NO_ACCESS_EVENT, onNoAccess);
  }, []);

  const goBack = () => (window.history.length > 1 ? window.history.back() : (window.location.hash = "#/"));
  // 退出登录一律走 services/auth 单源（报告 §三 S5/S6：此处曾手删第三套 key）
  const goLogin = () => performLogout();
  // 重试 = 回到被拦路径重新触发 RequireAuth 探测（fail-closed 后允许恢复，但绝不放行未校验内容）
  const goRetry = () => navigate(blockedPath || "/", { replace: true });

  return (
    <div className={`min-h-screen flex items-center justify-center p-6 ${styles.appBg}`}>
      <div className={`w-full max-w-md ${styles.cardBg} border ${styles.cardBorder} rounded-xl p-8 space-y-5 flex flex-col items-center text-center`}>
        <div className={`p-4 rounded-2xl ${unavailable ? styles.warningBg : styles.dangerBg}`}>
          {unavailable
            ? <ShieldQuestion size={36} className={styles.warningText} />
            : <ShieldAlert size={36} className={styles.dangerText} />}
        </div>
        <div className="space-y-1.5">
          <h1 className={`text-lg font-extrabold ${styles.cardText}`}>
            {unavailable ? t('noAccess.unavailable.title') : t('noAccess.title')}
          </h1>
          <p className={`text-xs ${styles.cardTextMuted}`}>
            {unavailable ? t('noAccess.unavailable.message') : t('noAccess.message')}
          </p>
        </div>

        {blockedPath && (
          <p className={`text-[10px] ${styles.cardTextMuted} font-mono`}>
            {t('noAccess.affected', { path: blockedPath })}
          </p>
        )}

        {!affectedPath && blockedPath && (
          <div className="flex items-center gap-2.5 w-full pt-1">
            <button
              type="button"
              onClick={goRetry}
              className={`flex items-center justify-center gap-1.5 flex-1 px-3.5 py-2.5 rounded-lg text-xs font-bold cursor-pointer ${styles.accentBg} ${styles.accentSolidText} hover:opacity-90`}
            >
              <RefreshCw size={13} /> {t('noAccess.retry')}
            </button>
          </div>
        )}

        {affectedPath && (
          <p className={`text-[10px] ${styles.cardTextMuted} font-mono`}>
            {t('noAccess.affected', { path: affectedPath })}
          </p>
        )}

        <div className="flex items-center gap-2.5 w-full pt-2">
          <button
            type="button"
            onClick={goBack}
            className={`flex items-center justify-center gap-1.5 flex-1 px-3.5 py-2.5 rounded-lg text-xs font-bold cursor-pointer ${styles.cardBg} border ${styles.cardBorder} ${styles.cardText} hover:opacity-90`}
          >
            <ArrowLeft size={13} /> {t('noAccess.back')}
          </button>
          <button
            type="button"
            onClick={() => (window.location.hash = "#/")}
            className={`flex items-center justify-center gap-1.5 flex-1 px-3.5 py-2.5 rounded-lg text-xs font-bold cursor-pointer ${styles.accentBg} ${styles.accentSolidText} hover:opacity-90`}
          >
            <Home size={13} /> {t('noAccess.home')}
          </button>
        </div>

        <button
          type="button"
          onClick={goLogin}
          className={`mt-1 text-[10px] cursor-pointer ${styles.accentText}`}
        >
          {t('noAccess.switch')}
        </button>
      </div>
    </div>
  );
}
