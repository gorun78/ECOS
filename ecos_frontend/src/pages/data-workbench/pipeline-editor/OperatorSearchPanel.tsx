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
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';

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

const CATEGORY_COLORS_FN = (styles: Record<string, string>): Record<PBFunctionCategory, string> => ({
  string: `${styles.successText} ${styles.successBg} ${styles.successBorder}`,
  numeric: `${styles.accentText} ${styles.infoBg} ${styles.accentBorder}`,
  date_time: `${styles.infoText} ${styles.infoBg} ${styles.infoBorder}`,
  conditional: `${styles.warningText} ${styles.warningBg} ${styles.warningBorder}`,
  array: `${styles.infoText} ${styles.infoBg} ${styles.infoBorder}`,
  window: `${styles.infoText} ${styles.infoBg} ${styles.infoBorder}`,
  casting: `${styles.warningText} ${styles.warningBg} ${styles.warningBorder}`,
  hash: `${styles.cardTextMuted} ${styles.cardBg} ${styles.cardBorder}`,
});

// ─── Component ────────────────────────────────────────

const OperatorSearchPanel: React.FC<OperatorSearchPanelProps> = ({
  onSelectFunction,
  onClose,
  className = '',
}) => {
  const { t } = useLanguage();
  const { styles } = useTheme();
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
        t(fn.description).toLowerCase().includes(query) ||
        t(CATEGORY_LABELS[fn.category]).includes(query)
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
    <div className={`flex flex-col ${styles.cardBg} border ${styles.cardBorder} rounded-lg shadow-xl overflow-hidden ${className}`}>
      {/* Header */}
      <div className={`flex items-center justify-between px-3 py-2.5 border-b ${styles.cardBorder} ${styles.cardBg} shrink-0`}>
        <h3 className={`text-xs font-bold ${styles.cardTextMuted} uppercase tracking-wider`}>
          {t('databench.pipeline.functionLibrary')}
        </h3>
        <div className="flex items-center gap-1">
          <span className={`text-[10px] ${styles.cardTextMuted}`}>
            {t('databench.pipeline.functionCount', { count: PB_FUNCTIONS.length })}
          </span>
          {onClose && (
            <button
              onClick={onClose}
              className={`p-0.5 rounded hover:${styles.sidebarBg} ${styles.cardTextMuted} transition-colors`}
            >
              <X size={14} />
            </button>
          )}
        </div>
      </div>

      {/* Search */}
      <div className="px-3 py-2 shrink-0">
        <div className="relative">
          <Search size={13} className={`absolute left-2 top-1/2 -translate-y-1/2 ${styles.cardTextMuted}`} />
          <input
            ref={searchInputRef}
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder={t('databench.pipeline.searchPlaceholder')}
            className={`w-full pl-7 pr-3 py-1.5 text-xs border ${styles.cardBorder} rounded-md ${styles.cardBg} ${styles.cardTextMuted} placeholder:${styles.cardTextMuted} focus:${styles.infoBorder} focus:ring-1 focus:${styles.infoBorder} outline-none transition-colors`}
          />
        </div>
      </div>

      {/* Function list by category */}
      <div className="flex-1 overflow-y-auto">
        {Object.keys(groupedFunctions).length === 0 ? (
          <div className={`flex flex-col items-center justify-center py-8 ${styles.cardTextMuted}`}>
            <Search size={24} className="mb-2" />
            <span className="text-xs">{t('databench.pipeline.noFunctionsFound')}</span>
          </div>
        ) : (
          Object.entries(groupedFunctions).map(([category, functions]) => {
            const isCollapsed = collapsedCategories.has(category);
            const catKey = category as PBFunctionCategory;
            return (
              <div key={category} className={`border-b ${styles.cardBorder} last:border-b-0`}>
                {/* Category header */}
                <button
                  onClick={() => toggleCategory(category)}
                  className={`flex items-center gap-2 w-full px-3 py-2 text-left hover:${styles.cardBg} transition-colors sticky top-0 ${styles.cardBg} z-10`}
                >
                  {isCollapsed ? (
                    <ChevronRight size={12} className={`${styles.cardTextMuted}`} />
                  ) : (
                    <ChevronDown size={12} className={`${styles.cardTextMuted}`} />
                  )}
                  <span
                    className={`inline-flex items-center justify-center w-5 h-5 rounded text-[10px] font-bold ${CATEGORY_COLORS_FN(styles as unknown as Record<string, string>)[catKey]}`}
                  >
                    {CATEGORY_ICONS[catKey]}
                  </span>
                  <span className={`text-xs font-semibold ${styles.cardTextMuted} flex-1`}>
                    {t(CATEGORY_LABELS[catKey])}
                  </span>
                  <span className={`text-[10px] ${styles.cardTextMuted} tabular-nums`}>
                    {functions.length}
                  </span>
                </button>

                {/* Function items */}
                {!isCollapsed && (
                  <div className="pb-1">
                    {functions.map((fn) => (
                      <div
                        key={fn.name}
                        className={`flex items-start gap-2 px-3 py-1.5 hover:${styles.infoBg} transition-colors group cursor-pointer`}
                        onClick={() => handleSelect(fn)}
                      >
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-1.5">
                            <code className={`text-[11px] font-bold ${styles.infoText} ${styles.infoBg} px-1 rounded`}>
                              {fn.name}
                            </code>
                            <span className={`text-[10px] ${styles.cardTextMuted} truncate`}>
                              {fn.signature}
                            </span>
                          </div>
                          <div className={`text-[10px] ${styles.muted} mt-0.5 truncate`}>
                            {t(fn.description)}
                          </div>
                          <div className={`text-[9px] ${styles.cardTextMuted} font-mono mt-0.5 truncate`}>
                            {fn.example}
                          </div>
                        </div>
                        <div className="flex items-center gap-0.5 shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              handleCopyExample(fn);
                            }}
                            className={`p-0.5 rounded hover:${styles.infoBg} ${styles.cardTextMuted} hover:${styles.infoText} transition-colors`}
                            title={t('databench.pipeline.copyExample')}
                          >
                            {copiedFn === fn.name ? (
                              <Check size={12} className={`${styles.successText}`} />
                            ) : (
                              <Copy size={12} />
                            )}
                          </button>
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              handleSelect(fn);
                            }}
                            className={`p-0.5 rounded hover:${styles.infoBg} ${styles.cardTextMuted} hover:${styles.infoText} transition-colors`}
                            title={t('databench.pipeline.insertFunction')}
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
