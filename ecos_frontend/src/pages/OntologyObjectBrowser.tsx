/**
 * OntologyObjectBrowser — 本体对象浏览器
 *
 * 左侧：OntologyWorkbenchSidebar（域 → 对象类型树）
 * 右侧：选中对象类型的详情（元信息 + 属性表 + 映射）
 *
 * 数据源：
 *   - GET /api/v1/ontology/domains  （域列表，用于解析域名）
 *   - GET /api/v1/ontology/objects  （对象类型列表，由侧边栏加载并透传）
 *
 * 类型来源：src/types/ontology.ts (OntologyDomain / ObjectType / PropertyType)
 * 不引入新依赖，不使用 mockData。
 *
 * @license Apache-2.0
 */
import React, { useState, useEffect, useMemo, useCallback } from "react";
import {
  Box,
  Search,
  Key,
  Hash,
  Type,
  Tag,
  Layers,
  ChevronRight,
  Database,
  Link2,
  Info,
  Loader2,
  AlertCircle,
  X,
  ArrowLeft,
  Table2,
} from "lucide-react";
import OntologyWorkbenchSidebar from "../components/ontology-workbench/OntologyWorkbenchSidebar";
import { apiFetchData } from "../api";
import type {
  OntologyDomain,
  ObjectType,
  PropertyType,
  PropertyDataType,
} from "../types/ontology";

// ──────────────────────────────────────────────────────────────
// 图标 / 颜色解析（与侧边栏保持一致的本地副本，避免循环依赖）
// ──────────────────────────────────────────────────────────────
type IconComp = React.ComponentType<{ size?: number | string; className?: string }>;
const ICON_REGISTRY: Record<string, IconComp> = {
  Box,
  Database,
  Layers,
  Tag,
  Type,
  Hash,
  Table2,
  Link2,
  Info,
};

function resolveIcon(name?: string): IconComp {
  if (!name) return Box;
  if (ICON_REGISTRY[name]) return ICON_REGISTRY[name];
  const key = Object.keys(ICON_REGISTRY).find(
    (k) => k.toLowerCase() === name.toLowerCase()
  );
  return key ? ICON_REGISTRY[key] : Box;
}

interface ColorTint {
  bg: string;
  text: string;
  border: string;
}
const COLOR_TINTS: Record<string, ColorTint> = {
  blue: { bg: "bg-blue-500/15", text: "text-blue-400", border: "border-blue-500/30" },
  emerald: { bg: "bg-emerald-500/15", text: "text-emerald-400", border: "border-emerald-500/30" },
  amber: { bg: "bg-amber-500/15", text: "text-amber-400", border: "border-amber-500/30" },
  purple: { bg: "bg-purple-500/15", text: "text-purple-400", border: "border-purple-500/30" },
  rose: { bg: "bg-rose-500/15", text: "text-rose-400", border: "border-rose-500/30" },
  indigo: { bg: "bg-indigo-500/15", text: "text-indigo-400", border: "border-indigo-500/30" },
  cyan: { bg: "bg-cyan-500/15", text: "text-cyan-400", border: "border-cyan-500/30" },
  orange: { bg: "bg-orange-500/15", text: "text-orange-400", border: "border-orange-500/30" },
  slate: { bg: "bg-slate-500/15", text: "text-slate-400", border: "border-slate-500/30" },
};
const DEFAULT_TINT = COLOR_TINTS.slate;

function resolveTint(color?: string): ColorTint {
  if (!color) return DEFAULT_TINT;
  const name = color
    .replace(/^bg-/, "")
    .replace(/^text-/, "")
    .replace(/-(50|100|200|300|400|500|600|700|800|900)$/, "");
  return COLOR_TINTS[name] || DEFAULT_TINT;
}

const STATUS_BADGE: Record<string, string> = {
  ACTIVE: "text-emerald-400 bg-emerald-500/10 border-emerald-500/30",
  DRAFT: "text-amber-400 bg-amber-500/10 border-amber-500/30",
  DEPRECATED: "text-rose-400 bg-rose-500/10 border-rose-500/30",
};

// 属性数据类型 → 展示标签 + 颜色
const DATATYPE_STYLE: Record<PropertyDataType, string> = {
  string: "text-slate-300 bg-slate-500/10",
  integer: "text-blue-400 bg-blue-500/10",
  decimal: "text-cyan-400 bg-cyan-500/10",
  boolean: "text-purple-400 bg-purple-500/10",
  date: "text-amber-400 bg-amber-500/10",
  timestamp: "text-orange-400 bg-orange-500/10",
  geopoint: "text-emerald-400 bg-emerald-500/10",
};

const DOMAINS_API = "/api/v1/ontology/domains";

// ──────────────────────────────────────────────────────────────
// 属性表行
// ──────────────────────────────────────────────────────────────
function PropertyRow({ prop }: { prop: PropertyType }) {
  const isPK = prop.isPrimaryKey;
  const dtStyle = DATATYPE_STYLE[prop.dataType] || "text-slate-300 bg-slate-500/10";
  return (
    <tr className="border-b border-[#1E293B] last:border-0 hover:bg-white/[0.02] transition">
      <td className="px-3 py-2">
        <div className="flex items-center gap-1.5">
          {isPK && (
            <span title="主键" className="shrink-0">
              <Key size={11} className="text-amber-400" />
            </span>
          )}
          <span className="text-xs font-medium text-slate-200 truncate">
            {prop.displayName}
          </span>
        </div>
        {prop.description && (
          <div className="text-[9px] text-slate-500 mt-0.5 truncate">
            {prop.description}
          </div>
        )}
      </td>
      <td className="px-3 py-2">
        <span className="text-[10px] font-mono text-slate-400">{prop.apiName}</span>
      </td>
      <td className="px-3 py-2">
        <span className={`text-[9px] px-1.5 py-0.5 rounded font-mono ${dtStyle}`}>
          {prop.dataType}
        </span>
      </td>
      <td className="px-3 py-2 text-center">
        {isPK ? (
          <span className="text-[9px] text-amber-400 font-semibold">PK</span>
        ) : (
          <span className="text-[9px] text-slate-600">—</span>
        )}
      </td>
      <td className="px-3 py-2 text-center">
        {prop.sharedPropertyId ? (
          <span className="text-[9px] text-indigo-400 font-mono" title={prop.sharedPropertyId}>
            共享
          </span>
        ) : (
          <span className="text-[9px] text-slate-600">—</span>
        )}
      </td>
    </tr>
  );
}

// ──────────────────────────────────────────────────────────────
// 元信息卡片
// ──────────────────────────────────────────────────────────────
function MetaItem({
  icon,
  label,
  value,
  mono = false,
}: {
  icon: React.ReactNode;
  label: string;
  value: React.ReactNode;
  mono?: boolean;
}) {
  return (
    <div className="bg-[#0f1419] border border-[#1E293B] rounded-lg px-3 py-2">
      <div className="flex items-center gap-1 text-[9px] text-slate-500 uppercase tracking-wider mb-1">
        {icon}
        {label}
      </div>
      <div
        className={`text-xs text-slate-200 truncate ${mono ? "font-mono" : ""}`}
        title={typeof value === "string" ? value : undefined}
      >
        {value || <span className="text-slate-600">—</span>}
      </div>
    </div>
  );
}

// ──────────────────────────────────────────────────────────────
// 主组件
// ──────────────────────────────────────────────────────────────
export default function OntologyObjectBrowser() {
  const [domains, setDomains] = useState<OntologyDomain[]>([]);
  const [domainsLoading, setDomainsLoading] = useState(true);
  const [selected, setSelected] = useState<ObjectType | null>(null);
  const [selectedDomainId, setSelectedDomainId] = useState<string | null>(null);
  const [propSearch, setPropSearch] = useState("");
  const [error, setError] = useState("");

  // ── 加载域（用于解析域名）──
  useEffect(() => {
    let alive = true;
    setDomainsLoading(true);
    apiFetchData<OntologyDomain[]>(DOMAINS_API)
      .then((d) => {
        if (alive) setDomains(Array.isArray(d) ? d : []);
      })
      .catch((e: any) => {
        if (alive) setError(e?.message || "加载业务域失败");
      })
      .finally(() => {
        if (alive) setDomainsLoading(false);
      });
    return () => {
      alive = false;
    };
  }, []);

  const domainName = useMemo(() => {
    if (!selected?.domainId) return "未分组";
    const d = domains.find((x) => x.id === selected.domainId);
    return d?.displayName || selected.domainId;
  }, [selected, domains]);

  // 主键 / 标题属性解析
  const pkProp = useMemo(
    () => selected?.properties.find((p) => p.id === selected.primaryKey) || null,
    [selected]
  );
  const titleProp = useMemo(
    () => selected?.properties.find((p) => p.id === selected.titleProperty) || null,
    [selected]
  );

  // 属性表过滤
  const filteredProps = useMemo(() => {
    if (!selected) return [];
    const q = propSearch.trim().toLowerCase();
    if (!q) return selected.properties;
    return selected.properties.filter(
      (p) =>
        p.displayName.toLowerCase().includes(q) ||
        p.apiName.toLowerCase().includes(q) ||
        p.dataType.toLowerCase().includes(q)
    );
  }, [selected, propSearch]);

  const handleSelectObjectType = useCallback((o: ObjectType) => {
    setSelected(o);
    setSelectedDomainId(o.domainId || null);
    setPropSearch("");
  }, []);

  const handleSelectDomain = useCallback((d: OntologyDomain) => {
    setSelectedDomainId(d.id);
  }, []);

  return (
    <div className="flex-1 flex h-full overflow-hidden bg-[#0f1117] font-sans">
      {/* ═══════ 左侧：侧边栏 ═══════ */}
      <div className="w-[280px] min-w-[220px] border-r border-[#1E293B] shrink-0">
        <OntologyWorkbenchSidebar
          selectedDomainId={selectedDomainId}
          selectedObjectTypeId={selected?.id || null}
          onSelectDomain={handleSelectDomain}
          onSelectObjectType={handleSelectObjectType}
        />
      </div>

      {/* ═══════ 右侧：详情区 ═══════ */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* 顶部栏 */}
        <div className="shrink-0 flex items-center gap-2 px-4 py-2.5 border-b border-[#1E293B] bg-[#141924]">
          <Box className="w-3.5 h-3.5 text-indigo-400" />
          <span className="text-[11px] font-medium text-slate-300">对象浏览器</span>
          {selected && (
            <>
              <ChevronRight size={12} className="text-slate-600" />
              <span className="text-[11px] text-slate-500">{domainName}</span>
              <ChevronRight size={12} className="text-slate-600" />
              <span className="text-[11px] font-medium text-slate-200 truncate">
                {selected.displayName}
              </span>
            </>
          )}
          <span className="ml-auto text-[10px] text-slate-600 font-mono">
            {domainsLoading ? "加载域..." : `${domains.length} 域`}
          </span>
        </div>

        {/* 错误浮层 */}
        {error && (
          <div className="mx-4 mt-3 bg-red-500/10 border border-red-500/20 text-red-400 rounded-lg px-3 py-2 text-xs flex items-center gap-2">
            <AlertCircle size={14} className="shrink-0" />
            <span className="flex-1">{error}</span>
            <button onClick={() => setError("")} className="hover:text-red-300">
              <X size={12} />
            </button>
          </div>
        )}

        {/* 内容区 */}
        <div className="flex-1 overflow-y-auto scrollbar-thin">
          {!selected ? (
            <EmptyState />
          ) : (
            <div className="p-4 space-y-4">
              {/* ── 对象类型头部 ── */}
              <ObjectHeader
                obj={selected}
                domainName={domainName}
                pkProp={pkProp?.displayName}
                titleProp={titleProp?.displayName}
              />

              {/* ── 元信息网格 ── */}
              <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
                <MetaItem
                  icon={<Layers size={10} />}
                  label="所属域"
                  value={domainName}
                />
                <MetaItem
                  icon={<Key size={10} />}
                  label="主键属性"
                  value={pkProp?.displayName || selected.primaryKey}
                  mono={!pkProp}
                />
                <MetaItem
                  icon={<Type size={10} />}
                  label="标题属性"
                  value={titleProp?.displayName || selected.titleProperty}
                  mono={!titleProp}
                />
                <MetaItem
                  icon={<Link2 size={10} />}
                  label="实现接口"
                  value={
                    selected.interfaces?.length
                      ? `${selected.interfaces.length} 个`
                      : "无"
                  }
                />
              </div>

              {/* ── 属性表 ── */}
              <div className="bg-[#141924] border border-[#1E293B] rounded-xl overflow-hidden">
                <div className="flex items-center gap-2 px-3 py-2.5 border-b border-[#1E293B]">
                  <Table2 size={13} className="text-indigo-400" />
                  <span className="text-xs font-semibold text-slate-200">
                    属性定义
                  </span>
                  <span className="text-[10px] text-slate-500">
                    {selected.properties.length} 项
                  </span>
                  <div className="ml-auto relative">
                    <Search
                      size={11}
                      className="absolute left-2 top-1/2 -translate-y-1/2 text-slate-500"
                    />
                    <input
                      value={propSearch}
                      onChange={(e) => setPropSearch(e.target.value)}
                      placeholder="过滤属性..."
                      className="w-44 bg-[#0b0e14] border border-[#1E293B] rounded-md pl-6 pr-2 py-1 text-[11px] text-slate-200 placeholder:text-slate-600 focus:outline-none focus:border-indigo-500/40 transition"
                    />
                  </div>
                </div>
                <div className="overflow-x-auto">
                  <table className="w-full text-left">
                    <thead>
                      <tr className="text-[9px] uppercase tracking-wider text-slate-500 border-b border-[#1E293B]">
                        <th className="px-3 py-2 font-medium">属性名</th>
                        <th className="px-3 py-2 font-medium">API 名</th>
                        <th className="px-3 py-2 font-medium">类型</th>
                        <th className="px-3 py-2 font-medium text-center">主键</th>
                        <th className="px-3 py-2 font-medium text-center">共享</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredProps.length === 0 ? (
                        <tr>
                          <td
                            colSpan={5}
                            className="px-3 py-8 text-center text-[11px] text-slate-500"
                          >
                            {selected.properties.length === 0
                              ? "该对象类型暂无属性定义"
                              : "无匹配属性"}
                          </td>
                        </tr>
                      ) : (
                        filteredProps.map((p) => (
                          <PropertyRow key={p.id} prop={p} />
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* ── 数据映射（可选）── */}
              {selected.mapping && (
                <div className="bg-[#141924] border border-[#1E293B] rounded-xl overflow-hidden">
                  <div className="flex items-center gap-2 px-3 py-2.5 border-b border-[#1E293B]">
                    <Database size={13} className="text-indigo-400" />
                    <span className="text-xs font-semibold text-slate-200">
                      数据集映射
                    </span>
                    <span className="text-[10px] text-slate-500 font-mono truncate">
                      {selected.mapping.datasetId}
                    </span>
                  </div>
                  <div className="p-3">
                    <div className="text-[9px] text-slate-500 uppercase tracking-wider mb-2">
                      属性 → 列映射（{Object.keys(selected.mapping.propertyMappings).length} 项）
                    </div>
                    <div className="grid grid-cols-2 gap-1.5">
                      {Object.entries(selected.mapping.propertyMappings).map(
                        ([propId, col]) => {
                          const pn =
                            selected.properties.find((p) => p.id === propId)
                              ?.apiName || propId;
                          return (
                            <div
                              key={propId}
                              className="flex items-center gap-1.5 bg-[#0f1419] border border-[#1E293B] rounded px-2 py-1.5"
                            >
                              <span className="text-[10px] font-mono text-slate-400 truncate">
                                {pn}
                              </span>
                              <ArrowLeft size={9} className="text-slate-600 shrink-0" />
                              <span className="text-[10px] font-mono text-indigo-400 truncate">
                                {col}
                              </span>
                            </div>
                          );
                        }
                      )}
                    </div>
                  </div>
                </div>
              )}

              {/* ── 接口列表（可选）── */}
              {selected.interfaces && selected.interfaces.length > 0 && (
                <div className="bg-[#141924] border border-[#1E293B] rounded-xl overflow-hidden">
                  <div className="flex items-center gap-2 px-3 py-2.5 border-b border-[#1E293B]">
                    <Link2 size={13} className="text-indigo-400" />
                    <span className="text-xs font-semibold text-slate-200">
                      实现接口
                    </span>
                  </div>
                  <div className="p-3 flex flex-wrap gap-1.5">
                    {selected.interfaces.map((iid) => (
                      <span
                        key={iid}
                        className="text-[10px] font-mono text-indigo-300 bg-indigo-500/10 border border-indigo-500/20 rounded px-2 py-1"
                      >
                        {iid}
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ──────────────────────────────────────────────────────────────
// 对象类型头部
// ──────────────────────────────────────────────────────────────
function ObjectHeader({
  obj,
  domainName,
  pkProp,
  titleProp,
}: {
  obj: ObjectType;
  domainName: string;
  pkProp?: string;
  titleProp?: string;
}) {
  const Icon = resolveIcon(obj.icon);
  const tint = resolveTint(obj.color);
  const badge = STATUS_BADGE[obj.status];
  return (
    <div className="bg-[#141924] border border-[#1E293B] rounded-xl p-4">
      <div className="flex items-start gap-3">
        <div
          className={`w-11 h-11 rounded-lg flex items-center justify-center shrink-0 ${tint.bg} ${tint.text} ${tint.border} border`}
        >
          <Icon size={20} />
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <h2 className="text-sm font-bold text-white truncate">
              {obj.displayName}
            </h2>
            {badge && (
              <span
                className={`text-[9px] px-1.5 py-0.5 rounded border font-semibold ${badge}`}
              >
                {obj.status}
              </span>
            )}
          </div>
          <div className="text-[10px] text-slate-500 font-mono mt-0.5">
            {obj.apiName} · {obj.id}
          </div>
          {obj.description && (
            <p className="text-[11px] text-slate-400 mt-2 leading-relaxed">
              {obj.description}
            </p>
          )}
          <div className="flex items-center gap-3 mt-2 text-[9px] text-slate-500">
            <span className="flex items-center gap-1">
              <Layers size={9} /> {domainName}
            </span>
            {pkProp && (
              <span className="flex items-center gap-1">
                <Key size={9} /> PK: {pkProp}
              </span>
            )}
            {titleProp && (
              <span className="flex items-center gap-1">
                <Type size={9} /> 标题: {titleProp}
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

// ──────────────────────────────────────────────────────────────
// 空态
// ──────────────────────────────────────────────────────────────
function EmptyState() {
  return (
    <div className="h-full flex flex-col items-center justify-center text-slate-500 px-6">
      <div className="w-16 h-16 rounded-2xl bg-indigo-500/10 flex items-center justify-center mb-4">
        <Box size={28} className="text-indigo-400/60" />
      </div>
      <p className="text-sm font-medium text-slate-400">未选择对象类型</p>
      <p className="text-[11px] text-slate-600 mt-1 text-center max-w-xs">
        从左侧侧边栏选择一个业务域并展开，点击其中的对象类型以查看其属性定义与数据映射。
      </p>
    </div>
  );
}
