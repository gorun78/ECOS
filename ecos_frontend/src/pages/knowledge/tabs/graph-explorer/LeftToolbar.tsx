/**
 * GraphExplorerTab 左侧工具栏（自 GraphExplorerTab 拆出，纯搬迁，行为不变）。
 * 搜索 / 路径查找 / graph domain 过滤 / nav domain 切换 / 业务域多选 / 全量加载 / 邻居深度 / 图例。
 */
import {
  Search, ArrowRight, Loader2, GitBranch, Network, CornerDownRight,
} from 'lucide-react';
import type { NavCategoryVO } from '../../../../services/knowledgeNavApi';
import type { GraphNode } from './types';

export interface LeftToolbarProps {
  styles: Record<string, string>;
  t: (key: string) => string;
  // 搜索
  searchQuery: string;
  onSearchQueryChange: (v: string) => void;
  onSearchSubmit: () => void;
  onSearchInputFocus: () => void;
  showSearchResults: boolean;
  searchResults: GraphNode[];
  onSearchResultClick: (id: string) => void;
  // 路径查找
  pathSource: string;
  pathTarget: string;
  onPathSourceChange: (v: string) => void;
  onPathTargetChange: (v: string) => void;
  onComputePath: () => void;
  onPathMissing: () => void;
  isComputingPath: boolean;
  // graph domain 过滤
  domainFilter: string;
  onDomainChange: (d: string) => void;
  // nav domain 切换（kb_nav_category.domain，与 graph domainFilter 区分）
  navDomain: string;
  onNavDomainChange: (d: string) => void;
  navDomains: string[];
  // 业务域多选
  navCategories: NavCategoryVO[];
  selectedCategoryIds: string[];
  onCategoryToggle: (id: string) => void;
  // 全量加载 / 加载态
  onLoadFullGraph: () => void;
  isLoading: boolean;
  // 邻居深度
  neighborDegree: number;
  onNeighborDegreeChange: (n: number) => void;
}

export function LeftToolbar({
  styles,
  t,
  searchQuery,
  onSearchQueryChange,
  onSearchSubmit,
  onSearchInputFocus,
  showSearchResults,
  searchResults,
  onSearchResultClick,
  pathSource,
  pathTarget,
  onPathSourceChange,
  onPathTargetChange,
  onComputePath,
  onPathMissing,
  isComputingPath,
  domainFilter,
  onDomainChange,
  navDomain,
  onNavDomainChange,
  navDomains,
  navCategories,
  selectedCategoryIds,
  onCategoryToggle,
  onLoadFullGraph,
  isLoading,
  neighborDegree,
  onNeighborDegreeChange,
}: LeftToolbarProps) {
  const DOMAIN_OPTIONS = [
    { value: '', label: t('knowledge.graph.all') },
    { value: 'ontology', label: t('knowledge.graph.ontologyDomain') },
    { value: 'data', label: t('knowledge.graph.dataDomain') },
    { value: 'business', label: t('knowledge.graph.businessDomain') },
  ];

  return (
    <div className={`w-56 border-r ${styles.cardBorder} flex flex-col shrink-0 p-3 space-y-3 ${styles.appBg} z-10 relative`}>
      {/* Search — full-text graph search */}
      <div className="space-y-1.5 relative">
        <label className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase tracking-wider`}>{t('knowledge.graph.fullTextSearch')}</label>
        <div className="flex gap-1.5">
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => onSearchQueryChange(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && onSearchSubmit()}
            onFocus={onSearchInputFocus}
            placeholder={t('knowledge.graph.searchPlaceholder')}
            className={`flex-1 px-2.5 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded-lg ${styles.inputText} placeholder:${styles.muted} outline-none focus:border-blue-500`}
          />
          <button type="button"
            onClick={onSearchSubmit}
            className="px-2 py-1.5 bg-blue-600 hover:bg-blue-500 text-white rounded-lg cursor-pointer transition"
          >
            <Search size={13} />
          </button>
        </div>
        {/* Search results dropdown */}
        {showSearchResults && searchResults.length > 0 && (
          <div className={`${styles.sidebarBg} border ${styles.sidebarBorder} rounded-lg shadow-xl max-h-48 overflow-y-auto`}>
            {searchResults.map((r) => (
              <button type="button"
                key={r.id}
                onClick={() => onSearchResultClick(r.id)}
                className={`w-full text-left px-2.5 py-1.5 text-[11px] ${styles.sidebarText} ${styles.sidebarHoverBg} border-b ${styles.cardBorder} last:border-0 flex items-center gap-2 transition cursor-pointer`}
              >
                <CornerDownRight size={10} className="text-blue-400 shrink-0" />
                <span className="truncate">{r.label}</span>
                <span className={`text-[9px] ${styles.muted} shrink-0 ml-auto`}>{r.type}</span>
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Path finder — inline inputs */}
      <div className="space-y-1.5">
        <label className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase tracking-wider`}>{t('knowledge.graph.pathFinder')}</label>
        <div className="flex flex-wrap gap-1 items-center">
          <input
            type="text"
            value={pathSource}
            onChange={(e) => onPathSourceChange(e.target.value)}
            placeholder={t('knowledge.graph.pathSourcePlaceholder')}
            className={`w-[70px] px-2 py-1 text-[10px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} placeholder:${styles.muted} outline-none focus:border-amber-500`}
          />
          <ArrowRight size={10} className={`${styles.muted} shrink-0`} />
          <input
            type="text"
            value={pathTarget}
            onChange={(e) => onPathTargetChange(e.target.value)}
            placeholder={t('knowledge.graph.pathTargetPlaceholder')}
            className={`w-[70px] px-2 py-1 text-[10px] ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} placeholder:${styles.muted} outline-none focus:border-amber-500`}
          />
          <button type="button"
            onClick={() => { if (pathSource && pathTarget) onComputePath(); else onPathMissing(); }}
            disabled={isComputingPath}
            className="px-2 py-1 bg-amber-600 hover:bg-amber-500 text-white rounded text-[10px] font-bold cursor-pointer transition disabled:opacity-50"
          >
            {isComputingPath ? <Loader2 size={10} className="animate-spin" /> : <GitBranch size={10} />}
          </button>
        </div>
      </div>

      {/* Domain Filter */}
      <div className="space-y-1.5">
        <label className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase tracking-wider`}>{t('knowledge.graph.domainFilter')}</label>
        <select
          value={domainFilter}
          onChange={(e) => onDomainChange(e.target.value)}
          className={`w-full px-2.5 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded-lg ${styles.inputText} outline-none focus:border-blue-500 cursor-pointer`}
        >
          {DOMAIN_OPTIONS.map(opt => (
            <option key={opt.value} value={opt.value}>{opt.label}</option>
          ))}
        </select>
      </div>

      {/* PMO-C T2 — nav domain 切换（kb_nav_category.domain，区别于上方 graph domainFilter） */}
      <div className="space-y-1.5">
        <label className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase tracking-wider`}>
          {t('knowledge.nav.domain_filter_label')}
        </label>
        <select
          value={navDomain}
          onChange={(e) => onNavDomainChange(e.target.value)}
          className={`w-full px-2.5 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded-lg ${styles.inputText} outline-none focus:border-blue-500 cursor-pointer`}
        >
          {!navDomains.includes('default') && (
            <option value="default">{t('knowledge.nav.domain_default')}</option>
          )}
          {navDomains.map(d => (
            <option key={d} value={d}>{d === 'default' ? t('knowledge.nav.domain_default') : d}</option>
          ))}
        </select>
      </div>

      {/* PMO-B T3 — 按业务域过滤（多 checkbox 单选可叠加） */}
      <div className="space-y-1.5">
        <label className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase tracking-wider`}>
          {t('knowledge.nav.nav_filter_pair')}
          <span className="ml-1 normal-case font-mono text-emerald-600">
            {selectedCategoryIds.length > 0 ? `(${selectedCategoryIds.length})` : ''}
          </span>
        </label>
        {navCategories.length === 0 ? (
          <p className={`text-[9px] ${styles.muted}`}>{t('knowledge.nav.tree_empty')}</p>
        ) : (
          <div className={`max-h-32 overflow-y-auto space-y-1 pr-1 border rounded-lg p-1.5 ${styles.sidebarBg}`}>
            {navCategories.slice(0, 15).map(cat => (
              <label
                key={cat.id}
                className={`flex items-center gap-1.5 px-1.5 py-0.5 rounded hover:bg-blue-50 cursor-pointer text-[10px] ${styles.muted} transition`}
              >
                <input
                  type="checkbox"
                  checked={selectedCategoryIds.includes(cat.id)}
                  onChange={() => onCategoryToggle(cat.id)}
                  className="h-3 w-3 cursor-pointer"
                />
                <span className="truncate flex-1">{cat.name}</span>
                <span className={`text-[8px] ${styles.muted} shrink-0`}>({cat.articleCount})</span>
              </label>
            ))}
          </div>
        )}
      </div>

      {/* Load Full Graph */}
      <button type="button"
        onClick={onLoadFullGraph}
        disabled={isLoading}
        className={`w-full px-3 py-2 text-[11px] font-bold ${styles.sidebarBg} ${styles.sidebarHoverBg} ${styles.sidebarText} rounded-lg border ${styles.cardBorder} flex items-center justify-center gap-2 transition cursor-pointer disabled:opacity-50`}
      >
        {isLoading ? <Loader2 size={12} className="animate-spin" /> : <Network size={12} />}
        {t('knowledge.graph.loadFullGraph')}
      </button>

      {/* Neighbor Expand */}
      <div className="space-y-1.5">
        <label className={`text-[10px] font-bold ${styles.cardTextMuted} uppercase tracking-wider`}>
          {t('knowledge.graph.neighborDegree')}
        </label>
        <input
          type="range"
          aria-label={t('knowledge.graph.neighborDegree')}
          min={1}
          max={3}
          value={neighborDegree}
          onChange={(e) => onNeighborDegreeChange(parseInt(e.target.value))}
          className="w-full accent-blue-500 cursor-pointer"
        />
        <div className={`flex justify-between text-[10px] ${styles.muted}`}>
          <span>1</span>
          <span className="font-bold text-blue-400">{neighborDegree}</span>
          <span>3</span>
        </div>
      </div>

      {/* Legend */}
      <div className={`mt-auto p-2.5 ${styles.sidebarBg} rounded-lg border ${styles.cardBorder} space-y-1.5`}>
        <span className={`text-[9px] font-bold ${styles.muted} uppercase`}>{t('knowledge.graph.legend')}</span>
        <div className={`space-y-1 text-[10px] ${styles.cardTextMuted}`}>
          <div className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-blue-500" /> {t('knowledge.graph.dataDomain')}
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-emerald-500" /> {t('knowledge.graph.ontologyDomain')}
          </div>
          <div className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-amber-500" /> {t('knowledge.graph.businessDomain')}
          </div>
        </div>
      </div>
    </div>
  );
}
