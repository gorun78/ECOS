/**
 * DemoBadge (aiworkbench 内联版) — 「能力未上线」徽标
 *
 * 检视报告 §4.5 铁律：后端无对应通道（ghost 端点 / 零端点）时，控件必须禁用并
 * 显式标注能力未上线，禁止 setInterval 演出进度后弹成功 toast。
 * 与 `components/common/status/DemoBadge`（预测场景演示态单源徽标）语义不同，
 * 本组件仅做静态标注：不判定 forecastScenarioMode、不发请求。
 *
 * 规范：文案走 t()（§六）；色彩走主题 token（§七）；图标仅 lucide-react。
 * @license Apache-2.0
 */
import React from 'react';
import { FlaskConical } from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';

export default function DemoBadge({ className = '' }: { className?: string }) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  return (
    <span
      className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded font-bold text-[9px] align-middle ${styles.badgeBg} ${styles.badgeText} ${className}`}
      title={t('aiworkbench.common.capabilityOfflineTitle')}
    >
      <FlaskConical size={10} aria-hidden />
      <span>{t('aiworkbench.common.capabilityOffline')}</span>
    </span>
  );
}
