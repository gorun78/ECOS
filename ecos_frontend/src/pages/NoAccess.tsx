/**
 * NoAccess — dedicated 403 "you don't have permission" page.
 * Reached when: (a) RequireAuth.findRoles() fails, or (b) apiFetch hits 403
 * (token VALID, resource forbidden — token must NOT be cleared).
 *
 * Listens for the `ecos-no-access` CustomEvent dispatched by api.ts so
 * any in-app operation that receives a 403 can route the user here without
 * logging them out.
 *
 * @license Apache-2.0
 */

import { useEffect, useState } from 'react';
import { ShieldAlert, ArrowLeft, Home } from 'lucide-react';
import { useLanguage } from '../components/LanguageContext';
import { useTheme } from '../components/ThemeContext';
import { NO_ACCESS_EVENT } from '../api';

export default function NoAccess() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [affectedPath, setAffectedPath] = useState<string | null>(null);

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
  const goLogin = () => {
    ["token", "username", "roles"].forEach((k) => localStorage.removeItem(k));
    window.location.hash = "#/login";
  };

  return (
    <div className={`min-h-screen flex items-center justify-center p-6 ${styles.appBg}`}>
      <div className={`w-full max-w-md ${styles.cardBg} border ${styles.cardBorder} rounded-xl p-8 space-y-5 flex flex-col items-center text-center`}>
        <div className={`p-4 rounded-2xl ${styles.warningBg}`}>
          <ShieldAlert size={36} className={styles.warningText} />
        </div>
        <div className="space-y-1.5">
          <h1 className={`text-lg font-extrabold ${styles.cardText}`}>{t('noAccess.title')}</h1>
          <p className={`text-xs ${styles.cardTextMuted}`}>{t('noAccess.message')}</p>
        </div>

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
            className={`flex items-center justify-center gap-1.5 flex-1 px-3.5 py-2.5 rounded-lg text-xs font-bold cursor-pointer ${styles.accentBg} text-white hover:opacity-90`}
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
