/**
 * PMO-D Batch 1 — F4 ExtractionPage（骨架）。
 *
 * Batch 2 实体现：
 * - 左栏 结构化 I→K（左 1.25fr）：本体契约 select + 3 checkbox + 提交
 * - 右栏 文档导入 Document→K（右 0.75fr）：uploadDocumentChunked + 4 badge
 * - 下方 3 KPI 卡片：实体 / 关系 / 候选知识
 *
 * 当前骨架复现「 DW 层契约源 + 文档导入 」提示，真实按 PRD §3.2 F4 实现。
 */
import React from 'react';
import { ArrowDownUp, FileUp, Boxes } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';

export default function ExtractionPage() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className="p-2" style={{ color: styles.cardText }}>
      {/* 骨架 banner — Batch 2 实体落地时替换为双栏 1.25fr/.75fr */}
      <div
        className="rounded-md border p-6 flex flex-col items-center justify-center gap-2 min-h-64"
        style={{ borderColor: styles.cardBorder, background: styles.cardBg }}
      >
        <ArrowDownUp className="w-8 h-8 opacity-40" style={{ color: styles.cardTextMuted }} />
        <div className="text-sm font-semibold">{t('knowledge.nav.page_extract')}</div>
        <div className="text-xs opacity-60 text-center max-w-md">
          {t('knowledge.extract.placeholder_structured_vs_document')}
        </div>
      </div>

      {/* 占位双栏 — 结构化 / 文档导入（i18n key 已预建） */}
      <div className="mt-4 grid grid-cols-1 md:grid-cols-[1.25fr_0.75fr] gap-4">
        <div
          className="rounded-md border p-4"
          style={{ borderColor: styles.cardBorder, background: styles.cardBg }}
        >
          <div className="flex items-center gap-2 text-sm font-medium mb-2">
            <Boxes className="w-4 h-4 opacity-50" />
            <span>{t('knowledge.extract.structured')}</span>
          </div>
          <span
            className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-mono"
            style={{ background: styles.badgeBg, color: styles.badgeText }}
          >
            I → K
          </span>
        </div>

        <div
          className="rounded-md border p-4"
          style={{ borderColor: styles.cardBorder, background: styles.cardBg }}
        >
          <div className="flex items-center gap-2 text-sm font-medium mb-2">
            <FileUp className="w-4 h-4 opacity-50" />
            <span>{t('knowledge.extract.document')}</span>
          </div>
          <span
            className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-mono"
            style={{ background: styles.badgeBg, color: styles.badgeText }}
          >
            Document → K
          </span>
        </div>
      </div>
    </div>
  );
}
