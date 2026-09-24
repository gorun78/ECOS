/**
 * PMO-D Batch 1 — F2 OverviewPage（骨架）。
 *
 * 当前 Batch 1 仅复现现有 OverviewDashboard 作为 base，Batch 2 按 PRD §3.2 F2
 * 落地完整 6 KPI + 5 步流水线 + 3 待办 + 最近资产 5 列表 + 「查看任务」按钮。
 *
 * - 主题守护 §4.1：0 硬编码色值，全 useTheme().styles
 * - i18n：t('knowledge.nav.page_overview') 等已写入 locales/knowledge/*
 */
import OverviewDashboard from '../tabs/OverviewDashboard';

export default function OverviewPage() {
  return <OverviewDashboard />;
}
