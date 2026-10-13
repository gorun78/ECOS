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
import { Box, Code, GitMerge, Layers, PlusCircle, ShieldOff, Tag, Zap } from 'lucide-react';
import { useLanguage } from '../../LanguageContext';
import { useTheme } from '../../ThemeContext';
import DynamicIcon from './SidebarDynamicIcon';

type CreateType = 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'function';

interface CreateElementMenuProps {
  showCreateDropdown: boolean;
  setShowCreateDropdown: (open: boolean) => void;
  onCreateNew: (type: CreateType) => void;
  /**
   * 无假真理：这些类型没有后端创建端点（已核实 services/ontology/*：
   * action-types 仅 GET；interfaces / shared-properties / functions 无任何端点）。
   * 出现在此数组的项 ⇒ 按钮禁用 + 「能力未上线」标注，而不是内存假创建。
   */
  unsupportedCreateTypes?: ReadonlyArray<CreateType>;
}

export default function CreateElementMenu({
  showCreateDropdown,
  setShowCreateDropdown,
  onCreateNew,
  unsupportedCreateTypes,
}: CreateElementMenuProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  const items: Array<{ type: CreateType; icon: React.ReactNode; label: string }> = [
    { type: 'object', icon: <Box size={13} />, label: t('ow.btn.newObjectType') },
    { type: 'link', icon: <GitMerge size={13} />, label: t('ow.btn.newLinkType') },
    { type: 'action', icon: <Zap size={13} />, label: t('ow.btn.newActionType') },
    { type: 'interface', icon: <Layers size={13} />, label: t('ow.btn.newInterface') },
    { type: 'shared_property', icon: <Tag size={13} />, label: t('ow.btn.newSharedProperty') },
    { type: 'function', icon: <Code size={13} />, label: t('ow.btn.newFunction') },
  ];

  // Bottom Action bar — T4: 替换原来的 白底/黑底 硬编码 → theme tokens
  return (
    <div className={`p-3 border-t ${styles.appBorder} ${styles.cardBg} relative`}>
      <button type="button"
        onClick={() => setShowCreateDropdown(!showCreateDropdown)}
        className={`w-full ${styles.accentBg} text-white ${styles.accentHover} font-medium py-2 px-3 rounded-lg flex items-center justify-center gap-1.5 transition-colors shadow-xs`}
      >
        <PlusCircle size={14} />
        <span>{t('ow.btn.createNewElement')}</span>
        <DynamicIcon name={showCreateDropdown ? "ChevronDown" : "ChevronUp"} size={12} />
      </button>

      {/* Create Dropdown — 无持久化端点的类型禁用并如实标注（不弹假成功） */}
      {showCreateDropdown && (
        <div className={`absolute bottom-14 left-3 right-3 ${styles.cardBg} border ${styles.appBorder} rounded-lg shadow-lg py-1 z-30 divide-y ${styles.divider}`}>
          {items.map(item => {
            const unsupported = (unsupportedCreateTypes ?? []).includes(item.type);
            return (
              <button
                key={item.type}
                type="button"
                disabled={unsupported}
                title={unsupported ? t('ontology.create_unsupported') : undefined}
                onClick={() => {
                  if (unsupported) return;
                  onCreateNew(item.type);
                  setShowCreateDropdown(false);
                }}
                className={`w-full text-left px-3 py-2 flex items-start gap-2 transition-colors ${
                  unsupported
                    ? `${styles.muted} cursor-not-allowed opacity-60`
                    : `${styles.cardText} ${styles.sidebarHoverBg}`
                }`}
              >
                <span className="mt-0.5 shrink-0">{item.icon}</span>
                <span className="min-w-0">
                  <span className="block">{item.label}</span>
                  {unsupported && (
                    <span className={`flex items-center gap-1 ${styles.warningText} text-[10px] font-semibold`}>
                      <ShieldOff size={10} /> {t('ontology.capability_not_online')}
                    </span>
                  )}
                </span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
