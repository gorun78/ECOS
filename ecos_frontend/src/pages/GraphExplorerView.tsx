/**
 * GraphExplorerView — 知识图谱探索独立页面
 * 将 GraphExplorerTab 包装为全高容器，使用主题感知样式。
 *
 * @license Apache-2.0
 */

import React from 'react';
import { useTheme } from '../components/ThemeContext';
import GraphExplorerTab from './knowledge/tabs/GraphExplorerTab';

export default function GraphExplorerView() {
  const { styles } = useTheme();

  return (
    <div className={`h-full w-full overflow-auto ${styles.appBg} ${styles.cardText}`}>
      <GraphExplorerTab />
    </div>
  );
}
