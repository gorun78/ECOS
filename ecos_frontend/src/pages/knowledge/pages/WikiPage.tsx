/**
 * PMO-D Batch 1 — F6 WikiPage（骨架）。
 *
 * Batch 2 实体现：
 * - 左栏（知识空间 tree, 220px）：一级 5 节点 + 二级 md 文件列表
 * - 中栏（md 预览，只读）：状态 badge + 版本 + <Markdown> body（react-markdown）
 * - 右栏（Knowledge Context, 280px）：语义实体 chips + 相关知识 + 图谱关系
 * - 顶栏：「＋ 上传 .md」按钮
 *
 * 批量 md 导入走 `POST /api/v1/knowledge/docs/ingest` (Content-Type: text/markdown, F6)。
 * 当前骨架为三栏占位，真实渲染在 Batch 2 落地。
 */
import React from 'react';
import { BookOpen, FileText } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';

export default function WikiPage() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className="p-2" style={{ color: styles.cardText }}>
      <div
        className="rounded-md border p-2"
        style={{ borderColor: styles.cardBorder, background: styles.cardBg }}
      >
        {/* 顶栏：上传 .md 按钮（Batch 2 接入 uploadDocumentChunked + Content-Type: text/markdown） */}
        <div
          className="flex items-center justify-between px-3 py-2 border-b"
          style={{ borderColor: styles.cardBorder }}
        >
          <div className="flex items-center gap-2 text-sm font-semibold">
            <BookOpen className="w-4 h-4" />
            <span>{t('knowledge.nav.page_wiki')}</span>
          </div>
          <button
            type="button"
            className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md text-xs border cursor-default"
            style={{ borderColor: styles.cardBorder, color: styles.cardTextMuted, opacity: 0.6 }}
            title={t('knowledge.wiki.upload_md_button')}
          >
            <FileText className="w-3.5 h-3.5" />
            .md
          </button>
        </div>

        {/* 三栏骨架 — Batch 2 实体落地 */}
        <div className="grid grid-cols-1 md:grid-cols-[220px_1fr_280px] gap-0 divide-x" style={{ borderColor: styles.cardBorder }}>
          <div className="p-4">
            <div className="text-xs font-medium opacity-60">
              {t('knowledge.wiki.left_tree')}
            </div>
            <div className="mt-2 text-[11px] opacity-40">
              {t('knowledge.wiki.left_tree_placeholder')}
            </div>
          </div>
          <div className="p-4">
            <div className="text-xs font-medium opacity-60">
              {t('knowledge.wiki.center_preview')}
            </div>
            <div className="mt-2 text-[11px] opacity-40">
              {t('knowledge.wiki.center_preview_placeholder')}
            </div>
          </div>
          <div className="p-4">
            <div className="text-xs font-medium opacity-60">
              {t('knowledge.wiki.right_context')}
            </div>
            <div className="mt-2 text-[11px] opacity-40">
              {t('knowledge.wiki.right_context_placeholder')}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
