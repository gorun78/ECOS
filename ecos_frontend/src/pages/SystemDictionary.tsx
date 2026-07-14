/**
 * SystemDictionary — 系统字典浏览器
 * 展示 sys_dict 表中的 11 类 49 条字典数据（clearance_level, agent_type, ...）
 */
import React, { useState, useEffect, useCallback } from "react";
import { BookOpen, Loader2, AlertCircle, Tag, Hash, Info } from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { apiFetchData } from "../api";

interface DictItem {
  id: string; dictType: string; dictCode: string;
  dictLabel: string; dictLabelEn: string; sortOrder: number;
  status: string; parentCode: string | null; extValue: string | null;
  createdAt: string; updatedAt: string;
}

interface DictTypeGroup {
  dictType: string; itemCount: number; items: DictItem[];
}

/** Group flat dict items by dictType */
function groupByType(items: DictItem[]): DictTypeGroup[] {
  const map = new Map<string, DictItem[]>();
  for (const item of items) {
    const list = map.get(item.dictType) || [];
    list.push(item);
    map.set(item.dictType, list);
  }
  return Array.from(map.entries()).map(([dictType, items]) => ({
    dictType,
    itemCount: items.length,
    items: items.sort((a, b) => a.sortOrder - b.sortOrder),
  }));
}

export default function SystemDictionary() {
  const { locale } = useLanguage() as any;
  const { styles } = useTheme() as any;
  const isZh = locale !== 'en';

  const [groups, setGroups] = useState<DictTypeGroup[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeType, setActiveType] = useState<string | null>(null);
  const [expandedItem, setExpandedItem] = useState<string | null>(null);

  const loadAll = useCallback(async () => {
    setLoading(true); setError(null);
    try {
      const data = await apiFetchData<DictItem[]>('/api/v1/system/dict');
      setGroups(groupByType(Array.isArray(data) ? data : []));
    } catch (e: any) {
      setError(e.message || 'Failed to load');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { loadAll(); }, [loadAll]);

  const activeGroup = groups.find(g => g.dictType === activeType);
  const totalItems = groups.reduce((s, g) => s + g.itemCount, 0);

  return (
    <div className={`flex h-full ${styles.cardBg}`}>
      {/* Sidebar: dict types */}
      <div className={`w-52 border-r ${styles.sidebarBorder} ${styles.cardBg} p-3 flex flex-col gap-1 overflow-y-auto`}>
        <div className="flex items-center gap-2 px-1 mb-2">
          <BookOpen size={14} className={styles.muted} />
          <h3 className={`text-xs font-semibold ${styles.muted} uppercase`}>
            {isZh ? '字典分类' : 'Dict Types'} ({groups.length})
          </h3>
        </div>
        {loading ? (
          <Loader2 size={18} className="animate-spin text-indigo-400 mx-auto mt-4" />
        ) : (
          groups.map(g => (
            <button key={g.dictType} onClick={() => { setActiveType(g.dictType); setExpandedItem(null); }}
              className={`text-left px-3 py-2 rounded text-sm transition-colors flex items-center justify-between
                ${activeType === g.dictType ? 'bg-indigo-600 text-white' : `${styles.muted} hover:bg-gray-100 dark:hover:bg-gray-800`}`}
            >
              <span className="truncate text-xs">{g.dictType}</span>
              <span className={`text-[10px] px-1.5 py-0.5 rounded-full font-mono
                ${activeType === g.dictType ? 'bg-indigo-500 text-white' : 'bg-gray-200 dark:bg-gray-700'}`}>
                {g.itemCount}
              </span>
            </button>
          ))
        )}
        <div className={`mt-auto pt-3 border-t ${styles.sidebarBorder} text-[10px] ${styles.muted} px-1`}>
          {isZh ? `共 ${totalItems} 条` : `Total ${totalItems} items`}
        </div>
      </div>

      {/* Main: items */}
      <div className="flex-1 p-4 overflow-auto">
        {loading && (
          <div className="flex items-center gap-3 p-12 justify-center">
            <Loader2 size={24} className="animate-spin text-indigo-400" />
            <span className={styles.muted}>{isZh ? '加载中...' : 'Loading...'}</span>
          </div>
        )}

        {error && (
          <div className="flex items-center gap-2 p-4 rounded-lg bg-red-50 dark:bg-red-950 text-red-600 dark:text-red-400">
            <AlertCircle size={18} />
            <span className="text-sm">{error}</span>
          </div>
        )}

        {!loading && !error && activeGroup && (
          <div>
            <div className="flex items-center gap-3 mb-4">
              <Tag size={18} className="text-indigo-500" />
              <h2 className={`text-lg font-semibold ${styles.text}`}>{activeGroup.dictType}</h2>
              <span className={`text-xs px-2 py-0.5 rounded-full ${styles.muted} bg-gray-100 dark:bg-gray-800`}>
                {activeGroup.items.length} {isZh ? '项' : 'items'}
              </span>
            </div>

            <div className="space-y-2">
              {activeGroup.items.map(item => {
                const isExpanded = expandedItem === item.id;
                return (
                  <div key={item.id}
                    className={`rounded-lg border ${styles.sidebarBorder} ${styles.cardBg} overflow-hidden transition-all`}
                  >
                    {/* Row */}
                    <div
                      className="flex items-center gap-3 px-4 py-3 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-800/50"
                      onClick={() => setExpandedItem(isExpanded ? null : item.id)}
                    >
                      <Hash size={14} className="text-slate-400 shrink-0" />
                      <code className="text-xs px-2 py-0.5 rounded bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 font-mono shrink-0">
                        {item.dictCode}
                      </code>
                      <span className={`text-sm font-medium flex-1 ${styles.text}`}>
                        {isZh ? item.dictLabel : (item.dictLabelEn || item.dictLabel)}
                      </span>
                      {item.extValue && (
                        <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 dark:bg-slate-800 text-slate-500">
                          {item.extValue}
                        </span>
                      )}
                      <span className={`text-[10px] px-1.5 py-0.5 rounded-full font-mono
                        ${item.status === 'active' ? 'bg-emerald-100 dark:bg-emerald-900/40 text-emerald-700 dark:text-emerald-300'
                          : 'bg-slate-100 dark:bg-slate-800 text-slate-400'}`}>
                        {item.status}
                      </span>
                    </div>

                    {/* Expanded detail */}
                    {isExpanded && (
                      <div className={`px-4 py-3 border-t ${styles.sidebarBorder} bg-gray-50 dark:bg-gray-900/30 text-xs space-y-1`}>
                        <div className="grid grid-cols-2 gap-x-4 gap-y-1">
                          <div><span className={styles.muted}>ID:</span> <span className="font-mono">{item.id}</span></div>
                          <div><span className={styles.muted}>Sort:</span> {item.sortOrder}</div>
                          {item.parentCode && <div><span className={styles.muted}>Parent:</span> <span className="font-mono">{item.parentCode}</span></div>}
                          <div><span className={styles.muted}>Created:</span> {item.createdAt?.slice(0, 16).replace('T', ' ')}</div>
                          {item.extValue && <div><span className={styles.muted}>Ext:</span> <span className="font-mono text-indigo-500">{item.extValue}</span></div>}
                        </div>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>
        )}

        {!loading && !error && !activeType && (
          <div className="flex flex-col items-center justify-center h-full">
            <BookOpen size={40} className={`${styles.muted} mb-3 opacity-30`} />
            <p className={`text-sm ${styles.muted}`}>
              {isZh ? '← 选择左侧字典分类查看数据' : '← Select a dict type to view items'}
            </p>
          </div>
        )}
      </div>
    </div>
  );
}
