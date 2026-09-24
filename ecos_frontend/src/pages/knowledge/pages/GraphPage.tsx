/**
 * PMO-D Batch 1 — F5 GraphPage（骨架）。
 *
 * Batch 2 实体现：
 * - 顶部搜索栏：输入 + 实体/关系 select
 * - 图谱画布（深色 #0f172a）：复用 GraphCanvas 组件
 * - 左工具栏：搜索 / 路径 / domain select / category checkbox (后端下沉)
 * - 右详情面板：节点属性 / 关系 / 操作
 * - 底栏：实体 N · 关系 M · 当前视图 K 节点
 *
 * 当前骨架复现「categoryIds 透传 fetchGraph + 复用 GraphExplorerTab 中心」,
 * 真实按 PRD §3.2 F5 实现。
 * ※ 主题守护：0 硬编码色值（§4.1）— 渲染 GraphCanvas 时由 GraphExplorerTab 自身主题。
 */
import React from 'react';
import { Network } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';

export default function GraphPage() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className="p-2" style={{ color: styles.cardText }}>
      <div
        className="rounded-md border p-8 flex flex-col items-center justify-center gap-2 min-h-96"
        style={{ borderColor: styles.cardBorder, background: styles.cardBg }}
      >
        <Network className="w-10 h-10 opacity-40" style={{ color: styles.cardTextMuted }} />
        <div className="text-sm font-semibold">{t('knowledge.nav.page_graph')}</div>
        <div className="text-xs opacity-60 text-center max-w-md">
          {t('knowledge.graph.placeholder_canvas_and_categories_backended')}
        </div>
      </div>
    </div>
  );
}
