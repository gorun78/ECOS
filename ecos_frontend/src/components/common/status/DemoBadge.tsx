/**
 * DemoBadge — 演示数据徽标（详设-09 R-63 ② / PRD-09 §八 演练⑥ 验收项）
 *
 * <p>判定条件（单源于 `services/forecastScenarioMode.ts`）：真实四源（预算 / 实际账 /
 * 计量 / 关账）未在本场景运行面串通前**恒显示**；未打徽标而展示演示数据 = 假验收
 * （架构铁律 v2.0 :14）。四源结案（H-1/H-5 上线 + 演练 2/3 金标准归档）后翻转标记，
 * 本徽标自动退役。</p>
 *
 * <p>规范合规：文案走 `t()`（§六）；色彩走 `Badge` 语义 token（§七，无字面量色）；
 * 图标仅 `lucide-react`；纯展示组件，不发请求。</p>
 *
 * @license Apache-2.0
 */

import React from 'react';
import { FlaskConical } from 'lucide-react';
import { useLanguage } from '../../LanguageContext';
import Badge from '../Badge';
import { isForecastDemoMode } from '../../../services/forecastScenarioMode';

export interface DemoBadgeProps {
  /** 额外布局类名（仅排版，不含颜色） */
  className?: string;
  /**
   * 强制显示（默认跟随 `isForecastDemoMode()`）。
   * 仅供测试/演示快照使用；业务面一律留空走单源判定，禁各处自行三元判断漂移。
   */
  force?: boolean;
}

/** 演示数据徽标：demo 态 ⇒ 渲染；四源已通 ⇒ 返回 null（自动退役）。 */
export default function DemoBadge({ className = '', force }: DemoBadgeProps) {
  const { t } = useLanguage();
  const demo = force ?? isForecastDemoMode();
  if (!demo) return null;
  return (
    <Badge variant="warning" className={className} >
      <FlaskConical size={11} aria-hidden />
      <span data-testid="demo-badge-label">{t('common.status.demo_badge')}</span>
    </Badge>
  );
}
