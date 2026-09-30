/**
 * SidebarElementSections — 侧栏本体元素分节列表（对象/链接/动作/函数/接口/共享属性/数据集）
 *
 * 从 `components/ontology/Sidebar.tsx` 机械抽取（H6-T4 组件行数治理）：
 * 分节顺序、行内 className（含源码原有的选中态硬编码 class）与图标均逐字保留。
 *
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { Code, Database, GitMerge, Layers, Tag, Zap } from 'lucide-react';
import { useLanguage } from '../../LanguageContext';
import { useTheme } from '../../ThemeContext';
import {
  ActionType,
  Dataset,
  FunctionType,
  InterfaceType,
  LinkType,
  ObjectType,
  SharedProperty,
} from '../../../types/ontology';
import DynamicIcon from './SidebarDynamicIcon';
import SidebarAccordionSection from './SidebarAccordionSection';

type ElementSectionCategory =
  | 'object'
  | 'link'
  | 'action'
  | 'function'
  | 'interface'
  | 'shared_property'
  | 'dataset';

interface SidebarElementSectionsProps {
  objectTypes: ObjectType[];
  linkTypes: LinkType[];
  actionTypes: ActionType[];
  interfaces: InterfaceType[];
  sharedProperties: SharedProperty[];
  datasets: Dataset[];
  functionTypes: FunctionType[];
  selectedCategory: string;
  selectedId: string | null;
  onSelectCategory: (category: ElementSectionCategory, id: string | null) => void;
  expanded: Record<string, boolean>;
  toggleExpand: (key: string) => void;
}

export default function SidebarElementSections({
  objectTypes,
  linkTypes,
  actionTypes,
  interfaces,
  sharedProperties,
  datasets,
  functionTypes,
  selectedCategory,
  selectedId,
  onSelectCategory,
  expanded,
  toggleExpand,
}: SidebarElementSectionsProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className="flex-1 overflow-y-auto py-3 space-y-1">

      {/* 1. OBJECT TYPES */}
      <SidebarAccordionSection
        sectionKey="object"
        label={t('ow.sidebar.objectTypes')}
        count={objectTypes.length}
        outerClassName="space-y-0.5"
        expanded={expanded}
        onToggle={toggleExpand}
        renderRows={() => objectTypes.map(ot => {
          const isActive = selectedCategory === 'object' && selectedId === ot.id;
          return (
            <button
              key={ot.id}
              onClick={() => onSelectCategory('object', ot.id)}
              className={`w-full text-left py-1.5 px-2.5 rounded-md flex items-center justify-between transition-colors ${
                isActive
                  ? `${styles.sidebarActiveBg} ${styles.sidebarActiveText} font-semibold border-l-2 ${styles.accentBorder}`
                  : `${styles.sidebarText} ${styles.sidebarHoverBg}`
              }`}
            >
              <div className="flex items-center gap-2 truncate">
                <span className={`p-0.5 rounded border ${isActive ? `${styles.sidebarActiveBg} ${styles.accentBorder} ${styles.sidebarActiveText}` : `${styles.cardBg} ${styles.sidebarBorder} ${styles.muted}`}`}>
                  <DynamicIcon name={ot.icon} size={11} />
                </span>
                <span className="truncate">{ot.displayName}</span>
              </div>
              <span className={`text-[9px] font-mono opacity-65 uppercase ${styles.muted}`}>{ot.id}</span>
            </button>
          );
        })}
      />

      {/* 2. LINK TYPES */}
      <SidebarAccordionSection
        sectionKey="link"
        label={t('ow.sidebar.linkTypes')}
        count={linkTypes.length}
        outerClassName="space-y-0.5"
        expanded={expanded}
        onToggle={toggleExpand}
        renderRows={() => linkTypes.map(lt => {
          const isActive = selectedCategory === 'link' && selectedId === lt.id;
          return (
            <button
              key={lt.id}
              onClick={() => onSelectCategory('link', lt.id)}
              className={`w-full text-left py-1.5 px-2.5 rounded-md flex items-center justify-between transition-colors ${
                isActive
                  ? `${styles.sidebarActiveBg} ${styles.sidebarActiveText} font-semibold border-l-2 ${styles.accentBorder}`
                  : `${styles.sidebarText} ${styles.sidebarHoverBg}`
              }`}
            >
              <div className="flex items-center gap-2 truncate">
                <span className={styles.muted}>
                  <GitMerge size={11} />
                </span>
                <span className="truncate">{lt.displayName}</span>
              </div>
              <span className={`text-[9px] font-mono opacity-50 font-bold ${styles.muted}`}>{lt.cardinality}</span>
            </button>
          );
        })}
      />

      {/* 3. ACTION TYPES */}
      <SidebarAccordionSection
        sectionKey="action"
        label={t('ow.sidebar.actionTypes')}
        count={actionTypes.length}
        outerClassName="space-y-0.5"
        expanded={expanded}
        onToggle={toggleExpand}
        renderRows={() => actionTypes.map(at => {
          const isActive = selectedCategory === 'action' && selectedId === at.id;
          return (
            <button
              key={at.id}
              onClick={() => onSelectCategory('action', at.id)}
              className={`w-full text-left py-1.5 px-2.5 rounded-md flex items-center justify-between transition-colors ${
                isActive
                  ? `${styles.sidebarActiveBg} ${styles.sidebarActiveText} font-semibold border-l-2 ${styles.accentBorder}`
                  : `${styles.sidebarText} ${styles.sidebarHoverBg}`
              }`}
            >
              <div className="flex items-center gap-2 truncate">
                <span className="text-amber-500">
                  <Zap size={11} className="fill-amber-400/30" />
                </span>
                <span className="truncate">{at.displayName}</span>
              </div>
            </button>
          );
        })}
      />

      {/* 3.5. FUNCTION TYPES */}
      <SidebarAccordionSection
        sectionKey="function"
        label={t('ow.sidebar.functionTypes')}
        count={functionTypes.length}
        outerClassName="space-y-0.5"
        expanded={expanded}
        onToggle={toggleExpand}
        renderRows={() => functionTypes.map(fn => {
          const isActive = selectedCategory === 'function' && selectedId === fn.id;
          return (
            <button
              key={fn.id}
              onClick={() => onSelectCategory('function', fn.id)}
              className={`w-full text-left py-1.5 px-2.5 rounded-md flex items-center justify-between transition-colors ${
                isActive
                  ? `${styles.sidebarActiveBg} ${styles.sidebarActiveText} font-semibold border-l-2 ${styles.accentBorder}`
                  : `${styles.sidebarText} ${styles.sidebarHoverBg}`
              }`}
            >
              <div className="flex items-center gap-2 truncate">
                <span className="text-violet-500">
                  <Code size={11} />
                </span>
                <span className="truncate">{fn.displayName}</span>
              </div>
              <span className={`text-[9px] font-mono opacity-50 uppercase ${styles.muted}`}>{fn.returnType}</span>
            </button>
          );
        })}
      />

      {/* 4. INTERFACE TYPES */}
      <SidebarAccordionSection
        sectionKey="interface"
        label={t('ow.sidebar.interfaces')}
        count={interfaces.length}
        outerClassName="space-y-0.5"
        expanded={expanded}
        onToggle={toggleExpand}
        renderRows={() => interfaces.map(it => {
          const isActive = selectedCategory === 'interface' && selectedId === it.id;
          return (
            <button
              key={it.id}
              onClick={() => onSelectCategory('interface', it.id)}
              className={`w-full text-left py-1.5 px-2.5 rounded-md flex items-center justify-between transition-colors ${
                isActive
                  ? 'bg-blue-50 text-blue-700 font-semibold border-l-2 border-blue-600'
                  : `${styles.sidebarText} ${styles.sidebarHoverBg}`
              }`}
            >
              <div className="flex items-center gap-2 truncate">
                <span className="text-indigo-500">
                  <Layers size={11} />
                </span>
                <span className="truncate">{it.displayName}</span>
              </div>
            </button>
          );
        })}
      />

      {/* 5. SHARED PROPERTIES */}
      <SidebarAccordionSection
        sectionKey="shared_property"
        label={t('ow.sidebar.sharedProperties')}
        count={sharedProperties.length}
        outerClassName="space-y-0.5"
        expanded={expanded}
        onToggle={toggleExpand}
        renderRows={() => sharedProperties.map(sp => {
          const isActive = selectedCategory === 'shared_property' && selectedId === sp.id;
          return (
            <button
              key={sp.id}
              onClick={() => onSelectCategory('shared_property', sp.id)}
              className={`w-full text-left py-1.5 px-2.5 rounded-md flex items-center justify-between transition-colors ${
                isActive
                  ? 'bg-blue-50 text-blue-700 font-semibold border-l-2 border-blue-600'
                  : `${styles.sidebarText} ${styles.sidebarHoverBg}`
              }`}
            >
              <div className="flex items-center gap-2 truncate">
                <span className="text-teal-500">
                  <Tag size={11} />
                </span>
                <span className="truncate">{sp.displayName}</span>
              </div>
            </button>
          );
        })}
      />

      {/* 6. RAW DATASETS */}
      <SidebarAccordionSection
        sectionKey="dataset"
        label={t('ow.sidebar.datasets')}
        count={datasets.length}
        outerClassName={`space-y-0.5 border-t ${styles.appBorder} pt-2 mt-2`}
        expanded={expanded}
        onToggle={toggleExpand}
        renderRows={() => datasets.map(ds => {
          const isActive = selectedCategory === 'dataset' && selectedId === ds.id;
          return (
            <button
              key={ds.id}
              onClick={() => onSelectCategory('dataset', ds.id)}
              className={`w-full text-left py-1.5 px-2.5 rounded-md flex items-center justify-between transition-colors ${
                isActive
                  ? 'bg-blue-50 text-blue-700 font-semibold border-l-2 border-blue-600'
                  : `${styles.sidebarText} ${styles.sidebarHoverBg}`
              }`}
            >
              <div className="flex items-center gap-2 truncate">
                <span className={`${styles.muted}`}>
                  <Database size={11} />
                </span>
                <span className="truncate font-mono text-[10px]">{ds.name}</span>
              </div>
            </button>
          );
        })}
      />
    </div>
  );
}
