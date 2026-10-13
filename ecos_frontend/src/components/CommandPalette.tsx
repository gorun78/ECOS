/**
 * Command Palette — global search with real-time API results
 * SAFE VERSION — wrapped in try-catch, null-safe throughout
 * W190/C172: 静态导航项 + 类型→导航映射全部派生自 navigation/navConfig 单源，
 * 本文件不再持有私有 STATIC_ITEMS / TYPE_NAV_MAP / hitMeta 表。
 * @license Apache-2.0
 */

import React, { useEffect, useRef, useState, useCallback } from "react";
import { Search, HelpCircle, Loader2 } from "lucide-react";
import { useLanguage } from "./LanguageContext";
import { useTheme } from "./ThemeContext";
import { globalSearch, SearchHit } from "../api";
import { PALETTE_ITEMS, TYPE_META_MAP, TYPE_NAV_MAP, type TypeMeta } from "../navigation/navConfig";

interface CommandPaletteProps {
  isOpen: boolean;
  onClose: () => void;
  /**
   * viewId = 分类页路由根；focusId = 命中的对象 id（报告 §4.1 P1 行64：旧实现
   * 命中项只跳分类页、从不传 hit.id → 搜索永远无法定位对象）。App 侧把 focusId
   * 拼为 `?focus=<id>` 查询参数，目标页据此消费定位；无 id 时保持旧跳转形态。
   */
  onNavigate: (viewId: string, focusId?: string) => void;
}

const FALLBACK_META: TypeMeta = {
  navId: "",
  categoryKey: "palette.category.other",
  icon: Search,
};

/** 命中类型 → 元数据（派生自 navConfig，未知类型走中性兜底）。 */
function hitMeta(type: string): TypeMeta {
  return TYPE_META_MAP[type] ?? FALLBACK_META;
}

export default function CommandPalette({ isOpen, onClose, onNavigate }: CommandPaletteProps) {
  // SAFETY: if anything crashes, return null
  try {
    return <CommandPaletteInner isOpen={isOpen} onClose={onClose} onNavigate={onNavigate} />;
  } catch (e) {
    console.error("[CommandPalette] render error:", e);
    return null;
  }
}

function CommandPaletteInner({ isOpen, onClose, onNavigate }: CommandPaletteProps) {
  const [search, setSearch] = useState("");
  const [hits, setHits] = useState<SearchHit[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  // 键盘导航（报告 §4.1 P1 行64：旧实现无 ↑/↓/Enter 选择）——高亮/激活项索引，
  // 列表 = 命中结果（有结果时）或过滤后的静态导航项（无结果时），与渲染顺序一致。
  const [activeIdx, setActiveIdx] = useState(0);
  const { t } = useLanguage();
  const { styles } = useTheme();

  useEffect(() => { setActiveIdx(0); }, [search, hits.length, isOpen]);

  useEffect(() => {
    if (!isOpen) return;
    const el = document.querySelector(`[data-palette-idx="${activeIdx}"]`);
    el?.scrollIntoView({ block: "nearest" });
  }, [activeIdx, isOpen]);

  useEffect(() => {
    if (isOpen) {
      setSearch("");
      setHits([]);
      setError(null);
      setTimeout(() => { try { inputRef.current?.focus(); } catch {} }, 50);
    }
  }, [isOpen]);

  // Ctrl+K toggle 单源在 App.tsx（报告 §4.1 P2 行66：旧实现在此重复监听同一
  // 键位，双 handler 竞争 open/close）。这里只保留面板内 Escape 关闭。
  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, onClose]);

  const doSearch = useCallback(async (q: string) => {
    if (!q || q.trim().length < 1) { setHits([]); setError(null); return; }
    setLoading(true); setError(null);
    try {
      const results = await globalSearch(q.trim());
      setHits(Array.isArray(results) ? results : []);
    } catch (e: unknown) {
      setError(e instanceof Error ? e.message : String(e) || "Search failed");
      setHits([]);
    } finally {
      setLoading(false);
    }
  }, []);

  const onInput = (val: string) => {
    setSearch(val);
    clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => doSearch(val), 300);
  };

  const navHit = (hit: SearchHit) => {
    const viewId = TYPE_NAV_MAP[hit.type] || hit.type?.toLowerCase() || "";
    // 深链定位（报告 §4.1 P1 行64）：命中对象 id 随导航一并透传，App 侧拼
    // ?focus=<id>；旧实现只跳分类页，搜索结果永远无法定位到具体对象。
    if (viewId) { onNavigate(viewId, hit.id); onClose(); }
  };

  if (!isOpen) return null;

  // 静态导航项 + 分类全部来自 navConfig 派生（W190），文案走 t()（locales/navigation）。
  const q = search.toLowerCase();
  const noQuery = search.trim().length === 0;
  const filteredStatic = noQuery
    ? PALETTE_ITEMS
    : PALETTE_ITEMS.filter(
        (item) =>
          t(item.titleKey).toLowerCase().includes(q) ||
          t(item.descKey).toLowerCase().includes(q) ||
          t(item.categoryKey).toLowerCase().includes(q),
      );

  const categories = Array.from(new Set(PALETTE_ITEMS.map((item) => item.categoryKey)));

  const hasResults = hits.length > 0;
  const hasStatic = filteredStatic.length > 0;

  // ── 键盘导航条目（与渲染顺序一致）：有搜索结果 → 结果列表；否则 → 静态项 ──
  type PaletteEntry = { kind: "hit"; hit: SearchHit } | { kind: "static"; id: string };
  const entries: PaletteEntry[] = hasResults
    ? hits.map((hit) => ({ kind: "hit" as const, hit }))
    : filteredStatic.map((item) => ({ kind: "static" as const, id: item.id }));
  const staticIdxById = new Map<string, number>();
  entries.forEach((entry, i) => {
    if (entry.kind === "static") staticIdxById.set(entry.id, i);
  });

  const activateEntry = (entry: PaletteEntry | undefined): void => {
    if (!entry) return;
    if (entry.kind === "hit") navHit(entry.hit);
    else { onNavigate(entry.id); onClose(); }
  };

  const onInputKeyDown = (e: React.KeyboardEvent<HTMLInputElement>): void => {
    if (e.key === "ArrowDown") {
      e.preventDefault();
      if (entries.length > 0) setActiveIdx((i) => Math.min(i + 1, entries.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      if (entries.length > 0) setActiveIdx((i) => Math.max(i - 1, 0));
    } else if (e.key === "Enter") {
      e.preventDefault();
      if (!loading) activateEntry(entries[activeIdx] ?? entries[0]);
    }
  };

  return (
    <div className={`fixed inset-0 z-50 flex items-start justify-center pt-24 select-none ${styles.overlayBg} backdrop-blur-xs`}>
      <div className={`w-full max-w-2xl overflow-hidden ${styles.cardBg} border ${styles.cardBorder} rounded-xl shadow-2xl animate-fade-in-down ${styles.cardText} font-sans`}>

        {/* Search header */}
        <div className={`flex items-center px-5 py-4 border-b ${styles.cardBorder} ${styles.appBg}`}>
          <Search className={`w-5 h-5 mr-3 ${styles.cardTextMuted} shrink-0`} />
          <input
            ref={inputRef}
            type="text"
            className={`flex-1 text-sm bg-transparent border-0 outline-hidden ${styles.cardText} placeholder:${styles.cardTextMuted}`}
            placeholder={t("palette.placeholder")}
            autoFocus
            value={search}
            onChange={(e) => onInput(e.target.value)}
            onKeyDown={onInputKeyDown}
            role="combobox"
            aria-expanded={entries.length > 0}
            aria-label={t("palette.placeholder")}
          />
          <span className={`px-2 py-0.5 text-[10px] font-mono ${styles.cardTextMuted} ${styles.cardBg} rounded border ${styles.cardBorder}`}>ESC</span>
        </div>

        <div className="max-h-[420px] overflow-y-auto p-3 scrollbar-thin">
          {loading && (
            <div className={`py-8 text-center ${styles.cardTextMuted}`}>
              <Loader2 className="w-5 h-5 mx-auto mb-2 animate-spin" />
              <p className="text-xs">{t("palette.searching")}</p>
            </div>
          )}

          {!loading && error && (
            <div className={`py-8 text-center ${styles.cardTextMuted}`}>
              <HelpCircle className={`w-6 h-6 mx-auto mb-2 ${styles.warningText}`} />
              <p className="text-xs">{t("palette.unavailable")}</p>
            </div>
          )}

          {!loading && hasResults && (
            <div className="space-y-1 mb-3">
              <div className={`px-3 py-1 text-[9px] font-mono tracking-widest uppercase ${styles.accentText} font-bold`}>
                {t("palette.results", { count: hits.length })}
              </div>
              {hits.map((hit, i) => {
                const meta = hitMeta(hit.type);
                const IconC = meta.icon;
                return (
                  <button key={`h-${i}`} type="button" onClick={() => navHit(hit)}
                    data-palette-idx={i}
                    aria-selected={i === activeIdx}
                    className={`w-full text-left flex items-start p-2.5 rounded-lg group transition cursor-pointer ${i === activeIdx ? `${styles.sidebarHoverBg} ${styles.accentBorder} border` : `${styles.sidebarHoverBg} border border-transparent`}`}>
                    <div className={`p-2 mr-3 ${styles.badgeBg} border ${styles.accentBorder} rounded-md`}>
                      <IconC className={`w-4 h-4 ${styles.accentText}`} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className={`text-sm font-semibold ${styles.cardText}`}>{hit.name || hit.id || "—"}</span>
                        <span className={`px-1.5 py-0 text-[9px] font-mono rounded ${styles.appBg} ${styles.cardTextMuted}`}>{hit.type || "?"}</span>
                      </div>
                      <span className={`text-[10px] ${styles.cardTextMuted}`}>{t(meta.categoryKey)}</span>
                    </div>
                  </button>
                );
              })}
            </div>
          )}

          {!loading && !hasResults && hasStatic && (
            <div className="space-y-4">
              {categories.map((catKey) => {
                const catItems = filteredStatic.filter((i) => i.categoryKey === catKey);
                if (catItems.length === 0) return null;
                return (
                  <div key={catKey} className="space-y-1">
                    <div className={`px-3 py-1 text-[9px] font-mono tracking-widest uppercase ${styles.cardTextMuted} font-bold`}>
                      {t(catKey)}
                    </div>
                    {catItems.map((item) => {
                      const IconC = item.icon;
                      const idx = staticIdxById.get(item.id) ?? -1;
                      return (
                        <button key={item.id} type="button" onClick={() => { onNavigate(item.id); onClose(); }}
                          data-palette-idx={idx}
                          aria-selected={idx === activeIdx}
                          className={`w-full text-left flex items-start p-2.5 rounded-lg ${styles.sidebarHoverBg} group transition cursor-pointer ${idx === activeIdx ? `${styles.accentBorder} border` : "border border-transparent"}`}>
                          <div className={`p-2 mr-3 ${styles.appBg} border ${styles.cardBorder} rounded-md`}>
                            <IconC className={`w-4 h-4 ${styles.cardTextMuted}`} />
                          </div>
                          <div className="flex-1 min-w-0">
                            <p className={`text-sm font-semibold ${styles.cardText}`}>{t(item.titleKey)}</p>
                            <p className={`text-xs ${styles.cardTextMuted} truncate mt-1`}>{t(item.descKey)}</p>
                          </div>
                        </button>
                      );
                    })}
                  </div>
                );
              })}
            </div>
          )}

          {!loading && !hasStatic && !hasResults && (
            <div className={`py-12 text-center ${styles.cardTextMuted}`}>
              <HelpCircle className={`w-8 h-8 mx-auto mb-2 ${styles.cardTextMuted}`} />
              <p className="text-sm font-semibold">{t("palette.no_results")}</p>
            </div>
          )}
        </div>

        <div className={`flex items-center justify-between px-5 py-3 border-t ${styles.cardBorder} ${styles.appBg} text-[10px] ${styles.cardTextMuted} font-mono`}>
          <span>{t("palette.hint")}</span>
          <span className={`font-semibold ${styles.cardTextMuted}`}>{t("palette.brand")}</span>
        </div>
      </div>
    </div>
  );
}
