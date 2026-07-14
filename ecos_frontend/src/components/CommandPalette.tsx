/**
 * Command Palette — global search with real-time API results
 * SAFE VERSION — wrapped in try-catch, null-safe throughout
 * @license Apache-2.0
 */

import React, { useEffect, useRef, useState, useCallback } from "react";
import {
  Search, Database, Cpu, Shield, HelpCircle, Activity,
  Globe, Command, GitBranch, Loader2,
} from "lucide-react";
import { useLanguage } from "./LanguageContext";
import { globalSearch, SearchHit } from "../api";

interface CommandPaletteProps {
  isOpen: boolean;
  onClose: () => void;
  onNavigate: (viewId: string) => void;
}

interface PaletteItem {
  id: string;
  title: string;
  titleZh: string;
  description: string;
  descriptionZh: string;
  category: string;
  categoryZh: string;
  icon: React.ComponentType<{ className?: string }>;
}

const STATIC_ITEMS: PaletteItem[] = [
  {
    id: "catalog", title: "Data Catalog / Assets Explorer",
    titleZh: "数据目录 / 资产浏览",
    description: "List, search, filter and inspect production data catalogs.",
    descriptionZh: "列表、搜索、过滤和查看生产数据目录。",
    category: "Data Platform", categoryZh: "数据平台", icon: Database
  },
  { id: "lineage", title: "Data Lineage", titleZh: "数据血缘", description: "Trace data lifecycle.", descriptionZh: "追踪数据生命周期。", category: "Data Platform", categoryZh: "数据平台", icon: GitBranch },
  { id: "pipeline", title: "Pipeline Builder", titleZh: "流水线编排", description: "Visual DAG editor.", descriptionZh: "可视化DAG编辑器。", category: "Data Platform", categoryZh: "数据平台", icon: Activity },
  { id: "ontology", title: "Ontology Manager", titleZh: "本体管理", description: "Model semantic structures.", descriptionZh: "建模语义结构。", category: "Knowledge Layer", categoryZh: "知识层", icon: Globe },
  { id: "agent_studio", title: "Agent Studio", titleZh: "Agent工坊", description: "Build specialist agents.", descriptionZh: "构建专业Agent。", category: "Cognitive Layer", categoryZh: "认知层", icon: Cpu },
  { id: "mission_control", title: "Mission Control", titleZh: "企业总控台", description: "Strategic dashboard.", descriptionZh: "战略仪表盘。", category: "Cognitive Layer", categoryZh: "认知层", icon: Command },
  { id: "security-center", title: "Security Center", titleZh: "安全中心", description: "安全审计、OPA策略、ABAC、数据脱敏统一管理", descriptionZh: "安全审计、OPA策略、ABAC、数据脱敏统一管理", category: "Security & Audit", categoryZh: "安全与审计", icon: Shield }
];

function hitMeta(type: string): { cat: string; catZh: string; icon: React.ComponentType<{ className?: string }> } {
  const m: Record<string, { cat: string; catZh: string; icon: React.ComponentType<{ className?: string }> }> = {
    OntologyEntity: { cat: "Knowledge Layer", catZh: "知识层", icon: Globe },
    Asset: { cat: "Data Platform", catZh: "数据平台", icon: Database },
    Goal: { cat: "Cognitive Layer", catZh: "认知层", icon: Command },
    Scenario: { cat: "Cognitive Layer", catZh: "认知层", icon: Command },
    Object: { cat: "Data Platform", catZh: "数据平台", icon: Database },
    Workflow: { cat: "Data Platform", catZh: "数据平台", icon: Activity },
    Pipeline: { cat: "Data Platform", catZh: "数据平台", icon: Activity },
    Knowledge: { cat: "Knowledge Layer", catZh: "知识层", icon: Globe },
    Agent: { cat: "Cognitive Layer", catZh: "认知层", icon: Cpu },
  };
  return m[type] || { cat: "Other", catZh: "其他", icon: Search };
}

const TYPE_NAV_MAP: Record<string, string> = {
  OntologyEntity: "ontology", Asset: "marketplace", Goal: "mission_control",
  Scenario: "mission_control", Object: "catalog", Workflow: "pipeline",
  Pipeline: "pipeline", Knowledge: "knowledge_graph", Agent: "agent_studio",
};

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
  const debounceRef = useRef<ReturnType<typeof setTimeout>>();
  const { locale } = useLanguage();
  const zh = locale === "zh";

  useEffect(() => {
    if (isOpen) {
      setSearch("");
      setHits([]);
      setError(null);
      setTimeout(() => { try { inputRef.current?.focus(); } catch {} }, 50);
    }
  }, [isOpen]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key === "k") {
        e.preventDefault();
        if (isOpen) onClose();
      }
      if (e.key === "Escape" && isOpen) onClose();
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
    } catch (e: any) {
      setError(e?.message || "Search failed");
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
    if (viewId) { onNavigate(viewId); onClose(); }
  };

  if (!isOpen) return null;

  const noQuery = search.trim().length === 0;
  const filteredStatic = noQuery ? STATIC_ITEMS : STATIC_ITEMS.filter(
    (item) =>
      (zh ? item.titleZh : item.title).toLowerCase().includes(search.toLowerCase()) ||
      (zh ? item.descriptionZh : item.description).toLowerCase().includes(search.toLowerCase()) ||
      (zh ? item.categoryZh : item.category).toLowerCase().includes(search.toLowerCase())
  );

  const categories = [
    { en: "Data Platform", zh: "数据平台" },
    { en: "Knowledge Layer", zh: "知识层" },
    { en: "Cognitive Layer", zh: "认知层" },
    { en: "Security & Audit", zh: "安全与审计" },
  ];

  const hasResults = hits.length > 0;
  const hasStatic = filteredStatic.length > 0;

  return (
    <div className="fixed inset-0 z-50 flex items-start justify-center pt-24 bg-slate-900/40 backdrop-blur-xs">
      <div className="w-full max-w-2xl overflow-hidden bg-white border border-[#E2E8F0] rounded-xl shadow-2xl animate-fade-in-down text-[#1E293B] font-sans">

        {/* Search header */}
        <div className="flex items-center px-5 py-4 border-b border-[#E2E8F0] bg-slate-50">
          <Search className="w-5 h-5 mr-3 text-slate-400 shrink-0" />
          <input
            ref={inputRef}
            type="text"
            className="flex-1 text-sm bg-transparent border-0 outline-hidden text-[#1E293B] placeholder-slate-400"
            placeholder={zh ? "实时搜索…" : "Live search…"}
            autoFocus
            value={search}
            onChange={(e) => onInput(e.target.value)}
          />
          <span className="px-2 py-0.5 text-[10px] font-mono text-slate-400 bg-white rounded border border-[#E2E8F0]">ESC</span>
        </div>

        <div className="max-h-[420px] overflow-y-auto p-3 scrollbar-thin">
          {loading && (
            <div className="py-8 text-center text-slate-400">
              <Loader2 className="w-5 h-5 mx-auto mb-2 animate-spin" />
              <p className="text-xs">{zh ? "搜索中…" : "Searching…"}</p>
            </div>
          )}

          {!loading && error && (
            <div className="py-8 text-center text-slate-400">
              <HelpCircle className="w-6 h-6 mx-auto mb-2 text-amber-400" />
              <p className="text-xs">{zh ? "搜索暂不可用" : "Search unavailable"}</p>
            </div>
          )}

          {!loading && hasResults && (
            <div className="space-y-1 mb-3">
              <div className="px-3 py-1 text-[9px] font-mono tracking-widest uppercase text-indigo-500 font-bold">
                {zh ? `结果 (${hits.length})` : `Results (${hits.length})`}
              </div>
              {hits.map((hit, i) => {
                const meta = hitMeta(hit.type);
                const IconC = meta.icon;
                return (
                  <button key={`h-${i}`} onClick={() => navHit(hit)}
                    className="w-full text-left flex items-start p-2.5 rounded-lg hover:bg-indigo-50 group transition cursor-pointer">
                    <div className="p-2 mr-3 bg-indigo-50 border border-indigo-100 rounded-md">
                      <IconC className="w-4 h-4 text-indigo-400 group-hover:text-indigo-600" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-semibold text-slate-700">{hit.name || hit.id || "—"}</span>
                        <span className="px-1.5 py-0 text-[9px] font-mono rounded bg-slate-100 text-slate-400">{hit.type || "?"}</span>
                      </div>
                      <span className="text-[10px] text-slate-400">{zh ? meta.catZh : meta.cat}</span>
                    </div>
                  </button>
                );
              })}
            </div>
          )}

          {!loading && !hasResults && hasStatic && (
            <div className="space-y-4">
              {categories.map((cat) => {
                const catItems = filteredStatic.filter((i) => i.category === cat.en);
                if (catItems.length === 0) return null;
                return (
                  <div key={cat.en} className="space-y-1">
                    <div className="px-3 py-1 text-[9px] font-mono tracking-widest uppercase text-slate-400 font-bold">
                      {zh ? cat.zh : cat.en}
                    </div>
                    {catItems.map((item) => {
                      const IconC = item.icon;
                      return (
                        <button key={item.id} onClick={() => { onNavigate(item.id); onClose(); }}
                          className="w-full text-left flex items-start p-2.5 rounded-lg hover:bg-slate-50 group transition cursor-pointer">
                          <div className="p-2 mr-3 bg-slate-50 border border-[#E2E8F0] rounded-md">
                            <IconC className="w-4 h-4 text-slate-400 group-hover:text-[#3B82F6]" />
                          </div>
                          <div className="flex-1 min-w-0">
                            <p className="text-sm font-semibold text-slate-700">{zh ? item.titleZh : item.title}</p>
                            <p className="text-xs text-slate-400 truncate mt-1">{zh ? item.descriptionZh : item.description}</p>
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
            <div className="py-12 text-center text-slate-400">
              <HelpCircle className="w-8 h-8 mx-auto mb-2 text-slate-300" />
              <p className="text-sm font-semibold">{zh ? "无结果" : "No results"}</p>
            </div>
          )}
        </div>

        <div className="flex items-center justify-between px-5 py-3 border-t border-[#E2E8F0] bg-slate-50 text-[10px] text-slate-400 font-mono">
          <span>{zh ? "↵ 选择 · ESC 关闭" : "↵ Select · ESC Close"}</span>
          <span className="font-semibold text-slate-500">{zh ? "C2EOS 命令面板" : "C2EOS Command Palette"}</span>
        </div>
      </div>
    </div>
  );
}
