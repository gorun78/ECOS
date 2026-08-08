/**
 * AutoDiscoverPreview — 自动发现候选实体预览组件
 *
 * 列表展示发现的表/视图：名称、字段数、置信度%、资源类型
 * 支持勾选/取消勾选，用于 AutoDiscoverPanel 第二步
 *
 * ≤250 行
 *
 * @license Apache-2.0
 */

import React from 'react';
import { Table, Check, Database } from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import type { AutoDiscoverEntityPreview } from '../../services/ontologyApi';

// ── 组件接口 ────────────────────────────────────────────────

interface AutoDiscoverPreviewProps {
  /** 候选实体列表 */
  candidates: AutoDiscoverEntityPreview[];
  /** 已选中的资源名称集合 */
  selectedNames: Set<string>;
  /** 切换选中 */
  onToggle: (name: string) => void;
  /** 全选/取消全选 */
  onToggleAll: (selectAll: boolean) => void;
  /** 是否加载中 */
  loading?: boolean;
}

// ── 置信度颜色映射 ──────────────────────────────────────────

function getConfidenceColor(confidence: number): string {
  if (confidence >= 0.8) return 'text-emerald-400 bg-emerald-500/10';
  if (confidence >= 0.6) return 'text-amber-400 bg-amber-500/10';
  return 'text-slate-400 bg-slate-500/10';
}

function getConfidenceLabel(c: number): string {
  return `${Math.round(c * 100)}%`;
}

// ── 主组件 ──────────────────────────────────────────────────

export default function AutoDiscoverPreview({
  candidates,
  selectedNames,
  onToggle,
  onToggleAll,
  loading = false,
}: AutoDiscoverPreviewProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const allSelected = candidates.length > 0 && candidates.every((c) => selectedNames.has(c.resourceName));
  const someSelected = candidates.some((c) => selectedNames.has(c.resourceName));

  // ── 加载态 ──
  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <Database size={24} className="animate-pulse text-slate-600" />
        <span className={`ml-3 text-sm ${styles.muted}`}>
          {t('ontology.autoDiscover.discovering')}
        </span>
      </div>
    );
  }

  // ── 空状态 ──
  if (candidates.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12">
        <Database size={32} className="mb-3 opacity-20 text-slate-500" />
        <p className={`text-sm ${styles.muted}`}>
          {t('ontology.autoDiscover.noCandidates')}
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {/* 标题行 + 全选 */}
      <div className="flex items-center justify-between">
        <span className={`text-xs font-medium ${styles.cardText}`}>
          {t('ontology.autoDiscover.candidateEntities')}
          <span className={`ml-1.5 ${styles.muted}`}>
            ({candidates.length})
          </span>
        </span>
        <button
          onClick={() => onToggleAll(!allSelected)}
          className={`text-[10px] px-2 py-1 rounded transition ${
            someSelected
              ? 'text-indigo-400 hover:text-indigo-300'
              : `${styles.muted} hover:text-slate-400`
          }`}
        >
          {allSelected
            ? t('ontology.autoDiscover.deselectAll')
            : t('ontology.autoDiscover.selectAll')}
        </button>
      </div>

      {/* 候选列表 */}
      <div className={`max-h-[360px] overflow-y-auto space-y-1.5 rounded-lg border ${styles.cardBorder} ${styles.appBg}`}>
        {candidates.map((cand) => {
          const isSelected = selectedNames.has(cand.resourceName);
          const confColor = getConfidenceColor(cand.confidence);

          return (
            <button
              key={cand.resourceName}
              onClick={() => onToggle(cand.resourceName)}
              className={`w-full text-left flex items-center gap-3 px-3 py-2.5 border-b ${styles.cardBorder}/50 last:border-b-0 transition ${
                isSelected
                  ? 'bg-indigo-500/8'
                  : `hover:${styles.cardBg}/[0.03]`
              }`}
            >
              {/* 勾选框 */}
              <div
                className={`shrink-0 w-4 h-4 rounded border flex items-center justify-center transition ${
                  isSelected
                    ? 'bg-indigo-500 border-indigo-500'
                    : `border-slate-600 bg-transparent`
                }`}
              >
                {isSelected && <Check size={10} className="text-white" />}
              </div>

              {/* 图标 */}
              <div className={`p-1.5 rounded shrink-0 ${isSelected ? 'bg-indigo-500/15' : 'bg-slate-500/10'}`}>
                <Table size={14} className={isSelected ? 'text-indigo-400' : 'text-slate-500'} />
              </div>

              {/* 信息 */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <span className={`text-xs font-medium truncate ${styles.cardText}`}>
                    {cand.resourceName}
                  </span>
                  <span className="text-[9px] px-1.5 py-0.5 rounded bg-slate-700/40 text-slate-500 shrink-0">
                    {cand.resourceType}
                  </span>
                </div>
                {cand.schemaName && (
                  <p className={`text-[10px] ${styles.muted} mt-0.5`}>
                    {cand.schemaName}
                  </p>
                )}
              </div>

              {/* 字段数 */}
              <span className={`text-[10px] ${styles.muted} shrink-0`}>
                {t('ontology.autoDiscover.fieldCount').replace('{count}', String(cand.fieldCount))}
              </span>

              {/* 置信度 */}
              <span className={`text-[10px] px-2 py-0.5 rounded-full font-medium shrink-0 ${confColor}`}>
                {getConfidenceLabel(cand.confidence)}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
