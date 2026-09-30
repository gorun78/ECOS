/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import LucideIcon from '../LucideIcon';
import { useTheme } from '../../../components/ThemeContext';

interface CreateMenuFooterProps {
  showCreateDropdown: boolean;
  setShowCreateDropdown: (value: boolean) => void;
  onCreateNew: (type: 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'function') => void;
}

export default function CreateMenuFooter({
  showCreateDropdown,
  setShowCreateDropdown,
  onCreateNew
}: CreateMenuFooterProps) {
  const { styles } = useTheme();

  return (
    <div className={`p-3 border-t ${styles.sidebarBorder} ${styles.sidebarBg} relative`}>
      <button
        onClick={() => setShowCreateDropdown(!showCreateDropdown)}
        className="w-full bg-[var(--card,#0F172A)] hover:bg-[var(--muted,#1E293B)] text-white font-medium py-2 px-3 rounded-lg flex items-center justify-center gap-1.5 transition-colors shadow-xs"
      >
        <LucideIcon name="PlusCircle" size={14} />
        <span>新建本体元素</span>
        <LucideIcon name={showCreateDropdown ? "ChevronDown" : "ChevronUp"} size={12} />
      </button>

      {/* Create Dropdown */}
      {showCreateDropdown && (
        <div className={`absolute bottom-14 left-3 right-3 ${styles.cardBg} border ${styles.sidebarBorder} rounded-lg shadow-lg py-1 z-30 divide-y ${styles.divider}`}>
          <button
            onClick={() => {
              onCreateNew('object');
              setShowCreateDropdown(false);
            }}
            className={`w-full text-left px-3 py-2 ${styles.cardText} hover:bg-blue-50/20 flex items-center gap-2 transition-colors`}
          >
            <span className="text-blue-500">
              <LucideIcon name="Box" size={13} />
            </span>
            <span>新建对象类型 (Object Type)</span>
          </button>
          <button
            onClick={() => {
              onCreateNew('link');
              setShowCreateDropdown(false);
            }}
            className={`w-full text-left px-3 py-2 ${styles.cardText} hover:bg-blue-50/20 flex items-center gap-2 transition-colors`}
          >
            <span className={styles.cardTextMuted}>
              <LucideIcon name="GitMerge" size={13} />
            </span>
            <span>新建链接关系 (Link Type)</span>
          </button>
          <button
            onClick={() => {
              onCreateNew('action');
              setShowCreateDropdown(false);
            }}
            className={`w-full text-left px-3 py-2 ${styles.cardText} hover:bg-blue-50/20 flex items-center gap-2 transition-colors`}
          >
            <span className="text-amber-500">
              <LucideIcon name="Zap" size={13} />
            </span>
            <span>新建操作类型 (Action Type)</span>
          </button>
          <button
            onClick={() => {
              onCreateNew('interface');
              setShowCreateDropdown(false);
            }}
            className={`w-full text-left px-3 py-2 ${styles.cardText} hover:bg-blue-50/20 flex items-center gap-2 transition-colors`}
          >
            <span className="text-indigo-500">
              <LucideIcon name="Layers" size={13} />
            </span>
            <span>新建接口定义 (Interface)</span>
          </button>
          <button
            onClick={() => {
              onCreateNew('shared_property');
              setShowCreateDropdown(false);
            }}
            className={`w-full text-left px-3 py-2 ${styles.cardText} hover:bg-blue-50/20 flex items-center gap-2 transition-colors`}
          >
            <span className="text-teal-500">
              <LucideIcon name="Tag" size={13} />
            </span>
            <span>新建共享属性 (Shared Property)</span>
          </button>
          <button
            onClick={() => {
              onCreateNew('function');
              setShowCreateDropdown(false);
            }}
            className={`w-full text-left px-3 py-2 ${styles.cardText} hover:bg-blue-50/20 flex items-center gap-2 transition-colors`}
          >
            <span className="text-violet-500">
              <LucideIcon name="Code" size={13} />
            </span>
            <span>新建逻辑函数 (Function)</span>
          </button>
        </div>
      )}
    </div>
  );
}
