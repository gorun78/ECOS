/**
 * Wave 3 C1 — OntologyTreePicker（本体树形选择器）
 *
 * 受控组件：父组件传入 selectedOntologyIds，勾选变化经 onChange 回调。
 * 三种视图：全部（选中即勾选树中全部本体）/ 按域（域头批量 + 叶子本体勾选）。
 * 层级结构：域（domainBreakdown 业务域）→ 本体（勾选粒度）→ 对象类型（只读明细，entityType 徽章）。
 * 记忆回显：localStorage `ecos_kb_ontology_picker` 保存 30 天，挂载时自动恢复。
 */

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  TreePine, Folder, CheckSquare, Square, ChevronRight, ChevronDown, RefreshCw, Loader2,
  Users, Building2, Globe, Database, FileText, Box,
} from 'lucide-react';
import { useLanguage } from '../../../../components/LanguageContext';
import { useTheme } from '../../../../components/ThemeContext';
import { fetchOntologyTree } from '../../services/knowledgeApi';
import type { OntologyTreeNode, OntologyTreeVo, EntityBrief } from '../../services/knowledgeApi';
import type { LucideIcon } from 'lucide-react';

const STORAGE_KEY = 'ecos_kb_ontology_picker';
const STORAGE_TTL_MS = 30 * 24 * 60 * 60 * 1000; // 30 天
const MAX_VISIBLE_TAGS = 5;

/** 未分组对象的虚拟域 key（domainBreakdown 无条目 / entity.domainId 为空时兜底） */
const UNASSIGNED_KEY = '__unassigned__';

/** 域图标按 domainId 匹配（无 i18n 名称时回退 domainId 文本，按字母序取第一个匹配） */
const DOMAIN_ICONS: Array<{ prefix: string; icon: LucideIcon }> = [
  { prefix: 'org', icon: Building2 },
  { prefix: 'hr', icon: Users },
];

/** 对象类型 entityType 徽章（口径对齐本体工作台 mapEntityToObjectType：MASTER=indigo/Database，其余 teal/FileText，其它 slate/Box） */
const ENTITY_TYPE_BADGE: Record<string, { icon: LucideIcon; cls: string; labelKey: string }> = {
  MASTER: { icon: Database, cls: 'text-indigo-400 bg-indigo-500/15 border-indigo-500/25', labelKey: 'knowledge.datasync.entityType.master' },
  TRANSACTION: { icon: FileText, cls: 'text-teal-400 bg-teal-500/15 border-teal-500/25', labelKey: 'knowledge.datasync.entityType.transaction' },
};
const ENTITY_TYPE_BADGE_DEFAULT: { icon: LucideIcon; cls: string; labelKey: string } = {
  icon: Box, cls: 'text-slate-400 bg-slate-500/15 border-slate-500/25', labelKey: 'knowledge.datasync.entityType.other',
};
/** 未命中表值的 entityType 全部落 DEFAULT（"其它"） */
function entityBadge(entityType: string) {
  return ENTITY_TYPE_BADGE[entityType] || ENTITY_TYPE_BADGE_DEFAULT;
}

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

/** 域图标：按 domainId 匹配（org/hr 细分），其余统一 Globe */
function domainIcon(domainId: string): LucideIcon {
  const hit = DOMAIN_ICONS.find(d => domainId.includes(d.prefix));
  return hit ? hit.icon : Globe;
}

/** 组装本体内各业务域的组条目（domainBreakdown 无覆盖的对象并入未分组） */
function domainEntries(onto: OntologyTreeVo['ontologies'][number]): Array<{ key: string; entities: EntityBrief[] }> {
  const sorted = [...(onto.entities || [])].sort(
    (a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)
  );
  const breakdown = onto.domainBreakdown || {};
  const entries: Array<{ key: string; entities: EntityBrief[] }> = Object.keys(breakdown)
    .sort()
    .map(key => ({
      key,
      entities: [...(breakdown[key] || [])].sort(
        (a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)
      ),
    }));
  const knownCodes = new Set(entries.flatMap(e => e.entities.map(x => x.code)));
  const unassigned = sorted.filter(e => {
    if (knownCodes.has(e.code)) return false;
    return !e.domainId || !breakdown[e.domainId];
  });
  if (unassigned.length > 0) entries.push({ key: UNASSIGNED_KEY, entities: unassigned });
  return entries;
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

  /** 汇总统计：业务域数（各本体 domainBreakdown key 并集，未分组不计）/ 本体数 / 对象类型数 */
  const { domainCounts, totalEntityCount } = useMemo(() => {
    const domainSet = new Set<string>();
    let objects = 0;
    tree.forEach(node => node.ontologies.forEach(onto => {
      Object.keys(onto.domainBreakdown || {}).forEach(k => domainSet.add(k));
      objects += (onto.entities || []).length;
    }));
    return { domainCounts: Array.from(domainSet), totalEntityCount: objects };
  }, [tree]);

  /** i18n 未命中（回显原 key）时落回原文本 */
  const domainLabel = (domainId: string) => {
    const key = `knowledge.datasync.ontology.domains.${domainId}`;
    return t(key) === key ? domainId : t(key);
  };

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
          {/* 顶部汇总：域数 · 本体数 · 对象类型数 */}
          <div className={`text-[10px] font-mono flex items-center gap-1 px-1 pb-0.5 ${styles.muted}`}>
            {t('knowledge.datasync.picker.summary', {
              domains: domainCounts.length,
              ontologies: allIds.length,
              objects: totalEntityCount,
            })}
          </div>
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
            // 第一层 = 真实 domainId（后端 domainBreakdown keys），聚合跨本体
            // 第二层 = 该 domain 下的本体（勾粒度仍"本体 id"级）
            // 第三层 = 对象类型行（icon + name + code + entityType 徽章，只读）
            (() => {
              // 聚合：dom → { ontologies: [...], briefs: [...] }
              const byDom = new Map<string, { ontologies: OntologyTreeVo['ontologies'][number][]; briefs: EntityBrief[] }>();
              tree.forEach(node => node.ontologies.forEach(onto => {
                const br = onto.domainBreakdown || {};
                Object.entries(br).forEach(([domKey, briefs]) => {
                  const rec = byDom.get(domKey) || { ontologies: [], briefs: [] };
                  if (!rec.ontologies.some(o => o.id === onto.id)) rec.ontologies.push(onto);
                  rec.briefs.push(...briefs);
                  byDom.set(domKey, rec);
                });
                // 兜底：若本体的 domainBreakdown 为空但 entities 有值，归入 "未分组"
                if (Object.keys(br).length === 0 && (onto.entities || []).length > 0) {
                  const rec = byDom.get(UNASSIGNED_KEY) || { ontologies: [], briefs: [] };
                  if (!rec.ontologies.some(o => o.id === onto.id)) rec.ontologies.push(onto);
                  rec.briefs.push(...(onto.entities || []));
                  byDom.set(UNASSIGNED_KEY, rec);
                }
              }));
              const domEntries = Array.from(byDom.entries()).sort((a, b) => a[0].localeCompare(b[0]));
              if (domEntries.length === 0) {
                return <p className={`text-[10px] ${styles.muted} text-center py-3`}>{t('knowledge.datasync.picker.empty_hint')}</p>;
              }
              return (
                <>
                  {domEntries.map(([domKey, rec]) => {
                    const isCollapsed = !!collapsed[domKey];
                    const ontoCount = rec.ontologies.length;
                    const selectedInDom = rec.ontologies.filter(o => activeIds.includes(o.id)).length;
                    return (
                      <div key={`${domKey}`}>
                        {/* 第一层：域头（折叠 + 域图标 + 对象类型数统计） */}
                        <div className={`flex items-center gap-1 p-1.5 rounded-lg ${styles.sidebarHoverBg}`}>
                          <button
                            type="button"
                            onClick={() => toggleCollapsed(domKey)}
                            className={`p-0.5 rounded cursor-pointer ${styles.muted}`}
                            title={isCollapsed ? t('knowledge.tree_picker.expand') : t('knowledge.tree_picker.collapse')}
                          >
                            {isCollapsed ? <ChevronRight size={12} /> : <ChevronDown size={12} />}
                          </button>
                          <button
                            type="button"
                            onClick={() => toggleCollapsed(domKey)}
                            className={`flex-1 flex items-center gap-2 rounded-md px-1.5 py-1 cursor-pointer text-left ${styles.badgeBg} ${styles.sidebarText}`}
                          >
                            <span className={`w-5 h-5 rounded flex items-center justify-center shrink-0 ${styles.inputBg} ${styles.muted}`}>
                              {(() => { const DIcon = domainIcon(domKey); return <DIcon size={11} />; })()}
                            </span>
                            <span className="text-xs font-bold truncate">
                              {domainLabel(domKey)}
                            </span>
                            <span className={`ml-auto text-[10px] font-mono ${styles.muted}`}>
                              {selectedInDom}/{ontoCount}
                            </span>
                          </button>
                        </div>
                        {/* 第二层：该域下的本体行（可勾选） + 第三层：本域下的对象类型（只读） */}
                        {!isCollapsed && (
                          <>
                            {rec.ontologies.map(onto => (
                              <button
                                key={`${domKey}-onto-${onto.id}`}
                                type="button"
                                onClick={() => toggleOntology(onto.id)}
                                className={`w-full flex items-center gap-2 pl-7 pr-2 py-1 rounded-md mt-0.5 cursor-pointer text-left ${rowCls(activeIds.includes(onto.id), false)}`}
                              >
                                {activeIds.includes(onto.id) ? <CheckSquare size={12} /> : <Square size={12} />}
                                <Folder size={11} className={`${styles.muted} shrink-0`} />
                                <span className="text-[11px] font-semibold truncate">{onto.name || onto.id}</span>
                              </button>
                            ))}
                            {rec.briefs.map(ent => {
                              const badge = entityBadge(ent.entityType);
                              const BIcon = badge.icon;
                              return (
                                <div
                                  key={`${domKey}-${ent.code}`}
                                  className={`w-full flex items-center gap-2 pl-11 pr-2 py-1 text-left`}
                                >
                                  <span className={`w-5 h-5 rounded flex items-center justify-center shrink-0 border ${styles.inputBg} ${styles.muted}`}>
                                    <BIcon size={11} />
                                  </span>
                                  <div className="flex-1 min-w-0">
                                    <div className={`text-[11px] font-medium truncate ${styles.cardText}`}>
                                      {ent.name || ent.code}
                                    </div>
                                    <div className={`text-[9px] ${styles.muted} font-mono truncate`}>
                                      {ent.code}
                                    </div>
                                  </div>
                                  <span
                                    className={`text-[8px] px-1 py-0.5 rounded border font-medium shrink-0 ${badge.cls} ${styles.sidebarText}`}
                                  >
                                    {t(badge.labelKey)}
                                  </span>
                                </div>
                              );
                            })}
                          </>
                        )}
                      </div>
                    );
                  })}
                </>
              );
            })()
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
