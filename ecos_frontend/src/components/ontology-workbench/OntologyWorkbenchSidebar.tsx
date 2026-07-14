/**
 * OntologyWorkbenchSidebar — 本体工作台侧边栏
 *
 * 从后端 API 加载业务域 (GET /api/v1/ontology/domains) 与对象类型
 * (GET /api/v1/ontology/objects)，以「域 → 对象类型」树形结构展示。
 *
 * 功能：
 *   - 域 / 对象类型两级树，可折叠展开
 *   - 按域名 / 对象 displayName / apiName / description 实时搜索
 *   - 选中域或对象类型时通过回调通知父级
 *   - 加载 / 错误 / 空态完备，支持手动刷新
 *
 * 类型来源：src/types/ontology.ts (OntologyDomain / ObjectType)
 * API 层：  src/api.ts (apiFetchData —— 自动注入 Bearer token、解包 .data)
 * 主题：    深色 #141924，与 OntologyDomainPanel / EntityTreePanel 一致
 *
 * @license Apache-2.0
 */
import React, { useState, useEffect, useMemo, useCallback } from "react";
import {
  Search,
  Network,
  Box,
  ChevronRight,
  ChevronDown,
  Loader2,
  AlertCircle,
  RefreshCw,
  Layers,
  X,
  Database,
  Globe,
  Users,
  ShoppingCart,
  Package,
  Truck,
  Building2,
  CreditCard,
  FileText,
  Calendar,
  MapPin,
  Cpu,
  Shield,
  Zap,
  Activity,
  Tag,
  Briefcase,
} from "lucide-react";
import { apiFetchData } from "../../api";
import type { OntologyDomain, ObjectType } from "../../types/ontology";

// ──────────────────────────────────────────────────────────────
// 图标注册表：ObjectType.icon (lucide 图标名) → 组件
// ──────────────────────────────────────────────────────────────
type IconComp = React.ComponentType<{ size?: number | string; className?: string }>;
const ICON_REGISTRY: Record<string, IconComp> = {
  Box,
  Database,
  Globe,
  Layers,
  Users,
  ShoppingCart,
  Package,
  Truck,
  Building2,
  CreditCard,
  FileText,
  Calendar,
  MapPin,
  Cpu,
  Shield,
  Zap,
  Activity,
  Tag,
  Briefcase,
  Network,
};

/** 根据图标名解析 lucide 组件，找不到时回退到 Box */
function resolveIcon(name?: string): IconComp {
  if (!name) return Box;
  if (ICON_REGISTRY[name]) return ICON_REGISTRY[name];
  const key = Object.keys(ICON_REGISTRY).find(
    (k) => k.toLowerCase() === name.toLowerCase()
  );
  return key ? ICON_REGISTRY[key] : Box;
}

// ──────────────────────────────────────────────────────────────
// 颜色映射（静态类名，兼容 Tailwind v4 内容扫描）
// ──────────────────────────────────────────────────────────────
interface ColorTint {
  bg: string;
  text: string;
  ring: string;
}

const COLOR_TINTS: Record<string, ColorTint> = {
  blue: { bg: "bg-blue-500/15", text: "text-blue-400", ring: "ring-blue-500/30" },
  emerald: { bg: "bg-emerald-500/15", text: "text-emerald-400", ring: "ring-emerald-500/30" },
  amber: { bg: "bg-amber-500/15", text: "text-amber-400", ring: "ring-amber-500/30" },
  purple: { bg: "bg-purple-500/15", text: "text-purple-400", ring: "ring-purple-500/30" },
  rose: { bg: "bg-rose-500/15", text: "text-rose-400", ring: "ring-rose-500/30" },
  indigo: { bg: "bg-indigo-500/15", text: "text-indigo-400", ring: "ring-indigo-500/30" },
  cyan: { bg: "bg-cyan-500/15", text: "text-cyan-400", ring: "ring-cyan-500/30" },
  orange: { bg: "bg-orange-500/15", text: "text-orange-400", ring: "ring-orange-500/30" },
  slate: { bg: "bg-slate-500/15", text: "text-slate-400", ring: "ring-slate-500/30" },
};

const DEFAULT_TINT: ColorTint = COLOR_TINTS.slate;

/**
 * 将 color 字段（可能是 "bg-blue-500"、"blue" 等形式）归一化为颜色名，
 * 再查表返回静态 Tailwind 类。无法识别时回退 slate。
 */
function resolveTint(color?: string): ColorTint {
  if (!color) return DEFAULT_TINT;
  const name = color
    .replace(/^bg-/, "")
    .replace(/^text-/, "")
    .replace(/-(50|100|200|300|400|500|600|700|800|900)$/, "");
  return COLOR_TINTS[name] || DEFAULT_TINT;
}

// ──────────────────────────────────────────────────────────────
// 对象类型状态徽章
// ──────────────────────────────────────────────────────────────
const STATUS_BADGE: Record<string, string> = {
  ACTIVE: "text-emerald-400 bg-emerald-500/10 border-emerald-500/20",
  DRAFT: "text-amber-400 bg-amber-500/10 border-amber-500/20",
  DEPRECATED: "text-rose-400 bg-rose-500/10 border-rose-500/20",
};

// ──────────────────────────────────────────────────────────────
// API 端点
// ──────────────────────────────────────────────────────────────
const DOMAINS_API = "/api/v1/ontology/domains";
const OBJECTS_API = "/api/v1/ontology/objects";

// 未分配 domainId 的对象类型归入此虚拟分组
const UNASSIGNED_KEY = "__unassigned__";

// ──────────────────────────────────────────────────────────────
// Props
// ──────────────────────────────────────────────────────────────
export interface OntologyWorkbenchSidebarProps {
  /** 当前选中的域 ID */
  selectedDomainId?: string | null;
  /** 当前选中的对象类型 ID */
  selectedObjectTypeId?: string | null;
  /** 选中域时回调 */
  onSelectDomain?: (domain: OntologyDomain) => void;
  /** 选中对象类型时回调 */
  onSelectObjectType?: (objectType: ObjectType) => void;
  /** 顶部标题，默认 "本体 / 业务域" */
  title?: string;
  /** 是否显示底部统计栏，默认 true */
  showStats?: boolean;
  /** 额外 className */
  className?: string;
}

// ──────────────────────────────────────────────────────────────
// 组件
// ──────────────────────────────────────────────────────────────
export default function OntologyWorkbenchSidebar({
  selectedDomainId = null,
  selectedObjectTypeId = null,
  onSelectDomain,
  onSelectObjectType,
  title = "本体 / 业务域",
  showStats = true,
  className = "",
}: OntologyWorkbenchSidebarProps) {
  const [domains, setDomains] = useState<OntologyDomain[]>([]);
  const [objects, setObjects] = useState<ObjectType[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [search, setSearch] = useState("");
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});

  // ── 加载域 + 对象类型 ──
  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [d, o] = await Promise.all([
        apiFetchData<OntologyDomain[]>(DOMAINS_API),
        apiFetchData<ObjectType[]>(OBJECTS_API),
      ]);
      setDomains(Array.isArray(d) ? d : []);
      setObjects(Array.isArray(o) ? o : []);
    } catch (e: any) {
      setError(e?.message || "加载本体数据失败");
      setDomains([]);
      setObjects([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  // ── 按域分组对象类型 ──
  const objectsByDomain = useMemo(() => {
    const map: Record<string, ObjectType[]> = {};
    for (const o of objects) {
      const key = o.domainId || UNASSIGNED_KEY;
      (map[key] ||= []).push(o);
    }
    return map;
  }, [objects]);

  // ── 搜索过滤 ──
  const q = search.trim().toLowerCase();
  const matchObject = useCallback(
    (o: ObjectType) =>
      !q ||
      o.displayName.toLowerCase().includes(q) ||
      o.apiName.toLowerCase().includes(q) ||
      (o.description || "").toLowerCase().includes(q),
    [q]
  );

  const filteredDomains = useMemo(() => {
    if (!q) return domains;
    return domains.filter(
      (d) =>
        d.displayName.toLowerCase().includes(q) ||
        d.id.toLowerCase().includes(q) ||
        (objectsByDomain[d.id] || []).some(matchObject)
    );
  }, [domains, objectsByDomain, q, matchObject]);

  // 搜索时自动展开所有域
  const isCollapsed = (id: string) => (q ? false : collapsed[id] ?? false);
  const toggle = (id: string) =>
    setCollapsed((prev) => ({ ...prev, [id]: !prev[id] }));

  const handleDomainClick = (d: OntologyDomain) => {
    toggle(d.id);
    onSelectDomain?.(d);
  };

  const handleObjectClick = (o: ObjectType) => {
    onSelectObjectType?.(o);
  };

  const totalObjects = objects.length;
  const hasUnassigned = (objectsByDomain[UNASSIGNED_KEY] || []).length > 0;

  return (
    <div
      className={`flex flex-col h-full bg-[#141924] text-slate-200 ${className}`}
    >
      {/* ── 标题栏 ── */}
      <div className="px-3 py-3 border-b border-[#1E293B] flex items-center justify-between shrink-0">
        <div className="flex items-center gap-2">
          <Network className="w-4 h-4 text-indigo-400" />
          <div>
            <div className="text-xs font-semibold text-slate-200">{title}</div>
            {showStats && (
              <div className="text-[9px] text-slate-500 font-mono mt-0.5">
                {domains.length} 域 · {totalObjects} 对象类型
              </div>
            )}
          </div>
        </div>
        <button
          onClick={load}
          disabled={loading}
          className="p-1.5 rounded-lg hover:bg-indigo-500/10 text-slate-400 hover:text-indigo-400 transition disabled:opacity-50"
          title="刷新"
        >
          <RefreshCw size={13} className={loading ? "animate-spin" : ""} />
        </button>
      </div>

      {/* ── 搜索框 ── */}
      <div className="px-3 py-2.5 border-b border-[#1E293B] shrink-0">
        <div className="relative">
          <Search
            size={12}
            className="absolute left-2.5 top-1/2 -translate-y-1/2 text-slate-500"
          />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="搜索域或对象类型..."
            className="w-full bg-[#0b0e14] border border-[#1E293B] rounded-lg pl-7 pr-7 py-1.5
              text-xs text-white placeholder:text-slate-600
              focus:outline-none focus:border-indigo-500/40 transition"
          />
          {search && (
            <button
              onClick={() => setSearch("")}
              className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-600 hover:text-slate-400"
            >
              <X size={12} />
            </button>
          )}
        </div>
      </div>

      {/* ── 错误提示 ── */}
      {error && (
        <div className="px-3 py-2 bg-red-500/10 border-b border-red-500/20 text-[10px] text-red-400 flex items-center gap-1.5 shrink-0">
          <AlertCircle size={12} className="shrink-0" />
          <span className="flex-1 truncate">{error}</span>
          <button onClick={() => setError("")} className="hover:text-red-300">
            <X size={11} />
          </button>
        </div>
      )}

      {/* ── 树形列表 ── */}
      <div className="flex-1 overflow-y-auto scrollbar-thin">
        {loading ? (
          <div className="flex items-center justify-center py-10 text-slate-500">
            <Loader2 size={16} className="animate-spin mr-2" />
            <span className="text-[11px]">加载中...</span>
          </div>
        ) : filteredDomains.length === 0 && !hasUnassigned ? (
          <div className="flex flex-col items-center justify-center py-14 text-slate-500 px-4">
            <Layers size={28} className="mb-3 opacity-20" />
            <p className="text-xs">
              {search ? "无匹配结果" : "暂无本体数据"}
            </p>
            {!search && (
              <p className="text-[10px] mt-1 opacity-50">
                请确认后端 /api/v1/ontology 服务可用
              </p>
            )}
          </div>
        ) : (
          <div className="py-1">
            {filteredDomains.map((d) => {
              const objs = (objectsByDomain[d.id] || []).filter(matchObject);
              const col = isCollapsed(d.id);
              const isSel = selectedDomainId === d.id;
              const tint = resolveTint(d.color);
              return (
                <div key={d.id} className="mb-0.5">
                  {/* 域节点 */}
                  <button
                    onClick={() => handleDomainClick(d)}
                    className={`w-full flex items-center gap-1.5 px-2.5 py-2 text-left transition group ${
                      isSel ? "bg-indigo-500/10" : "hover:bg-white/[0.03]"
                    }`}
                  >
                    {col ? (
                      <ChevronRight size={13} className="text-slate-500 shrink-0" />
                    ) : (
                      <ChevronDown size={13} className="text-slate-500 shrink-0" />
                    )}
                    <span
                      className={`w-6 h-6 rounded-md flex items-center justify-center shrink-0 ${tint.bg} ${tint.text}`}
                    >
                      <Globe size={12} />
                    </span>
                    <div className="flex-1 min-w-0">
                      <div
                        className={`text-xs font-medium truncate ${
                          isSel ? "text-indigo-300" : "text-slate-200"
                        }`}
                      >
                        {d.displayName}
                      </div>
                      <div className="text-[9px] text-slate-500 font-mono truncate">
                        {d.id}
                      </div>
                    </div>
                    <span className="text-[9px] text-slate-500 bg-[#1a2030] px-1.5 py-0.5 rounded-full shrink-0">
                      {objs.length}
                    </span>
                  </button>

                  {/* 对象类型列表 */}
                  {!col && objs.length > 0 && (
                    <div className="ml-4 pl-3 border-l border-[#1E293B]">
                      {objs.map((o) => {
                        const Icon = resolveIcon(o.icon);
                        const sel = selectedObjectTypeId === o.id;
                        const oTint = resolveTint(o.color);
                        const badge = STATUS_BADGE[o.status];
                        return (
                          <button
                            key={o.id}
                            onClick={() => handleObjectClick(o)}
                            className={`w-full flex items-center gap-2 px-2.5 py-1.5 text-left transition border-l-2 ${
                              sel
                                ? "bg-indigo-500/10 border-l-indigo-500"
                                : "border-l-transparent hover:bg-white/[0.03]"
                            }`}
                          >
                            <span
                              className={`w-5 h-5 rounded flex items-center justify-center shrink-0 ${oTint.bg} ${oTint.text}`}
                            >
                              <Icon size={11} />
                            </span>
                            <div className="flex-1 min-w-0">
                              <div
                                className={`text-[11px] font-medium truncate ${
                                  sel ? "text-indigo-300" : "text-slate-300"
                                }`}
                              >
                                {o.displayName}
                              </div>
                              <div className="text-[9px] text-slate-500 font-mono truncate">
                                {o.apiName}
                              </div>
                            </div>
                            {badge && (
                              <span
                                className={`text-[8px] px-1 py-0.5 rounded border font-medium shrink-0 ${badge}`}
                              >
                                {o.status}
                              </span>
                            )}
                            <span className="text-[8px] text-slate-500 shrink-0">
                              {o.properties?.length || 0}属性
                            </span>
                          </button>
                        );
                      })}
                    </div>
                  )}
                </div>
              );
            })}

            {/* 未分配域的对象类型 */}
            {!q && hasUnassigned && (
              <div className="mt-2 px-2.5">
                <div className="text-[9px] text-slate-600 font-mono uppercase tracking-wider mb-1">
                  未分组
                </div>
                <div className="ml-1 pl-3 border-l border-[#1E293B]">
                  {objectsByDomain[UNASSIGNED_KEY].map((o) => {
                    const Icon = resolveIcon(o.icon);
                    const sel = selectedObjectTypeId === o.id;
                    return (
                      <button
                        key={o.id}
                        onClick={() => handleObjectClick(o)}
                        className={`w-full flex items-center gap-2 px-2.5 py-1.5 text-left transition border-l-2 ${
                          sel
                            ? "bg-indigo-500/10 border-l-indigo-500"
                            : "border-l-transparent hover:bg-white/[0.03]"
                        }`}
                      >
                        <span className="w-5 h-5 rounded flex items-center justify-center shrink-0 bg-slate-500/15 text-slate-400">
                          <Icon size={11} />
                        </span>
                        <div className="flex-1 min-w-0">
                          <div className="text-[11px] font-medium truncate text-slate-300">
                            {o.displayName}
                          </div>
                          <div className="text-[9px] text-slate-500 font-mono truncate">
                            {o.apiName}
                          </div>
                        </div>
                      </button>
                    );
                  })}
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* ── 底部统计 ── */}
      {showStats && (
        <div className="shrink-0 border-t border-[#1E293B] px-3 py-2 flex items-center justify-between text-[9px] text-slate-500 font-mono">
          <span className="flex items-center gap-1">
            <Database size={10} /> {domains.length} 域
          </span>
          <span className="flex items-center gap-1">
            <Box size={10} /> {totalObjects} 对象类型
          </span>
        </div>
      )}
    </div>
  );
}
