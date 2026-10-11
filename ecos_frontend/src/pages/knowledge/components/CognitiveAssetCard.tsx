/**
 * CognitiveAssetCard — 认知资产只读卡片（议题1）。
 *
 * 主题守护：0 硬编码色（全 useTheme().styles）。图标仅 lucide-react。文案全 t()。
 * 只读：无任何编辑/导入/发布按钮（§8.4 C/K 互斥守护）。
 */
import React from 'react';
import { Lightbulb, Scale, Brain, Eye } from 'lucide-react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import {
  assetStatusLabelKey,
  assetStatusTone,
  probKindLabelKey,
  isUncalibratedKind,
  type CognitiveAssetRow,
} from '../services/cognitiveAsset';

const CLASS_ICON: Record<string, React.ComponentType<{ className?: string }>> = {
  HYPOTHESIS: Lightbulb,
  BELIEF: Scale,
  MODEL: Brain,
};

export interface CognitiveAssetCardProps {
  row: CognitiveAssetRow;
  active: boolean;
  onSelect: (assetId: string) => void;
}

export default function CognitiveAssetCard({ row, active, onSelect }: CognitiveAssetCardProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const Icon = CLASS_ICON[row.assetClass] ?? Brain;
  const tone = assetStatusTone(row.status);
  const toneColor =
    tone === 'positive' ? styles.successText : tone === 'warning' ? styles.warningText : styles.cardTextMuted;

  return (
    <button
      type="button"
      onClick={() => onSelect(row.assetId)}
      className="w-full text-left rounded-md border p-3 flex flex-col gap-2 transition-colors cursor-pointer"
      style={{
        borderColor: active ? styles.accentBorder : styles.cardBorder,
        background: active ? styles.badgeBg : styles.cardBg,
        color: styles.cardText,
      }}
      aria-label={row.label}
    >
      <div className="flex items-start gap-2">
        <Icon className="w-4 h-4 mt-0.5 flex-shrink-0 opacity-70" />
        <div className="min-w-0 flex-1">
          <div className="text-xs font-medium truncate">{row.label}</div>
          {row.detail && <div className="text-[11px] opacity-60 truncate mt-0.5">{row.detail}</div>}
        </div>
      </div>

      <div className="flex items-center gap-1.5 flex-wrap">
        <span className="px-1.5 py-0.5 rounded text-[10px] border" style={{ borderColor: styles.cardBorder, color: styles.badgeText }}>
          {t(`knowledge.cognitiveAsset.class.${row.assetClass}`) || row.assetClass}
        </span>
        {row.status && (
          <span className="px-1.5 py-0.5 rounded text-[10px] border" style={{ borderColor: styles.cardBorder, color: toneColor }}>
            {assetStatusLabelKey(row.status) ? t(assetStatusLabelKey(row.status) as string) || row.status : row.status}
          </span>
        )}
        {row.assetClass === 'BELIEF' && row.probability !== null && (
          <span className="px-1.5 py-0.5 rounded text-[10px]" style={{ background: styles.badgeBg, color: styles.badgeText }}>
            {row.probability.toFixed(2)}
            {isUncalibratedKind(row.probabilityKind) && <span className="ml-1 opacity-70">· {t('knowledge.cognitiveAsset.uncalibrated_tag')}</span>}
          </span>
        )}
        {row.probabilityKind && (
          <span className="text-[10px] opacity-50 truncate max-w-[140px]">
            {probKindLabelKey(row.probabilityKind) ? t(probKindLabelKey(row.probabilityKind) as string) || row.probabilityKind : row.probabilityKind}
          </span>
        )}
      </div>

      <div className="flex items-center justify-between pt-1">
        <span className="text-[10px] opacity-50">
          {row.updatedAt ? new Date(row.updatedAt).toLocaleString() : t('knowledge.cognitiveAsset.no_updated')}
        </span>
        <span className="inline-flex items-center gap-1 text-[10px] opacity-60">
          <Eye className="w-3 h-3" />
          {t('knowledge.cognitiveAsset.action_resolve')}
        </span>
      </div>
    </button>
  );
}
