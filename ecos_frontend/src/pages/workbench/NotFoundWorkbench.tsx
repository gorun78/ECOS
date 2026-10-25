/**
 * 工作台 404 兜底（F07-06）——main.tsx `*` 路由 fallback 指向本页。
 *
 * <p>文案全走 t('workspace.404.*')，颜色全走 useTheme 语义 token，
 * 仅使用 lucide-react 图标；移动端单列堆叠。</p>
 */
import React from 'react';
import { Link } from 'react-router-dom';
import {
  Briefcase,
  Compass,
  LayoutDashboard
} from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import { useMediaQuery } from '../../hooks/useMediaQuery';

export default function NotFoundWorkbench() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const isMobile = useMediaQuery('(max-width: 767px)');

  return (
    <div className={`flex-1 flex items-center justify-center ${styles.appBg} ${styles.appText} p-4`}>
      <div
        className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-6 text-center w-full ${
          isMobile ? 'max-w-sm' : 'max-w-md'
        }`}
      >
        <div className={`mx-auto mb-4 h-14 w-14 rounded-full flex items-center justify-center ${styles.accentBg} ${styles.accentText}`}>
          <Compass size={28} />
        </div>
        <p className={`text-[11px] font-mono ${styles.cardTextMuted} mb-1`}>{t('workspace.404.status')}</p>
        <h1 className={`text-xl font-extrabold ${styles.cardText}`}>{t('workspace.404.title')}</h1>
        <p className={`text-xs ${styles.cardTextMuted} mt-2 leading-relaxed`}>{t('workspace.404.subtitle')}</p>

        <div className={`mt-6 flex gap-2 justify-center flex-wrap`}>
          <Link
            to="/workbench"
            className={`inline-flex items-center gap-1.5 px-3 py-2 rounded border text-xs font-bold cursor-pointer ${styles.accentBg} ${styles.accentBorder} ${styles.cardText}`}
          >
            <LayoutDashboard size={13} />
            {t('workspace.404.go_home')}
          </Link>
          <Link
            to="/project_workbench"
            className={`inline-flex items-center gap-1.5 px-3 py-2 rounded border text-xs font-bold cursor-pointer ${styles.cardBg} ${styles.inputBorder} ${styles.inputText}`}
          >
            <Briefcase size={13} />
            {t('workspace.404.go_scenario')}
          </Link>
        </div>
      </div>
    </div>
  );
}
