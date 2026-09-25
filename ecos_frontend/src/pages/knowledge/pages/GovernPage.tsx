/**
 * PMO-D Batch 2 — F7 GovernPage（主骨架 / PRD §3.2 F7 / 铁律 §4.6 ≤ 800 行）.
 *
 * 3 区 grid（grid-rows-[1fr_1fr_1.25fr] gap-4）：
 *   上区 — AuditFeedCard（原 ExtractionReviewTab 主逻辑 + lifecycle pipeline）
 *   中区 — QualityMetricsTable（原 ComplianceRuleTab 部分：3 指标 + 状态）
 *   下区（1fr/1fr 2 列）— RuleRepoTable（左） + EvalRunPanel（右）
 *
 * 4 子组件已拆：
 *   - components/govern/AuditFeedCard.tsx
 *   - components/govern/QualityMetricsTable.tsx
 *   - components/govern/RuleRepoTable.tsx
 *   - components/govern/EvalRunPanel.tsx
 *
 * 本主文件仅做 layout + state 共享（不在这里写业务逻辑，避免与 §4.6 冲突）
 * 主题 §4.1：0 硬编码色值
 * i18n §4.3：0 硬编码中文
 */
import { ShieldCheck } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import AuditFeedCard from './components/govern/AuditFeedCard';
import QualityMetricsTable from './components/govern/QualityMetricsTable';
import RuleRepoTable from './components/govern/RuleRepoTable';
import EvalRunPanel from './components/govern/EvalRunPanel';

export default function GovernPage() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className="p-2 flex flex-col gap-3" style={{ color: styles.cardText }}>
      {/* 顶栏：标题 */}
      <div className="flex items-center gap-2 px-3 py-2 rounded-md border"
           style={{ borderColor: styles.cardBorder, background: styles.cardBg }}>
        <ShieldCheck className="w-4 h-4" style={{ color: styles.accentText }} />
        <span className="text-base font-bold flex items-center gap-2">{t('knowledge.nav.page_govern')}</span>
        <span className="text-[10px] font-mono" style={{ color: styles.muted }}>
          {t('knowledge.govern.page_subtitle')}
        </span>
      </div>

      {/* 3 区 grid — 上 1fr / 中 1fr / 下 1.25fr（PRD F7：grid grid-rows-[1fr_1fr_1.25fr]） */}
      <div className="grid grid-rows-[1fr_1fr_1.25fr] gap-3" style={{ minHeight: '70vh' }}>
        {/* 上区 — AI 候选审核 + lifecycle pipeline */}
        <AuditFeedCard />
        {/* 中区 — 质量监控 3 指标 */}
        <QualityMetricsTable />

        {/* 下区 — 1fr / 1fr 2 列：规则仓库（左） + 评测运行（右） */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-3 min-h-0">
          <RuleRepoTable />
          <EvalRunPanel />
        </div>
      </div>
    </div>
  );
}
