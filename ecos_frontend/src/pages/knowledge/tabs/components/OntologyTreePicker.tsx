/**
 * Wave 3 C1 — OntologyTreePicker（本体树形选择器）
 *
 * 受控组件：父组件传入 selectedOntologyIds，勾选变化经 onChange 回调。
 * 三种视图：全部（选中即勾选树中全部本体）/ 按域（域头 checkbox 批量 + 叶子本体勾选）。
 * 记忆回显：localStorage `ecos_kb_ontology_picker` 保存 30 天，挂载时自动恢复。
 */

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  TreePine, Folder, CheckSquare, Square, ChevronRight, ChevronDown, RefreshCw, Loader2,
} from 'lucide-react';
import { useLanguage } from '../../../../components/LanguageContext';
import { useTheme } from '../../../../components/ThemeContext';
import { fetchOntologyTree } from '../../services/knowledgeApi';
import type { OntologyTreeNode, OntologyTreeVo } from '../../services/knowledgeApi';

const STORAGE_KEY = 'ecos_kb_ontology_picker';
const STORAGE_TTL_MS = 30 * 24 * 60 * 60 * 1000; // 30 天
const MAX_VISIBLE_TAGS = 5;

type PickerMode = 'all' | 'domain';

interface StoredSelection { mode: PickerMode; ids: string[]; ts: number; }

interface OntologyTreePickerProps {
  selectedOntologyIds: string[];
  onChange: (ids: string[]) => void;
  onTreeChange?: (tree: OntologyTreeNode[]) => void;
}

/** 读取本地记忆（try/catch 降级，过期或非法数据返回 null） */
function loadStoredSelection(): StoredSelection | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as StoredSelection;
    if (!parsed || typeof parsed.ts !== 'number' || !Array.isArray(parsed.ids)) return null;
    if (Date.now() - parsed.ts > STORAGE_TTL_MS) return null;
    return parsed;
  } catch {
    return null;
  }
}

/** 汇总树中全部本体 id */
function allOntologyIds(tree: OntologyTreeVo[]): string[] {
  return tree.flatMap(node => node.ontologies.map(o => o.id));
}

export default function OntologyTreePicker({
  selectedOntologyIds, onChange, onTreeChange,
}: OntologyTreePickerProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [tree, setTree] = useState<OntologyTreeVo[]>([]);
  const [loading, setLoading] = useState(true);
  const [mode, setMode] = useState<PickerMode>('domain');
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});
  const restoreConsumed = useRef(false); // 记忆回显只消费一次

  const allIds = useMemo(() => allOntologyIds(tree), [tree]);
  const isAllMode = mode === 'all';
  const activeIds = isAllMode ? allIds : selectedOntologyIds;

  const loadTree = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchOntologyTree();
      setTree(data);
      onTreeChange?.(data);
    } catch {
      // fetchOntologyTree 内部已兜底返回 []
    } finally {
      setLoading(false);
    }
  }, [onTreeChange]);

  useEffect(() => {
    void loadTree();
  }, [loadTree]);

  /** 写本地记忆（try/catch 降级）后通知父组件 */
  const setSelection = useCallback((nextMode: PickerMode, nextIds: string[]) => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ mode: nextMode, ids: nextIds, ts: Date.now() }));
    } catch {
      // localStorage 不可用时静默降级
    }
    onChange(nextIds);
  }, [onChange]);

  // 挂载回显：树加载完成后应用 30 天内本地记忆（仅消费一次）
  useEffect(() => {
    if (restoreConsumed.current || allIds.length === 0) return;
    restoreConsumed.current = true;
    const stored = loadStoredSelection();
    if (!stored) return;
    if (stored.mode === 'all') {
      setMode('all');
      setSelection('all', allIds);
    } else {
      setMode('domain');
      setSelection('domain', stored.ids.filter(id => allIds.includes(id)));
    }
  }, [allIds, setSelection]);

  const toggleOntology = (id: string) => {
    if (isAllMode) return; // 全部模式下个体勾选锁定
    const next = activeIds.includes(id) ? activeIds.filter(x => x !== id) : [...activeIds, id];
    setSelection('domain', next);
  };

  const toggleDomain = (domain: OntologyTreeVo) => {
    if (isAllMode) return;
    const idsInDomain = domain.ontologies.map(o => o.id);
    const fullySelected = idsInDomain.length > 0 && idsInDomain.every(id => activeIds.includes(id));
    const next = fullySelected
      ? activeIds.filter(x => !idsInDomain.includes(x))
      : Array.from(new Set([...activeIds, ...idsInDomain]));
    setSelection('domain', next);
  };

  const switchMode = (nextMode: PickerMode) => {
    if (nextMode === mode) return;
    if (nextMode === 'all') {
      // 全部模式：选中全部本体
      setMode('all');
      setSelection('all', allIds);
    } else {
      // 按域模式：保留当前集合，供个体勾选
      setMode('domain');
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify({ mode: 'domain', ids: activeIds, ts: Date.now() }));
      } catch {
        // 静默降级
      }
    }
  };

  const clearSelection = () => setSelection('domain', []);

  const toggleCollapsed = (domain: string) => {
    setCollapsed(prev => ({ ...prev, [domain]: !prev[domain] }));
  };

  const selectedCountIn = (domain: OntologyTreeVo) =>
    domain.ontologies.filter(o => activeIds.includes(o.id)).length;

  const isDomainSelected = (domain: OntologyTreeVo) =>
    domain.ontologies.length > 0 && selectedCountIn(domain) === domain.ontologies.length;
  const isDomainPartial = (domain: OntologyTreeVo) =>
    !isDomainSelected(domain) && selectedCountIn(domain) > 0;

  /** 行背景：选中 > 半选 > 默认 hover */
  const rowCls = (checked: boolean, partial: boolean) =>
    checked
      ? `${styles.sidebarActiveBg} ${styles.cardText}`
      : partial
        ? `${styles.badgeBg} ${styles.cardText}`
        : `${styles.badgeBg} ${styles.sidebarHoverBg} ${styles.cardBorder} ${styles.sidebarText}`;

  const chipCls = (active: boolean) =>
    `flex items-center gap-1 px-2.5 py-1 rounded-lg text-[10px] font-bold cursor-pointer border transition-all ${
      active
        ? `${styles.sidebarActiveBg} ${styles.cardText}`
        : `${styles.badgeBg} ${styles.sidebarHoverBg} ${styles.appBorder} ${styles.sidebarText}`
    }`;

  // 已选摘要（超出 5 个折叠为 +N）
  const visibleSelected = activeIds.slice(0, MAX_VISIBLE_TAGS);
  const overflowCount = activeIds.length - visibleSelected.length;
  const nameOf = (id: string) =>
    tree.flatMap(n => n.ontologies).find(o => o.id === id)?.name || id;

  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 space-y-3`}>
      {/* 头部：标题 + 刷新/清空 */}
      <div className={`flex items-center justify-between border-b ${styles.appBorder} pb-2`}>
        <span className={`text-[10px] font-extrabold uppercase tracking-wider ${styles.muted} font-mono flex items-center gap-1`}>
          <TreePine size={12} />
          {t('knowledge.ontologyTree.title')}
        </span>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => void loadTree()}
            className={`p-1 rounded-md cursor-pointer ${styles.sidebarHoverBg} ${styles.muted}`}
            title={t('knowledge.tree_picker.refresh')}
          >
            <RefreshCw size={11} className={loading ? 'animate-spin' : ''} />
          </button>
          <button
            type="button"
            onClick={clearSelection}
            className={`p-1 rounded-md cursor-pointer ${styles.sidebarHoverBg} ${styles.muted}`}
            title={t('knowledge.tree_picker.clear')}
          >
            <Square size={11} />
          </button>
        </div>
      </div>

      {/* 模式切换 chip */}
      <div className="flex items-center gap-1.5">
        <button type="button" onClick={() => switchMode('all')} className={chipCls(isAllMode)}>
          <CheckSquare size={11} />
          <span>{t('knowledge.tree_picker.mode_all')}</span>
        </button>
        <button type="button" onClick={() => switchMode('domain')} className={chipCls(!isAllMode)}>
          <Folder size={11} />
          <span>{t('knowledge.tree_picker.mode_domain')}</span>
        </button>
      </div>

      {/* 加载骨架屏 / 空态 / 树体 */}
      {loading ? (
        <div className="space-y-2 py-1">
          {[0, 1, 2].map(i => (
            <div key={i} className={`h-6 rounded-md animate-pulse ${styles.badgeBg}`} />
          ))}
          <p className={`text-[10px] ${styles.muted} font-mono flex items-center gap-1.5 pt-1`}>
            <Loader2 size={10} className="animate-spin" />
            {t('knowledge.tree_picker.loading')}
          </p>
        </div>
      ) : allIds.length === 0 ? (
        <p className={`text-xs ${styles.muted} text-center py-6`}>{t('knowledge.tree_picker.empty')}</p>
      ) : (
        <div className="space-y-1 max-h-72 overflow-y-auto pr-1">
          {isAllMode ? (
            // 全部模式：单行高亮（选中即全选）
            <button
              type="button"
              className={`w-full flex items-center gap-2 p-2 rounded-lg cursor-pointer text-left ${rowCls(true, false)}`}
              onClick={() => setSelection('all', allIds)}
            >
              <CheckSquare size={13} />
              <span className="text-xs font-bold truncate">{t('knowledge.tree_picker.mode_all')}</span>
              <span className={`ml-auto text-[10px] font-mono ${styles.muted}`}>{allIds.length}</span>
            </button>
          ) : (
            tree.map(node => {
              const isCollapsed = !!collapsed[node.domain];
              return (
                <div key={node.domain}>
                  {/* 域头：展开箭头 + 批量勾选 */}
                  <div className={`flex items-center gap-1 p-1.5 rounded-lg ${styles.sidebarHoverBg}`}>
                    <button
                      type="button"
                      onClick={() => toggleCollapsed(node.domain)}
                      className={`p-0.5 rounded cursor-pointer ${styles.muted}`}
                      title={isCollapsed ? t('knowledge.tree_picker.expand') : t('knowledge.tree_picker.collapse')}
                    >
                      {isCollapsed ? <ChevronRight size={12} /> : <ChevronDown size={12} />}
                    </button>
                    <button
                      type="button"
                      onClick={() => toggleDomain(node)}
                      className={`flex-1 flex items-center gap-2 rounded-md px-1.5 py-1 cursor-pointer text-left ${rowCls(isDomainSelected(node), isDomainPartial(node))}`}
                    >
                      <Folder size={12} />
                      <span className="text-xs font-bold truncate">{node.domain}</span>
                      <span className={`ml-auto text-[10px] font-mono ${styles.muted}`}>
                        {selectedCountIn(node)}/{node.ontologies.length}
                      </span>
                    </button>
                  </div>
                  {/* 叶子：本体个体勾选 */}
                  {!isCollapsed && node.ontologies.map(onto => (
                    <button
                      key={onto.id}
                      type="button"
                      onClick={() => toggleOntology(onto.id)}
                      className={`w-full flex items-center gap-2 pl-7 pr-2 py-1.5 rounded-lg mt-0.5 cursor-pointer text-left ${rowCls(activeIds.includes(onto.id), false)}`}
                    >
                      {activeIds.includes(onto.id) ? <CheckSquare size={12} /> : <Square size={12} />}
                      <span className="text-[11px] font-semibold truncate">{onto.name || onto.id}</span>
                    </button>
                  ))}
                </div>
              );
            })
          )}
        </div>
      )}

      {/* 底部：已选摘要 tag */}
      <div className={`border-t ${styles.appBorder} pt-2 space-y-1.5`}>
        <div className="flex items-center justify-between">
          <span className={`text-[10px] font-mono ${styles.muted}`}>
            {t('knowledge.tree_picker.select_count', { count: activeIds.length })}
          </span>
          <button
            type="button"
            onClick={clearSelection}
            className={`text-[10px] font-bold cursor-pointer ${styles.muted} hover:opacity-70`}
          >
            {t('knowledge.tree_picker.clear_selected')}
          </button>
        </div>
        {visibleSelected.length > 0 && (
          <div className="flex flex-wrap gap-1">
            {visibleSelected.map(id => (
              <span key={id} className={`px-1.5 py-0.5 rounded-md text-[9px] font-mono font-bold ${styles.badgeBg} ${styles.sidebarText}`}>
                {nameOf(id)}
              </span>
            ))}
            {overflowCount > 0 && (
              <span className={`px-1.5 py-0.5 rounded-md text-[9px] font-mono font-bold ${styles.badgeBg} ${styles.muted}`}>
                {t('knowledge.tree_picker.more', { n: overflowCount })}
              </span>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
