/**
 * PMO-D Batch 1 — F7 GovernPage（骨架）。
 *
 * Batch 2 实体现（铁律 §4.6 ≤ 800 行）：
 * - 上区 — AI 候选审核（AuditFeedCard，原 ExtractionReviewTab 主逻辑）
 * - 中区 — 质量监控（QualityMetricsTable，原 ComplianceRuleTab 指标子集）
 * - 下区 — 治理策略 + 评测（RuleRepoTable + EvalRunPanel 1fr/1fr）
 *
 * 4 子组件拆分在 Batch 2 落地；本骨架占 3 区 layout。
 */
import type { LucideIcon } from 'lucide-react';
import { ShieldCheck, ClipboardCheck, Gauge, Scale } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';

export default function GovernPage() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const renderSection = (PageIcon: LucideIcon, titleKey: string) => (
    <div
      className="rounded-md border p-4 flex flex-col items-center justify-center gap-2 min-h-32"
      style={{ borderColor: styles.cardBorder, background: styles.cardBg }}
    >
      <PageIcon className="w-6 h-6 opacity-40" style={{ color: styles.cardTextMuted }} />
      <div className="text-sm font-semibold">{t(titleKey)}</div>
    </div>
  );

  return (
    <div className="p-2 flex flex-col gap-3" style={{ color: styles.cardText }}>
      {/* 上区 */}
      {renderSection(ClipboardCheck, 'knowledge.govern.audit_feed')}
      {/* 中区 */}
      {renderSection(Gauge, 'knowledge.govern.quality_metrics')}
      {/* 下区 1fr/1fr */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
        {renderSection(Scale, 'knowledge.govern.rule_repo')}
        {renderSection(ShieldCheck, 'knowledge.govern.eval_run')}
      </div>
    </div>
  );
}
