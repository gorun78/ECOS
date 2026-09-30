/**
 * CreateElementMenu — 侧栏底部「新建元素」动作条 + 元素类型下拉
 *
 * 从 `components/ontology/Sidebar.tsx` 机械抽取（H6-T4 组件行数治理）：
 * 按钮顺序、图标配色与下拉定位 className 与原实现逐字一致。
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { Box, Code, GitMerge, Layers, PlusCircle, Tag, Zap } from 'lucide-react';
import { useLanguage } from '../../LanguageContext';
import { useTheme } from '../../ThemeContext';
import DynamicIcon from './SidebarDynamicIcon';

interface CreateElementMenuProps {
  showCreateDropdown: boolean;
  setShowCreateDropdown: (open: boolean) => void;
  onCreateNew: (type: 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'function') => void;
}

export default function CreateElementMenu({
  showCreateDropdown,
  setShowCreateDropdown,
  onCreateNew,
}: CreateElementMenuProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  // Bottom Action bar — T4: 替换原来的 白底/黑底 硬编码 → theme tokens
  return (
    <div className={`p-3 border-t ${styles.appBorder} ${styles.cardBg} relative`}>
      <button
        onClick={() => setShowCreateDropdown(!showCreateDropdown)}
        className={`w-full ${styles.accentBg} text-white ${styles.accentHover} font-medium py-2 px-3 rounded-lg flex items-center justify-center gap-1.5 transition-colors shadow-xs`}
      >
        <PlusCircle size={14} />
        <span>{t('ow.btn.createNewElement')}</span>
        <DynamicIcon name={showCreateDropdown ? "ChevronDown" : "ChevronUp"} size={12} />
      </button>

      {/* Create Dropdown */}
      {showCreateDropdown && (
        <div className={`absolute bottom-14 left-3 right-3 ${styles.cardBg} border ${styles.appBorder} rounded-lg shadow-lg py-1 z-30 divide-y ${styles.divider}`}>
          <button
            onClick={() => {
              onCreateNew('object');
              setShowCreateDropdown(false);
            }}
            className={`w-full text-left px-3 py-2 ${styles.cardText} ${styles.sidebarHoverBg} flex items-center gap-2 transition-colors`}
          >
            <span className="text-blue-500">
              <Box size={13} />
            </span>
            <span>{t('ow.btn.newObjectType')}</span>
          </button>
          <button
            onClick={() => {
              onCreateNew('link');
              setShowCreateDropdown(false);
            }}
            className={`w-full text-left px-3 py-2 ${styles.cardText} ${styles.sidebarHoverBg} flex items-center gap-2 transition-colors`}
          >
            <span className={`${styles.muted}`}>
              <GitMerge size={13} />
            </span>
            <span>{t('ow.btn.newLinkType')}</span>
          </button>
          <button
            onClick={() => {
              onCreateNew('action');
              setShowCreateDropdown(false);
            }}
            className={`w-full text-left px-3 py-2 ${styles.cardText} ${styles.sidebarHoverBg} flex items-center gap-2 transition-colors`}
          >
            <span className="text-amber-500">
              <Zap size={13} />
            </span>
            <span>{t('ow.btn.newActionType')}</span>
          </button>
          <button
            onClick={() => {
              onCreateNew('interface');
              setShowCreateDropdown(false);
            }}
            className={`w-full text-left px-3 py-2 ${styles.cardText} ${styles.sidebarHoverBg} flex items-center gap-2 transition-colors`}
          >
            <span className="text-indigo-500">
              <Layers size={13} />
            </span>
            <span>{t('ow.btn.newInterface')}</span>
          </button>
          <button
            onClick={() => {
              onCreateNew('shared_property');
              setShowCreateDropdown(false);
            }}
            className={`w-full text-left px-3 py-2 ${styles.cardText} ${styles.sidebarHoverBg} flex items-center gap-2 transition-colors`}
          >
            <span className="text-teal-500">
              <Tag size={13} />
            </span>
            <span>{t('ow.btn.newSharedProperty')}</span>
          </button>
          <button
            onClick={() => {
              onCreateNew('function');
              setShowCreateDropdown(false);
            }}
            className={`w-full text-left px-3 py-2 ${styles.cardText} ${styles.sidebarHoverBg} flex items-center gap-2 transition-colors`}
          >
            <span className="text-violet-500">
              <Code size={13} />
            </span>
            <span>{t('ow.btn.newFunction')}</span>
          </button>
        </div>
      )}
    </div>
  );
}
