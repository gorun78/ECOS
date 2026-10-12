/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { ObjectType, LinkType, ActionType, InterfaceType, SharedProperty, Dataset, FunctionType } from '../../../types/ontology';
import LucideIcon from '../LucideIcon';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';

type SidebarCategory = 'overview' | 'explorer' | 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'dataset' | 'function';

interface AccordionBaseProps {
  expanded: Record<string, boolean>;
  toggleExpand: (key: string) => void;
  selectedCategory: SidebarCategory;
  selectedId: string | null;
  onSelectCategory: (category: any, id: string | null) => void;
}

interface ObjectTypesAccordionProps extends AccordionBaseProps {
  objectTypes: ObjectType[];
}

export function ObjectTypesAccordion({
  objectTypes,
  expanded,
  toggleExpand,
  selectedCategory,
  selectedId,
  onSelectCategory
}: ObjectTypesAccordionProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className="space-y-0.5">
      <button
        type="button"
        onClick={() => toggleExpand('object')}
        className={`w-full py-1.5 px-3 flex items-center justify-between ${styles.cardTextMuted} opacity-80 hover:opacity-100 font-semibold uppercase tracking-wider text-[10px]`}
      >
        <div className="flex items-center gap-1">
          <LucideIcon name={expanded.object ? "ChevronDown" : "ChevronRight"} size={12} />
          <span>{t('ow.side.objectLabel')}</span>
        </div>
        <span>{objectTypes.length}</span>
      </button>
      {expanded.object && (
        <div className="px-2 space-y-0.5">
          {objectTypes.map(ot => {
            const isActive = selectedCategory === 'object' && selectedId === ot.id;
            return (
              <button
                type="button"
                key={ot.id}
                onClick={() => onSelectCategory('object', ot.id)}
                className={`w-full text-left py-1.5 px-2.5 rounded-md flex items-center justify-between transition-colors ${
                  isActive
                    ? 'bg-blue-50 text-blue-700 font-semibold border-l-2 border-blue-600'
                    : `${styles.sidebarText} ${styles.sidebarHoverBg}`
                }`}
              >
                <div className="flex items-center gap-2 truncate">
                  <span className={`p-0.5 rounded border ${isActive ? 'bg-blue-100 border-blue-300 text-blue-800' : `${styles.cardBg} ${styles.sidebarBorder} ${styles.cardTextMuted}`}`}>
                    <LucideIcon name={ot.icon} size={11} />
                  </span>
                  <span className="truncate">{ot.displayName}</span>
                </div>
                <span className="text-[9px] font-mono opacity-65 uppercase">{ot.id}</span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

interface LinkTypesAccordionProps extends AccordionBaseProps {
  linkTypes: LinkType[];
}

export function LinkTypesAccordion({
  linkTypes,
  expanded,
  toggleExpand,
  selectedCategory,
  selectedId,
  onSelectCategory
}: LinkTypesAccordionProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className="space-y-0.5">
      <button
        type="button"
        onClick={() => toggleExpand('link')}
        className={`w-full py-1.5 px-3 flex items-center justify-between ${styles.cardTextMuted} opacity-80 hover:opacity-100 font-semibold uppercase tracking-wider text-[10px]`}
      >
        <div className="flex items-center gap-1">
          <LucideIcon name={expanded.link ? "ChevronDown" : "ChevronRight"} size={12} />
          <span>{t('ow.side.linkLabel')}</span>
        </div>
        <span>{linkTypes.length}</span>
      </button>
      {expanded.link && (
        <div className="px-2 space-y-0.5">
          {linkTypes.map(lt => {
            const isActive = selectedCategory === 'link' && selectedId === lt.id;
            return (
              <button
                type="button"
                key={lt.id}
                onClick={() => onSelectCategory('link', lt.id)}
                className={`w-full text-left py-1.5 px-2.5 rounded-md flex items-center justify-between transition-colors ${
                  isActive
                    ? 'bg-blue-50 text-blue-700 font-semibold border-l-2 border-blue-600'
                    : `${styles.sidebarText} ${styles.sidebarHoverBg}`
                }`}
              >
                <div className="flex items-center gap-2 truncate">
                  <span className={styles.cardTextMuted}>
                    <LucideIcon name="GitMerge" size={11} />
                  </span>
                  <span className="truncate">{lt.displayName}</span>
                </div>
                <span className="text-[9px] font-mono opacity-50 font-bold">{lt.cardinality}</span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

interface ActionTypesAccordionProps extends AccordionBaseProps {
  actionTypes: ActionType[];
}

export function ActionTypesAccordion({
  actionTypes,
  expanded,
  toggleExpand,
  selectedCategory,
  selectedId,
  onSelectCategory
}: ActionTypesAccordionProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className="space-y-0.5">
      <button
        type="button"
        onClick={() => toggleExpand('action')}
        className={`w-full py-1.5 px-3 flex items-center justify-between ${styles.cardTextMuted} opacity-80 hover:opacity-100 font-semibold uppercase tracking-wider text-[10px]`}
      >
        <div className="flex items-center gap-1">
          <LucideIcon name={expanded.action ? "ChevronDown" : "ChevronRight"} size={12} />
          <span>{t('ow.side.actionLabel')}</span>
        </div>
        <span>{actionTypes.length}</span>
      </button>
      {expanded.action && (
        <div className="px-2 space-y-0.5">
          {actionTypes.map(at => {
            const isActive = selectedCategory === 'action' && selectedId === at.id;
            return (
              <button
                type="button"
                key={at.id}
                onClick={() => onSelectCategory('action', at.id)}
                className={`w-full text-left py-1.5 px-2.5 rounded-md flex items-center justify-between transition-colors ${
                  isActive
                    ? 'bg-blue-50 text-blue-700 font-semibold border-l-2 border-blue-600'
                    : `${styles.sidebarText} ${styles.sidebarHoverBg}`
                }`}
              >
                <div className="flex items-center gap-2 truncate">
                  <span className="text-amber-500">
                    <LucideIcon name="Zap" size={11} className="fill-amber-400/30" />
                  </span>
                  <span className="truncate">{at.displayName}</span>
                </div>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

interface FunctionTypesAccordionProps extends AccordionBaseProps {
  functionTypes: FunctionType[];
}

export function FunctionTypesAccordion({
  functionTypes,
  expanded,
  toggleExpand,
  selectedCategory,
  selectedId,
  onSelectCategory
}: FunctionTypesAccordionProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className="space-y-0.5">
      <button
        type="button"
        onClick={() => toggleExpand('function')}
        className={`w-full py-1.5 px-3 flex items-center justify-between ${styles.cardTextMuted} opacity-80 hover:opacity-100 font-semibold uppercase tracking-wider text-[10px]`}
      >
        <div className="flex items-center gap-1">
          <LucideIcon name={expanded.function ? "ChevronDown" : "ChevronRight"} size={12} />
          <span>{t('ow.side.functionLabel')}</span>
        </div>
        <span>{functionTypes.length}</span>
      </button>
      {expanded.function && (
        <div className="px-2 space-y-0.5">
          {functionTypes.map(fn => {
            const isActive = selectedCategory === 'function' && selectedId === fn.id;
            return (
              <button
                type="button"
                key={fn.id}
                onClick={() => onSelectCategory('function', fn.id)}
                className={`w-full text-left py-1.5 px-2.5 rounded-md flex items-center justify-between transition-colors ${
                  isActive
                    ? 'bg-blue-50 text-blue-700 font-semibold border-l-2 border-blue-600'
                    : `${styles.sidebarText} ${styles.sidebarHoverBg}`
                }`}
              >
                <div className="flex items-center gap-2 truncate">
                  <span className="text-violet-500">
                    <LucideIcon name="Code" size={11} />
                  </span>
                  <span className="truncate">{fn.displayName}</span>
                </div>
                <span className="text-[9px] font-mono opacity-50 uppercase">{fn.returnType}</span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

interface InterfacesAccordionProps extends AccordionBaseProps {
  interfaces: InterfaceType[];
}

export function InterfacesAccordion({
  interfaces,
  expanded,
  toggleExpand,
  selectedCategory,
  selectedId,
  onSelectCategory
}: InterfacesAccordionProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className="space-y-0.5">
      <button
        type="button"
        onClick={() => toggleExpand('interface')}
        className={`w-full py-1.5 px-3 flex items-center justify-between ${styles.cardTextMuted} opacity-80 hover:opacity-100 font-semibold uppercase tracking-wider text-[10px]`}
      >
        <div className="flex items-center gap-1">
          <LucideIcon name={expanded.interface ? "ChevronDown" : "ChevronRight"} size={12} />
          <span>{t('ow.side.interfaceLabel')}</span>
        </div>
        <span>{interfaces.length}</span>
      </button>
      {expanded.interface && (
        <div className="px-2 space-y-0.5">
          {interfaces.map(it => {
            const isActive = selectedCategory === 'interface' && selectedId === it.id;
            return (
              <button
                type="button"
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
                    <LucideIcon name="Layers" size={11} />
                  </span>
                  <span className="truncate">{it.displayName}</span>
                </div>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

interface SharedPropertiesAccordionProps extends AccordionBaseProps {
  sharedProperties: SharedProperty[];
}

export function SharedPropertiesAccordion({
  sharedProperties,
  expanded,
  toggleExpand,
  selectedCategory,
  selectedId,
  onSelectCategory
}: SharedPropertiesAccordionProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className="space-y-0.5">
      <button
        type="button"
        onClick={() => toggleExpand('shared_property')}
        className={`w-full py-1.5 px-3 flex items-center justify-between ${styles.cardTextMuted} opacity-80 hover:opacity-100 font-semibold uppercase tracking-wider text-[10px]`}
      >
        <div className="flex items-center gap-1">
          <LucideIcon name={expanded.shared_property ? "ChevronDown" : "ChevronRight"} size={12} />
          <span>{t('ow.side.sharedPropLabel')}</span>
        </div>
        <span>{sharedProperties.length}</span>
      </button>
      {expanded.shared_property && (
        <div className="px-2 space-y-0.5">
          {sharedProperties.map(sp => {
            const isActive = selectedCategory === 'shared_property' && selectedId === sp.id;
            return (
              <button
                type="button"
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
                    <LucideIcon name="Tag" size={11} />
                  </span>
                  <span className="truncate">{sp.displayName}</span>
                </div>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

interface DatasetsAccordionProps extends AccordionBaseProps {
  datasets: Dataset[];
}

export function DatasetsAccordion({
  datasets,
  expanded,
  toggleExpand,
  selectedCategory,
  selectedId,
  onSelectCategory
}: DatasetsAccordionProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  return (
    <div className={`space-y-0.5 border-t ${styles.sidebarBorder} pt-2 mt-2`}>
      <button
        type="button"
        onClick={() => toggleExpand('dataset')}
        className={`w-full py-1.5 px-3 flex items-center justify-between ${styles.cardTextMuted} opacity-80 hover:opacity-100 font-semibold uppercase tracking-wider text-[10px]`}
      >
        <div className="flex items-center gap-1">
          <LucideIcon name={expanded.dataset ? "ChevronDown" : "ChevronRight"} size={12} />
          <span>{t('ow.side.datasetLabel')}</span>
        </div>
        <span>{datasets.length}</span>
      </button>
      {expanded.dataset && (
        <div className="px-2 space-y-0.5">
          {datasets.map(ds => {
            const isActive = selectedCategory === 'dataset' && selectedId === ds.id;
            return (
              <button
                type="button"
                key={ds.id}
                onClick={() => onSelectCategory('dataset', ds.id)}
                className={`w-full text-left py-1.5 px-2.5 rounded-md flex items-center justify-between transition-colors ${
                  isActive
                    ? 'bg-blue-50 text-blue-700 font-semibold border-l-2 border-blue-600'
                    : `${styles.sidebarText} ${styles.sidebarHoverBg}`
                }`}
              >
                <div className="flex items-center gap-2 truncate">
                  <span className={styles.cardTextMuted}>
                    <LucideIcon name="Database" size={11} />
                  </span>
                  <span className="truncate font-mono text-[10px]">{ds.name}</span>
                </div>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
