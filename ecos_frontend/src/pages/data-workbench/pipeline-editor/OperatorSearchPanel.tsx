/**
 * OperatorSearchPanel — 算子搜索面板
 * 搜索框 + 分类可折叠列表 (字符串/数值/日期/条件/数组/窗口/转换/哈希)
 * @license Apache-2.0
 */
import React, { useState, useMemo, useCallback, useRef, useEffect } from 'react';
import {
  Search, ChevronDown, ChevronRight,
  Copy, Info, Check, X,
} from 'lucide-react';
import {
  PB_FUNCTIONS,
  type PBFunctionDef,
  type PBFunctionCategory,
  CATEGORY_LABELS,
} from './pbFunctions';

// ─── Props ────────────────────────────────────────────

interface OperatorSearchPanelProps {
  onSelectFunction: (fn: PBFunctionDef) => void;
  onClose?: () => void;
  className?: string;
}

// ─── Category icons as emoji ─────────────────────────

const CATEGORY_ICONS: Record<PBFunctionCategory, string> = {
  string: 'Aa',
  numeric: '#',
  date_time: '📅',
  conditional: '↔️',
  array: '[]',
  window: '▦',
  casting: '⇄',
  hash: '#️⃣',
};

const CATEGORY_COLORS: Record<PBFunctionCategory, string> = {
  string: 'text-emerald-600 bg-emerald-50 border-emerald-200',
  numeric: 'text-blue-600 bg-blue-50 border-blue-200',
  date_time: 'text-purple-600 bg-purple-50 border-purple-200',
  conditional: 'text-orange-600 bg-orange-50 border-orange-200',
  array: 'text-pink-600 bg-pink-50 border-pink-200',
  window: 'text-cyan-600 bg-cyan-50 border-cyan-200',
  casting: 'text-amber-600 bg-amber-50 border-amber-200',
  hash: 'text-slate-600 bg-slate-50 border-slate-200',
};

// ─── Component ────────────────────────────────────────

const OperatorSearchPanel: React.FC<OperatorSearchPanelProps> = ({
  onSelectFunction,
  onClose,
  className = '',
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [collapsedCategories, setCollapsedCategories] = useState<Set<string>>(new Set());
  const [copiedFn, setCopiedFn] = useState<string | null>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);

  // Auto-focus search input
  useEffect(() => {
    searchInputRef.current?.focus();
  }, []);

  // Group functions by category
  const groupedFunctions = useMemo(() => {
    const query = searchQuery.toLowerCase().trim();
    const groups: Record<string, PBFunctionDef[]> = {};

    PB_FUNCTIONS.forEach((fn) => {
      if (
        query === '' ||
        fn.name.toLowerCase().includes(query) ||
        fn.description.toLowerCase().includes(query) ||
        CATEGORY_LABELS[fn.category].includes(query)
      ) {
        if (!groups[fn.category]) groups[fn.category] = [];
        groups[fn.category].push(fn);
      }
    });

    return groups;
  }, [searchQuery]);

  const toggleCategory = useCallback((category: string) => {
    setCollapsedCategories((prev) => {
      const next = new Set(prev);
      if (next.has(category)) next.delete(category);
      else next.add(category);
      return next;
    });
  }, []);

  const handleCopyExample = useCallback((fn: PBFunctionDef) => {
    navigator.clipboard.writeText(fn.example).catch(() => {});
    setCopiedFn(fn.name);
    setTimeout(() => setCopiedFn(null), 2000);
  }, []);

  const handleSelect = useCallback(
    (fn: PBFunctionDef) => {
      onSelectFunction(fn);
    },
    [onSelectFunction]
  );

  return (
    <div className={`flex flex-col bg-white border border-slate-200 rounded-lg shadow-xl overflow-hidden ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-2.5 border-b border-slate-100 bg-slate-50 shrink-0">
        <h3 className="text-xs font-bold text-slate-700 uppercase tracking-wider">
          函数库
        </h3>
        <div className="flex items-center gap-1">
          <span className="text-[10px] text-slate-400">
            {PB_FUNCTIONS.length} 个函数
          </span>
          {onClose && (
            <button
              onClick={onClose}
              className="p-0.5 rounded hover:bg-slate-200 text-slate-400 transition-colors"
            >
              <X size={14} />
            </button>
          )}
        </div>
      </div>

      {/* Search */}
      <div className="px-3 py-2 shrink-0">
        <div className="relative">
          <Search size={13} className="absolute left-2 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            ref={searchInputRef}
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="搜索函数名、描述或分类..."
            className="w-full pl-7 pr-3 py-1.5 text-xs border border-slate-200 rounded-md bg-white text-slate-700 placeholder:text-slate-400 focus:border-purple-400 focus:ring-1 focus:ring-purple-200 outline-none transition-colors"
          />
        </div>
      </div>

      {/* Function list by category */}
      <div className="flex-1 overflow-y-auto">
        {Object.keys(groupedFunctions).length === 0 ? (
          <div className="flex flex-col items-center justify-center py-8 text-slate-400">
            <Search size={24} className="mb-2" />
            <span className="text-xs">未找到匹配的函数</span>
          </div>
        ) : (
          Object.entries(groupedFunctions).map(([category, functions]) => {
            const isCollapsed = collapsedCategories.has(category);
            const catKey = category as PBFunctionCategory;
            return (
              <div key={category} className="border-b border-slate-50 last:border-b-0">
                {/* Category header */}
                <button
                  onClick={() => toggleCategory(category)}
                  className={`flex items-center gap-2 w-full px-3 py-2 text-left hover:bg-slate-50 transition-colors sticky top-0 bg-white z-10`}
                >
                  {isCollapsed ? (
                    <ChevronRight size={12} className="text-slate-400" />
                  ) : (
                    <ChevronDown size={12} className="text-slate-400" />
                  )}
                  <span
                    className={`inline-flex items-center justify-center w-5 h-5 rounded text-[10px] font-bold ${CATEGORY_COLORS[catKey]}`}
                  >
                    {CATEGORY_ICONS[catKey]}
                  </span>
                  <span className="text-xs font-semibold text-slate-700 flex-1">
                    {CATEGORY_LABELS[catKey]}
                  </span>
                  <span className="text-[10px] text-slate-400 tabular-nums">
                    {functions.length}
                  </span>
                </button>

                {/* Function items */}
                {!isCollapsed && (
                  <div className="pb-1">
                    {functions.map((fn) => (
                      <div
                        key={fn.name}
                        className="flex items-start gap-2 px-3 py-1.5 hover:bg-purple-50 transition-colors group cursor-pointer"
                        onClick={() => handleSelect(fn)}
                      >
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-1.5">
                            <code className="text-[11px] font-bold text-purple-700 bg-purple-100 px-1 rounded">
                              {fn.name}
                            </code>
                            <span className="text-[10px] text-slate-400 truncate">
                              {fn.signature}
                            </span>
                          </div>
                          <div className="text-[10px] text-slate-500 mt-0.5 truncate">
                            {fn.description}
                          </div>
                          <div className="text-[9px] text-slate-400 font-mono mt-0.5 truncate">
                            {fn.example}
                          </div>
                        </div>
                        <div className="flex items-center gap-0.5 shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              handleCopyExample(fn);
                            }}
                            className="p-0.5 rounded hover:bg-purple-200 text-slate-400 hover:text-purple-600 transition-colors"
                            title="复制示例"
                          >
                            {copiedFn === fn.name ? (
                              <Check size={12} className="text-green-500" />
                            ) : (
                              <Copy size={12} />
                            )}
                          </button>
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              handleSelect(fn);
                            }}
                            className="p-0.5 rounded hover:bg-purple-200 text-slate-400 hover:text-purple-600 transition-colors"
                            title="插入函数"
                          >
                            <Info size={12} />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>
    </div>
  );
};

export default OperatorSearchPanel;
