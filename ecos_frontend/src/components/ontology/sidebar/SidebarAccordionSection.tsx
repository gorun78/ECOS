/**
 * SidebarAccordionSection — 本体工作台侧栏折叠分节外壳
 *
 * 从 `components/ontology/Sidebar.tsx` 的 7 个同构分节（OBJECT/LINK/ACTION/FUNCTION/
 * INTERFACE/SHARED PROPERTY/DATASETS）机械抽取的公共骨架；
 * 展开图标、header className、计数与 `px-2 space-y-0.5` 列表容器与原实现逐字一致。
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { useTheme } from '../../ThemeContext';
import DynamicIcon from './SidebarDynamicIcon';

interface SidebarAccordionSectionProps {
  sectionKey: string;
  label: string;
  count: number;
  /** 分节外层容器 className，按源码字面量原样传入 */
  outerClassName: string;
  expanded: Record<string, boolean>;
  onToggle: (key: string) => void;
  /** 行渲染函数：仅在分节展开时调用（与源码 `expanded.x && (...)` 的短路语义一致） */
  renderRows: () => React.ReactNode;
  /** 无假真理：分节标题右侧标记（如 demo 数据徽标），由调用方传入 */
  headerBadge?: React.ReactNode;
}

export default function SidebarAccordionSection({
  sectionKey,
  label,
  count,
  outerClassName,
  expanded,
  onToggle,
  renderRows,
  headerBadge,
}: SidebarAccordionSectionProps) {
  const { styles } = useTheme();

  return (
    <div className={outerClassName}>
      <button type="button"
        onClick={() => onToggle(sectionKey)}
        className={`w-full py-1.5 px-3 flex items-center justify-between ${styles.muted} hover:${styles.cardText} font-semibold uppercase tracking-wider text-[10px]`}
      >
        <div className="flex items-center gap-1 min-w-0">
          <DynamicIcon name={expanded[sectionKey] ? "ChevronDown" : "ChevronRight"} size={12} />
          <span className="truncate">{label}</span>
          {headerBadge}
        </div>
        <span className="shrink-0">{count}</span>
      </button>
      {expanded[sectionKey] && (
        <div className="px-2 space-y-0.5">
          {renderRows()}
        </div>
      )}
    </div>
  );
}
