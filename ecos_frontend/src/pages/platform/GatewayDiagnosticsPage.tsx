/**
 * 网关诊断 · 主入口路由页 (L3+ 权限)
 * @license SPDX-License-Identifier: Apache-2.0
 *
 * 独立顶级路由 /gateway-diagnostics。读内部 ?tab= 参数（filters|routes|anonymous|editions）。
 * 五个版块（均取自一次 GET /api/v1/system/gateway-diagnostics）：
 *   产物 × 承流态 / 过滤器链 ORDER 对照 / 路由清单摘要（仅汇总计数，无逐条明细）/ 匿名端点 / 版本能力矩阵。
 * 403 → 友好展示「需要 L3 及以上权限 / requires L3+」。
 */

import React, { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  Activity,
  ArrowRightLeft,
  Boxes,
  FileText,
  GitBranch,
  Globe,
  Info,
  Loader2,
  RefreshCw,
  ShieldAlert,
  Server,
  Layers,
} from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { isNoAccessError } from "../../api";
import {
  fetchGatewayDiagnostics,
  type GatewayDiagnostics,
  type GatewayFilterChainItem,
  type GatewayArtifact,
  type GatewayRouteManifest,
  type GatewayAnonymousEndpoint,
  type GatewayEditionMatrix,
} from "../../services/platformApi";

type TabId = "artifacts" | "filters" | "routes" | "anonymous" | "editions";

const TABS: { id: TabId; icon: React.ComponentType<{ className?: string }>; i18nKey: string }[] = [
  { id: "artifacts", icon: Boxes, i18nKey: "platform.gateway.tab.artifacts" },
  { id: "filters", icon: ArrowRightLeft, i18nKey: "platform.gateway.tab.filters" },
  { id: "routes", icon: GitBranch, i18nKey: "dashboard.gateway.routes.tabSummary" },
  { id: "anonymous", icon: Globe, i18nKey: "platform.gateway.tab.anonymous" },
  { id: "editions", icon: Layers, i18nKey: "platform.gateway.tab.editions" },
];

const VALID = new Set(TABS.map((t) => t.id));

/** Normalize edition capabilities to a uniform array regardless of shape. */
function normalizeCaps(m?: GatewayEditionMatrix): Array<{ name: string; enabled: boolean }> {
  if (!m) return [];
  const c = m.capabilities;
  if (Array.isArray(c)) {
    return c.map((x) =>
      typeof x === "string" ? { name: x, enabled: true } : { name: x.name, enabled: x.enabled },
    );
  }
  if (c && typeof c === "object") {
    return Object.entries(c).map(([name, enabled]) => ({ name, enabled: Boolean(enabled) }));
  }
  return [];
}

/** Best-effort human string for an anonymous endpoint entry. */
function anonLabel(e: GatewayAnonymousEndpoint): string {
  const method = e.method ? `${e.method} ` : "";
  const path = e.path || e.endpoint || e.name || "";
  return `${method}${path}`.trim() || "—";
}

export default function GatewayDiagnosticsPage() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [searchParams, setSearchParams] = useSearchParams();

  const tabParam = searchParams.get("tab");
  const initialTab: TabId = VALID.has(tabParam as TabId) ? (tabParam as TabId) : "artifacts";
  const [activeTab, setActiveTab] = useState<TabId>(initialTab);

  const [loading, setLoading] = useState(true);
  const [noAccess, setNoAccess] = useState(false);
  const [error, setError] = useState("");
  const [data, setData] = useState<GatewayDiagnostics | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");
    setNoAccess(false);
    try {
      const d = await fetchGatewayDiagnostics();
      setData(d);
    } catch (e) {
      if (isNoAccessError(e)) setNoAccess(true);
      else setError(e instanceof Error ? e.message : "error");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const handleTabChange = useCallback(
    (id: TabId) => {
      setActiveTab(id);
      const next = new URLSearchParams(searchParams);
      next.set("tab", id);
      setSearchParams(next, { replace: true });
    },
    [searchParams, setSearchParams],
  );

  return (
    <div className={`flex-1 min-h-0 flex flex-col overflow-hidden ${styles.appBg}`}>
      {/* Header */}
      <div className="shrink-0 px-6 pt-4 pb-0 space-y-1 flex items-start justify-between gap-4">
        <div className="space-y-1 min-w-0">
          <div className="flex items-center gap-2">
            <Server className={`w-5 h-5 ${styles.accentText}`} />
            <h1 className="font-bold text-xl tracking-tight">{t("platform.gateway.title")}</h1>
          </div>
          <p className={`text-xs ${styles.cardTextMuted}`}>{t("platform.gateway.subtitle")}</p>
        </div>
        <button
          type="button"
          onClick={() => void load()}
          className={`shrink-0 flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold border ${styles.cardBorder} ${styles.cardBorder} rounded-lg transition hover:opacity-80 ${styles.cardTextMuted}`}
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
          {t("platform.gateway.refresh")}
        </button>
      </div>

      {noAccess ? (
        <NoAccessState styles={styles} t={t} />
      ) : (
        <>
          {/* Tab bar */}
          <div className={`${styles.cardBg} border-b ${styles.cardBorder} shrink-0`}>
            <div className="max-w-7xl mx-auto px-6 flex items-end gap-0.5 pt-3 overflow-x-auto">
              {TABS.map((tab) => {
                const Icon = tab.icon;
                const isActive = activeTab === tab.id;
                return (
                  <button
                    key={tab.id}
                    onClick={() => handleTabChange(tab.id)}
                    className={`h-9 px-3 rounded-t-md border-t-2 flex items-center gap-2 text-xs font-medium whitespace-nowrap shrink-0 cursor-pointer transition ${
                      isActive
                        ? `bg-transparent ${styles.cardText} font-bold ${styles.accentBorder}`
                        : `border-transparent opacity-70 hover:opacity-100 ${styles.cardTextMuted}`
                    }`}
                  >
                    <Icon className={`w-3.5 h-3.5 shrink-0 ${isActive ? styles.accentText : ""}`} />
                    {t(tab.i18nKey)}
                  </button>
                );
              })}
            </div>
          </div>

          {/* Body */}
          <div className="flex-1 min-h-0 overflow-hidden px-6 py-4 w-full min-w-0">
            <div className="max-w-7xl mx-auto h-full flex flex-col overflow-y-auto pr-1">
              {loading ? (
                <LoadingState styles={styles} t={t} />
              ) : error ? (
                <ErrorState styles={styles} t={t} message={error} onRetry={() => void load()} />
              ) : data ? (
                <>
                  {activeTab === "artifacts" && <ArtifactsTab data={data} />}
                  {activeTab === "filters" && <FiltersTab items={data.filterChain} />}
                  {activeTab === "routes" && <RoutesTab manifest={data.routeManifest} />}
                  {activeTab === "anonymous" && <AnonymousTab items={data.anonymousEndpoints} />}
                  {activeTab === "editions" && <EditionsTab matrix={data.editionMatrix} />}
                </>
              ) : null}
            </div>
          </div>

          {/* Footer — traceId */}
          {data?.traceId && (
            <div className={`shrink-0 px-6 py-2 border-t ${styles.cardBorder} ${styles.appBg}`}>
              <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
                {t("platform.gateway.traceId")}: {data.traceId}
              </span>
            </div>
          )}
        </>
      )}
    </div>
  );
}

// ── States ─────────────────────────────────────────────────────────

function LoadingState({ styles, t }: { styles: ReturnType<typeof useTheme>["styles"]; t: (k: string) => string }) {
  return (
    <div className="h-full flex flex-col items-center justify-center gap-3">
      <Loader2 className={`w-6 h-6 animate-spin ${styles.accentText}`} />
      <span className={`text-xs ${styles.cardTextMuted}`}>{t("common.loading")}</span>
    </div>
  );
}

function ErrorState({ styles, t, message, onRetry }: { styles: ReturnType<typeof useTheme>["styles"]; t: (k: string) => string; message: string; onRetry: () => void }) {
  return (
    <div className="h-full flex flex-col items-center justify-center gap-3 text-center">
      <ShieldAlert className={`w-9 h-9 ${styles.dangerText} opacity-70`} />
      <span className={`text-xs max-w-md break-words ${styles.cardTextMuted}`}>{message}</span>
      <button
        type="button"
        onClick={onRetry}
        className={`mt-2 flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold ${styles.accentBg} ${styles.accentHover} text-white rounded-lg transition cursor-pointer`}
      >
        <RefreshCw className="w-3.5 h-3.5" />
        {t("platform.gateway.retry")}
      </button>
    </div>
  );
}

function NoAccessState({ styles, t }: { styles: ReturnType<typeof useTheme>["styles"]; t: (k: string) => string }) {
  return (
    <div className="flex-1 min-h-0 flex flex-col items-center justify-center gap-3 text-center px-6">
      <div className={`p-4 rounded-2xl ${styles.warningBg}`}>
        <ShieldAlert className={`w-8 h-8 ${styles.warningText}`} />
      </div>
      <h2 className={`text-sm font-bold ${styles.cardText}`}>{t("platform.gateway.noAccess.title")}</h2>
      <p className={`text-xs max-w-sm ${styles.cardTextMuted}`}>{t("platform.gateway.noAccess.desc")}</p>
    </div>
  );
}

// ── Section shells ─────────────────────────────────────────────────

function SectionCard({ children, title, icon }: { children: React.ReactNode; title: string; icon?: React.ReactNode }) {
  const { styles } = useTheme();
  return (
    <div className={`border ${styles.cardBorder} rounded-xl ${styles.cardBg} overflow-hidden`}>
      <div className={`flex items-center gap-2 px-4 py-3 border-b ${styles.cardBorder}`}>
        {icon && <span className={styles.accentText}>{icon}</span>}
        <span className={`text-sm font-bold ${styles.cardText}`}>{title}</span>
      </div>
      <div className="p-0">{children}</div>
    </div>
  );
}

// ── Tab: artifacts × 承流态 ────────────────────────────────────────

function ArtifactsTab({ data }: { data: GatewayDiagnostics }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const rows: GatewayArtifact[] = data.artifacts ?? [];
  return (
    <SectionCard title={t("platform.gateway.artifacts.title")} icon={<Boxes className="w-4 h-4" />}>
      <div className="overflow-x-auto">
        <table className="w-full text-xs">
          <thead>
            <tr className={`border-b ${styles.cardBorder}`}>
              <th className={`px-4 py-2.5 text-left font-bold uppercase tracking-wider text-[10px] ${styles.cardTextMuted}`}>{t("platform.gateway.artifacts.col.name")}</th>
              <th className={`px-4 py-2.5 text-left font-bold uppercase tracking-wider text-[10px] ${styles.cardTextMuted}`}>{t("platform.gateway.artifacts.col.port")}</th>
              <th className={`px-4 py-2.5 text-left font-bold uppercase tracking-wider text-[10px] ${styles.cardTextMuted}`}>{t("platform.gateway.artifacts.col.mode")}</th>
              <th className={`px-4 py-2.5 text-left font-bold uppercase tracking-wider text-[10px] ${styles.cardTextMuted}`}>{t("platform.gateway.artifacts.col.active")}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((a, i) => (
              <tr key={`${a.name}-${a.port}-${i}`} className={`border-b last:border-0 ${styles.cardBorder}`}>
                <td className={`px-4 py-2.5 font-mono ${styles.cardText}`}>{a.name}</td>
                <td className={`px-4 py-2.5 font-mono ${styles.cardTextMuted}`}>{String(a.port)}</td>
                <td className={`px-4 py-2.5 ${styles.cardTextMuted}`}>{a.mode}</td>
                <td className="px-4 py-2.5">
                  <ActiveBadge active={a.active} t={t} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {rows.length === 0 && <EmptyRow t={t} />}
      </div>
    </SectionCard>
  );
}

function ActiveBadge({ active, t }: { active: boolean; t: (k: string) => string }) {
  const { styles } = useTheme();
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium border ${active ? `${styles.successBg} ${styles.successText} ${styles.successBorder}` : `${styles.badgeBg} ${styles.badgeText} ${styles.appBorder}`}`}>
      <Activity className="w-3 h-3" />
      {t(active ? "platform.gateway.active" : "platform.gateway.inactive")}
    </span>
  );
}

// ── Tab: filter chain ORDER 对照 ───────────────────────────────────

function FiltersTab({ items }: { items: GatewayFilterChainItem[] }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  return (
    <SectionCard title={t("platform.gateway.filters.title")} icon={<ArrowRightLeft className="w-4 h-4" />}>
      <div className="overflow-x-auto">
        <table className="w-full text-xs">
          <thead>
            <tr className={`border-b ${styles.cardBorder}`}>
              <th className={`px-4 py-2.5 text-left font-bold uppercase tracking-wider text-[10px] ${styles.cardTextMuted} w-14`}>{t("platform.gateway.filters.col.index")}</th>
              <th className={`px-4 py-2.5 text-left font-bold uppercase tracking-wider text-[10px] ${styles.cardTextMuted}`}>{t("platform.gateway.filters.col.name")}</th>
              <th className={`px-4 py-2.5 text-left font-bold uppercase tracking-wider text-[10px] ${styles.cardTextMuted} w-24`}>{t("platform.gateway.filters.col.order")}</th>
              <th className={`px-4 py-2.5 text-left font-bold uppercase tracking-wider text-[10px] ${styles.cardTextMuted} w-24`}>{t("platform.gateway.filters.col.spec")}</th>
              <th className={`px-4 py-2.5 text-left font-bold uppercase tracking-wider text-[10px] ${styles.cardTextMuted} w-28`}>{t("platform.gateway.filters.col.status")}</th>
            </tr>
          </thead>
          <tbody>
            {items.map((f, i) => (
              <tr key={`${f.name}-${f.index}-${i}`} className={`border-b last:border-0 ${styles.cardBorder}`}>
                <td className={`px-4 py-2.5 font-mono ${styles.cardTextMuted}`}>{f.index}</td>
                <td className={`px-4 py-2.5 font-mono ${styles.cardText}`}>{f.name}</td>
                <td className={`px-4 py-2.5 font-mono ${styles.cardText}`}>{f.ORDER}</td>
                <td className={`px-4 py-2.5 font-mono ${styles.cardTextMuted}`}>{f.specIndex}</td>
                <td className="px-4 py-2.5">
                  <OrderMatchBadge order={f.ORDER} spec={f.specIndex} t={t} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {items.length === 0 && <EmptyRow t={t} />}
      </div>
    </SectionCard>
  );
}

function OrderMatchBadge({ order, spec, t }: { order: number; spec: number; t: (k: string) => string }) {
  const { styles } = useTheme();
  const match = order === spec;
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-medium border ${match ? `${styles.successBg} ${styles.successText} ${styles.successBorder}` : `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}`}>
      {t(match ? "platform.gateway.filters.match" : "platform.gateway.filters.mismatch")}
    </span>
  );
}

// ── Tab: route manifest summary ────────────────────────────────────

function RoutesTab({ manifest }: { manifest: GatewayRouteManifest }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatTile
          label={t("platform.gateway.routes.present")}
          value={manifest.present ? t("platform.gateway.routes.yes") : t("platform.gateway.routes.no")}
          tone={manifest.present ? "success" : "warning"}
        />
        <StatTile label={t("platform.gateway.routes.count")} value={String(manifest.count)} />
        <StatTile label={t("platform.gateway.routes.service")} value={String(manifest.serviceRoutes)} tone="info" />
        <StatTile label={t("platform.gateway.routes.monolith")} value={String(manifest.monolithRoutes)} tone="info" />
      </div>
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4`}>
        <div className={`text-xs ${styles.cardTextMuted} flex items-start gap-2`}>
          <Info className="w-3.5 h-3.5 shrink-0 mt-0.5" />
          <span>
            {t("platform.gateway.routes.hint")}
            {/* name-vs-reality note: endpoint exposes aggregate counts only, no per-route detail rows */}
            <span className={`mt-1 block ${styles.cardTextMuted} opacity-80`}>
              {t("dashboard.gateway.routes.summaryNote")}
            </span>
          </span>
        </div>
      </div>
    </div>
  );
}

// ── Tab: anonymous endpoints ───────────────────────────────────────

function AnonymousTab({ items }: { items: GatewayAnonymousEndpoint[] | Record<string, unknown> }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const list: GatewayAnonymousEndpoint[] = Array.isArray(items)
    ? items
    : items && typeof items === "object"
      ? Object.entries(items).map(([k, v]) =>
          typeof v === "string" ? { name: k, path: k } : { name: k, ...((v as GatewayAnonymousEndpoint) ?? {}) },
        )
      : [];
  return (
    <SectionCard title={t("platform.gateway.anonymous.title")} icon={<Globe className="w-4 h-4" />}>
      {list.length === 0 ? (
        <EmptyRow t={t} />
      ) : (
        <ul className="divide-y">
          {list.map((e, i) => (
            <li key={i} className={`px-4 py-2.5 flex items-center gap-3 border-b ${styles.cardBorder}`}>
              <Globe className={`w-3.5 h-3.5 shrink-0 ${styles.accentText}`} />
              <span className="text-xs font-mono break-all">{anonLabel(e)}</span>
            </li>
          ))}
        </ul>
      )}
    </SectionCard>
  );
}

// ── Tab: edition capability matrix ─────────────────────────────────

function EditionsTab({ matrix }: { matrix: GatewayEditionMatrix }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const caps = normalizeCaps(matrix);
  return (
    <div className="space-y-4">
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 flex items-center gap-3`}>
        <FileText className={`w-4 h-4 ${styles.accentText}`} />
        <span className={`text-xs ${styles.cardTextMuted}`}>{t("platform.gateway.editions.active")}</span>
        <span className={`text-sm font-bold font-mono ${styles.cardText}`}>{matrix.activeProfile || "—"}</span>
      </div>
      <SectionCard title={t("platform.gateway.editions.capabilities")} icon={<Layers className="w-4 h-4" />}>
        {caps.length === 0 ? (
          <EmptyRow t={t} />
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 p-4">
            {caps.map((c, i) => (
              <div key={`${c.name}-${i}`} className={`flex items-center justify-between rounded-lg border px-3 py-2 ${c.enabled ? `${styles.successBorder}` : styles.appBorder} ${c.enabled ? styles.successBg : styles.badgeBg}`}>
                <span className={`text-xs font-medium ${styles.cardText}`}>{c.name}</span>
                <span className={`text-[10px] font-bold ${c.enabled ? styles.successText : styles.cardTextMuted}`}>
                  {t(c.enabled ? "platform.gateway.editions.enabled" : "platform.gateway.editions.disabled")}
                </span>
              </div>
            ))}
          </div>
        )}
      </SectionCard>
    </div>
  );
}

// ── Shared bits ────────────────────────────────────────────────────

function StatTile({ label, value, tone }: { label: string; value: string; tone?: "success" | "warning" | "info" }) {
  const { styles } = useTheme();
  const toneCls = tone
    ? {
        success: `${styles.successBg} ${styles.successText} ${styles.successBorder}`,
        warning: `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`,
        info: `${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`,
      }[tone]
    : `${styles.cardBg} ${styles.cardText} ${styles.cardBorder}`;
  return (
    <div className={`p-4 rounded-xl border ${toneCls}`}>
      <div className={`text-[10px] font-mono uppercase tracking-wider opacity-80`}>{label}</div>
      <div className="mt-1 text-2xl font-extrabold tabular-nums">{value}</div>
    </div>
  );
}

function EmptyRow({ t }: { t: (k: string) => string }) {
  const { styles } = useTheme();
  return (
    <div className={`py-8 text-center text-xs ${styles.cardTextMuted}`}>{t("platform.gateway.empty")}</div>
  );
}
