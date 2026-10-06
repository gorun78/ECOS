/**
 * GatewayDiagnosticsPage — 网关诊断页 (#/platform/gateway-diagnostics)
 * 五区块（服务健康/过滤链/路由清单/匿名端点/档位矩阵）+ 4 Tab（?tab=）+ 页脚 traceId。
 * 设计：docs/30-设计/详细设计-00-平台入口与横切底座-2026-09-28.md §3.1。
 *
 * 主题 token: 走 useTheme()（cardBg/cardBorder/cardText/...）。
 * i18n 键: platform.gateway.*（locales/platform/zh-CN.json + en.json）。
 * 无障碍: Tab role="tablist" + ←/→ 键盘；一致性判定 = 图标 + 文本双标识。
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  RefreshCw,
  CheckCircle2,
  XCircle,
  Copy,
  Check,
  Network,
  Filter,
  Route as RouteIcon,
  Globe2,
  Layers,
  Server,
  AlertTriangle,
  Inbox,
  Lock,
} from "lucide-react";
import { useLanguage } from "../../../components/LanguageContext";
import { useTheme } from "../../../components/ThemeContext";
import { useToast } from "../../../components/common/Toast";
import {
  GatewayDiagnostics,
  GATEWAY_TAB_IDS,
  GatewayTab,
  stateData,
  stateTraceId,
  type DiagnosticsState,
  normalizeTab,
  probe,
} from "./types";

type TabDef = {
  id: GatewayTab;
  labelKey: string;
  icon: React.ComponentType<{ className?: string }>;
};

const TABS: TabDef[] = [
  { id: "filters", labelKey: "platform.gateway.tab.filters", icon: Filter },
  { id: "routes", labelKey: "platform.gateway.tab.routes", icon: RouteIcon },
  { id: "anonymous", labelKey: "platform.gateway.tab.anonymous", icon: Globe2 },
  { id: "editions", labelKey: "platform.gateway.tab.editions", icon: Layers },
];

const ALL_BLOCK_IDS = ["services", "filterChain", "routes", "anonymous", "editions"] as const;
type BlockId = (typeof ALL_BLOCK_IDS)[number];

const BLOCK_ICON: Record<BlockId, React.ComponentType<{ className?: string }>> = {
  services: Server,
  filterChain: Filter,
  routes: RouteIcon,
  anonymous: Globe2,
  editions: Layers,
};

// ══════════════════════════════════════════════════════════════
// 小块渲染区段
// ══════════════════════════════════════════════════════════════

function ConsistentBadge({ consistent }: { consistent: boolean }) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  if (consistent) {
    return (
      <span className={`inline-flex items-center gap-1.5 ${styles.successText}`}>
        <CheckCircle2 className="w-3.5 h-3.5" data-testid="gw-consistent-yes" />
        <span>{t("platform.gateway.consistent.yes")}</span>
      </span>
    );
  }
  return (
    <span className={`inline-flex items-center gap-1.5 font-semibold ${styles.dangerText}`}>
      <XCircle className="w-3.5 h-3.5" data-testid="gw-consistent-no" />
      <span>{t("platform.gateway.consistent.no")}</span>
    </span>
  );
}

function CarriedBadge({ carried }: { carried: boolean }) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  return carried ? (
    <span className={`inline-flex items-center gap-1.5 ${styles.successText}`}>
      <CheckCircle2 className="w-3.5 h-3.5" />
      <span>{t("platform.gateway.carried.yes")}</span>
    </span>
  ) : (
    <span className={`inline-flex items-center gap-1.5 ${styles.muted}`}>
      <span className="w-1.5 h-1.5 rounded-full opacity-40 inline-block" />
      <span>{t("platform.gateway.carried.no")}</span>
    </span>
  );
}

function DuplicatePathBadge({ dualPath }: { dualPath: boolean }) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  return dualPath ? (
    <span className={`inline-flex items-center gap-1 ${styles.infoText}`}>
      <Check className="w-3 h-3" />
      {t("platform.gateway.dualPath.yes")}
    </span>
  ) : (
    <span className={styles.muted}>{t("platform.gateway.dualPath.no")}</span>
  );
}

function Section({
  blockId,
  title,
  children,
  active,
  tone,
}: {
  blockId: BlockId;
  title: string;
  children: React.ReactNode;
  active: boolean;
  tone?: "warn" | "danger";
}) {
  const { styles } = useTheme();
  const Icon = BLOCK_ICON[blockId];
  // 警示色用主题语义 border token（warningBorder/dangerBorder 形如 "border-amber-200"）。
  const toneCls = tone === "danger"
    ? `ring-1 ${styles.dangerBorder}`
    : tone === "warn"
      ? `ring-1 ${styles.warningBorder}`
      : "";
  return (
    <section
      data-testid={`gw-section-${blockId}`}
      aria-current={active ? "step" : undefined}
      className={`grid gap-3 p-4 rounded-md border ${styles.appBorder} ${styles.cardBg} ${tone ? toneCls : ""} ${active ? "" : "opacity-90"}`}
    >
      <header className="flex items-center gap-2">
        <Icon className={`w-4 h-4 ${styles.accentText}`} />
        <h3 className={`text-sm font-semibold ${styles.cardText}`}>{title}</h3>
      </header>
      {children}
    </section>
  );
}

function TableShell({
  head,
  body,
  testid,
}: {
  head: React.ReactNode;
  body: React.ReactNode;
  testid?: string;
}) {
  const { styles } = useTheme();
  return (
    <div data-testid={testid} className={`min-w-full overflow-x-auto ${styles.cardText}`}>
      <table className={`w-full text-xs border-collapse ${styles.cardText}`}>
        <thead>
          <tr className={`border-b ${styles.appBorder} text-[10px] uppercase tracking-wider ${styles.muted}`}>
            {head}
          </tr>
        </thead>
        <tbody className={`divide-y ${styles.appBorder} ${styles.cardText}`}>{body}</tbody>
      </table>
    </div>
  );
}

// 五区块各自独立渲染（每 block ≤60 行以内，整页 ≤800 行）

function ServicesBlock({ list }: { list: GatewayDiagnostics["artifacts"] }) {
  const { t } = useLanguage();
  return (
    <TableShell
      testid="gw-table-services"
      head={
        <>
          <th className="text-left px-2 py-1.5 font-mono">{t("platform.gateway.col.artifact")}</th>
          <th className="text-right px-2 py-1.5 font-mono">{t("platform.gateway.col.port")}</th>
          <th className="text-right px-2 py-1.5 font-mono">{t("platform.gateway.col.carried")}</th>
        </>
      }
      body={list.map((a) => (
        <tr key={`${a.name}-${a.port}`} className="hover:opacity-90">
          <td className="px-2 py-1.5 font-medium">{a.name}</td>
          <td className="px-2 py-1.5 text-right font-mono tabular-nums">{a.port}</td>
          <td className="px-2 py-1.5 text-right"><CarriedBadge carried={a.carried} /></td>
        </tr>
      ))}
    />
  );
}

function FilterChainBlock({ list }: { list: GatewayDiagnostics["filterChain"] }) {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const hasInconsistency = list.some((f) => !f.consistent);
  return (
    <div className="space-y-2" data-testid="gw-block-filterChain">
      {!list.length ? (
        <div className={`text-xs ${styles.muted}`}>{t("platform.gateway.state.empty")}</div>
      ) : (
        <TableShell
          testid="gw-table-filterChain"
          head={
            <>
              <th className="text-left px-2 py-1.5 font-mono">
                {t("platform.gateway.col.filter")}
              </th>
              <th className="text-right px-2 py-1.5 font-mono">{t("platform.gateway.col.currentOrder")}</th>
              <th className="text-right px-2 py-1.5 font-mono">{t("platform.gateway.col.expectedOrder")}</th>
              <th className="text-right px-2 py-1.5 font-mono">{t("platform.gateway.col.consistent")}</th>
            </>
          }
          body={list.map((f) => (
            <tr
              key={`${f.name}-${f.order}`}
              data-inconsistent={f.consistent ? "false" : "true"}
              className={f.consistent ? "" : `${styles.dangerBg} ${styles.dangerText}`}
            >
              <td className="px-2 py-1.5 font-mono">{f.name}</td>
              <td className="px-2 py-1.5 text-right font-mono tabular-nums">{f.order}</td>
              <td className="px-2 py-1.5 text-right font-mono tabular-nums">{f.expectedOrder}</td>
              <td className="px-2 py-1.5 text-right"><ConsistentBadge consistent={f.consistent} /></td>
            </tr>
          ))}
        />
      )}
      {hasInconsistency && (
        <p className={`text-[10px] ${styles.dangerText}`}>
          <AlertTriangle className="inline w-3 h-3 mr-1" />
          {t("platform.gateway.consistent.no")}
        </p>
      )}
    </div>
  );
}

function RoutesBlock({ list }: { list: GatewayDiagnostics["routeManifest"] }) {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  return (
    <div data-testid="gw-block-routes">
      {!list.length ? (
        <div className={`text-xs ${styles.muted}`}>{t("platform.gateway.state.empty")}</div>
      ) : (
        <TableShell
          testid="gw-table-routes"
          head={
            <>
              <th className="text-left px-2 py-1.5 font-mono">{t("platform.gateway.col.prefix")}</th>
              <th className="text-left px-2 py-1.5 font-mono">{t("platform.gateway.col.owner")}</th>
              <th className="text-left px-2 py-1.5 font-mono">{t("platform.gateway.col.mode")}</th>
              <th className="text-right px-2 py-1.5 font-mono">{t("platform.gateway.col.carried")}</th>
            </>
          }
          body={list.map((r, i) => (
            <tr key={`${r.prefix}-${i}`} data-testid="gw-route-row">
              <td className="px-2 py-1.5 font-mono break-all">{r.prefix}</td>
              <td className="px-2 py-1.5">{r.owner}</td>
              <td className="px-2 py-1.5">
                {r.mode === "service"
                  ? t("platform.gateway.mode.service")
                  : r.mode === "monolith"
                    ? t("platform.gateway.mode.monolith")
                    : "—"}
              </td>
              <td className="px-2 py-1.5 text-right"><CarriedBadge carried={r.carried} /></td>
            </tr>
          ))}
        />
      )}
    </div>
  );
}

function AnonymousBlock({ list }: { list: GatewayDiagnostics["anonymousEndpoints"] }) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  return (
    <div data-testid="gw-block-anonymous">
      {!list.length ? (
        <div className={`text-xs ${styles.muted}`}>{t("platform.gateway.state.empty")}</div>
      ) : (
        <TableShell
          testid="gw-table-anonymous"
          head={
            <>
              <th className="text-left px-2 py-1.5 font-mono">{t("platform.gateway.col.pattern")}</th>
              <th className="text-left px-2 py-1.5 font-mono">{t("platform.gateway.col.dualPath")}</th>
              <th className="text-left px-2 py-1.5 font-mono">{t("platform.gateway.col.justification")}</th>
              <th className="text-left px-2 py-1.5 font-mono">{t("platform.gateway.col.approver")}</th>
            </>
          }
          body={list.map((r, i) => (
            <tr key={`${r.pattern}-${i}`}>
              <td className="px-2 py-1.5 font-mono break-all">{r.pattern}</td>
              <td className="px-2 py-1.5"><DuplicatePathBadge dualPath={r.dualPath} /></td>
              <td className="px-2 py-1.5 max-w-xs break-words">{r.justification}</td>
              <td className="px-2 py-1.5">{r.approver || "—"}</td>
            </tr>
          ))}
        />
      )}
    </div>
  );
}

/**
 * editionMatrix 契约未固定 —— 后端设计中为 object；也兼容 array。
 * 渲染为「能力 × 档位」小矩阵（standard/enterprise/ultimate 三列 × 能力行）。
 */
function EditionsBlock({ matrix }: { matrix: GatewayDiagnostics["editionMatrix"] }) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const editions = ["standard", "enterprise", "ultimate"] as const;
  const rows = useMemo<{ key: string; values: Record<string, boolean> }[]>(() => {
    if (!matrix || Array.isArray(matrix)) return [];
    const m = matrix as Record<string, unknown>;
    return Object.entries(m).map(([capability, val]) => {
      const isYes =
        val === true ||
        val === "true" ||
        val === "yes" ||
        val === "✅" ||
        val === 1 ||
        val === "1";
      const values: Record<string, boolean> = {};
      editions.forEach((e) => (values[e] = isYes));
      return { key: capability, values };
    });
  }, [matrix]);

  return (
    <div data-testid="gw-block-editions">
      {!rows.length ? (
        <div className={`text-xs ${styles.muted}`}>{t("platform.gateway.state.empty")}</div>
      ) : (
        <TableShell
          testid="gw-table-editions"
          head={
            <>
              <th className="text-left px-2 py-1.5 font-mono">{t("platform.gateway.edition.col.capability")}</th>
              {editions.map((e) => (
                <th key={e} className="text-center px-2 py-1.5 font-mono">{t(`platform.gateway.edition.${e}`)}</th>
              ))}
            </>
          }
          body={rows.map((row) => (
            <tr key={row.key}>
              <td className="px-2 py-1.5 font-medium">{row.key}</td>
              {editions.map((e) => {
                const v: boolean | undefined = row.values[e];
                return (
                  <td key={e} className="text-center px-2 py-1.5" data-capability={row.key} data-edition={e}>
                    {v ? (
                      <span className={styles.successText}><CheckCircle2 className="inline w-3.5 h-3.5" /></span>
                    ) : (
                      <span className={styles.muted}>·</span>
                    )}
                  </td>
                );
              })}
            </tr>
          ))}
        />
      )}
    </div>
  );
}

// ══════════════════════════════════════════════════════════════
// Page shell
// ══════════════════════════════════════════════════════════════

export default function GatewayDiagnosticsPage() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const { showToast } = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = useMemo(() => normalizeTab(searchParams.get("tab")), [searchParams]);
  const [state, setState] = useState<DiagnosticsState>({ phase: "loading" });
  const [copied, setCopied] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const tabRefs = useRef<Record<GatewayTab, HTMLButtonElement | null>>({
    filters: null,
    routes: null,
    anonymous: null,
    editions: null,
  });
  const [asOf, setAsOf] = useState<string>("");

  const setTab = useCallback(
    (next: GatewayTab) => {
      setSearchParams((prev) => {
        const sp = new URLSearchParams(prev);
        sp.set("tab", next);
        return sp;
      });
      // 键盘焦点管理 — 焦点应跳到新选中 Tab 的按钮
      const el = tabRefs.current[next];
      if (el && typeof el.focus === "function") el.focus();
    },
    [setSearchParams]
  );

  const refresh = useCallback(async () => {
    setRefreshing(true);
    const prev = stateData(state);
    // 「重新探测」 — 从缓存 prev 进入 error 也会带回退
    setState(prev ? { phase: "loading", prev } : { phase: "loading" });
    const next = await probe(tab, prev);
    if (next.phase === "ready") {
      setAsOf(new Date().toISOString());
    }
    setState(next);
    setRefreshing(false);
    if (next.phase === "forbidden") {
      showToast("error", t("platform.gateway.state.forbidden"));
    } else if (next.phase === "error") {
      showToast("error", next.message);
    }
  }, [tab, state, showToast, t]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  // 复制 traceId
  const traceId = stateTraceId(state);

  const copyTrace = async () => {
    if (!traceId) return;
    try {
      await navigator.clipboard.writeText(traceId);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      showToast("error", t("platform.gateway.action.copy"));
    }
  };

  // 键盘 ←/→ 切 Tab
  const onTabKeyDown = (e: React.KeyboardEvent) => {
    const idx = GATEWAY_TAB_IDS.indexOf(tab);
    if (e.key === "ArrowRight" || e.key === "ArrowDown") {
      e.preventDefault();
      setTab(GATEWAY_TAB_IDS[(idx + 1) % GATEWAY_TAB_IDS.length]);
    } else if (e.key === "ArrowLeft" || e.key === "ArrowUp") {
      e.preventDefault();
      setTab(GATEWAY_TAB_IDS[(idx - 1 + GATEWAY_TAB_IDS.length) % GATEWAY_TAB_IDS.length]);
    } else if (e.key === "Home") {
      e.preventDefault();
      setTab(GATEWAY_TAB_IDS[0]);
    } else if (e.key === "End") {
      e.preventDefault();
      setTab(GATEWAY_TAB_IDS[GATEWAY_TAB_IDS.length - 1]);
    }
  };

  const data = state.phase === "ready" ? state.data : (state as { prev?: GatewayDiagnostics }).prev;
  const hasEmptyData =
    data &&
    data.artifacts.length === 0 &&
    data.filterChain.length === 0 &&
    data.routeManifest.length === 0 &&
    data.anonymousEndpoints.length === 0;

  const isOffline = state.phase === "error";

  const renderTab = (id: GatewayTab, def: TabDef) => {
    const Icon = def.icon;
    const isActive = tab === id;
    return (
      <button
        key={id}
        ref={(el) => { tabRefs.current[id] = el; }}
        role="tab"
        id={`gw-tab-${id}`}
        data-testid={`gw-tab-${id}`}
        aria-selected={isActive}
        aria-controls={`gw-tabpanel-${id}`}
        tabIndex={isActive ? 0 : -1}
        onClick={() => setTab(id)}
        onKeyDown={onTabKeyDown}
        className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-[11px] font-medium cursor-pointer transition
          ${isActive ? `${styles.accentBg} text-white` : `${styles.muted} hover:opacity-80`}
          disabled:opacity-50`}
      >
        <Icon className="w-3.5 h-3.5" />
        {t(def.labelKey)}
      </button>
    );
  };

  const renderLoadingSkeleton = () => (
    <div className="grid gap-3 lg:grid-cols-2" aria-busy="true" data-testid="gw-loading">
      {ALL_BLOCK_IDS.map((b) => (
        <div key={b} className={`h-24 rounded border ${styles.appBorder} animate-pulse`} />
      ))}
    </div>
  );

  const renderForbidden = () => (
    <div
      data-testid="gw-forbidden"
      className={`${styles.cardBg} border ${styles.cardBorder} rounded-md p-8 flex flex-col items-center gap-3`}
    >
      <Lock className={`w-8 h-8 ${styles.warningText}`} />
      <div className={`text-sm font-semibold ${styles.cardText}`}>
        {t("platform.gateway.state.forbidden")}
      </div>
      <div className={`text-xs ${styles.muted}`}>{t("platform.gateway.state.forbidden.desc")}</div>
    </div>
  );

  const renderError = (message: string, trace?: string) => (
    <div
      data-testid="gw-error"
      className={`${styles.dangerBg} border ${styles.dangerBorder} rounded-md p-4 space-y-2`}
      role="alert"
    >
      <div className={`text-sm font-semibold ${styles.dangerText} flex items-center gap-2`}>
        <AlertTriangle className="w-4 h-4" />
        {message}
      </div>
      {trace && (
        <div className="flex items-center gap-2 font-mono text-[10px]">
          <span className={styles.dangerText}>{t("platform.gateway.error.traceId")}:</span>
          <code className="break-all">{trace}</code>
          <button
            type="button"
            onClick={copyTrace}
            data-testid="gw-error-copy"
            className={`inline-flex items-center gap-1 px-1.5 py-0.5 border rounded border-current cursor-pointer ${styles.dangerText}`}
            aria-label={t("platform.gateway.action.copy")}
          >
            {copied ? <Check className="w-3 h-3" /> : <Copy className="w-3 h-3" />}
            {t("platform.gateway.action.copied")}
          </button>
        </div>
      )}
    </div>
  );

  const renderBlocks = (d: GatewayDiagnostics) => (
    <div className="grid gap-3 lg:grid-cols-2" data-testid="gw-blocks" role="tabpanel" id={`gw-tabpanel-${tab}`} aria-labelledby={`gw-tab-${tab}`}>
      <Section blockId="services" title={t("platform.gateway.block.services")} active={tab === "filters"}>
        {d.artifacts.length ? <ServicesBlock list={d.artifacts} /> : (
          <div className={`text-xs ${styles.muted} flex items-center gap-2`}>
            <Inbox className="w-4 h-4" /> {t("platform.gateway.state.empty")}
          </div>
        )}
      </Section>
      <Section blockId="filterChain" title={t("platform.gateway.block.filterChain")} active={tab === "filters"} tone={d.filterChain.some(f => !f.consistent) ? "warn" : undefined}>
        <FilterChainBlock list={d.filterChain} />
      </Section>
      <Section blockId="routes" title={t("platform.gateway.block.routes")} active={tab === "routes"}>
        <RoutesBlock list={d.routeManifest} />
      </Section>
      <Section blockId="anonymous" title={t("platform.gateway.block.anonymous")} active={tab === "anonymous"}>
        <AnonymousBlock list={d.anonymousEndpoints} />
      </Section>
      <div className="lg:col-span-2">
        <Section blockId="editions" title={t("platform.gateway.block.editions")} active={tab === "editions"}>
          <EditionsBlock matrix={d.editionMatrix} />
        </Section>
      </div>
    </div>
  );

  return (
    <div className={`h-full flex flex-col ${styles.appBg} ${styles.appText}`}>
      {/* 顶栏：标题 + 刷新按钮 */}
      <div className={`flex items-center justify-between px-4 pt-4 pb-3 border-b ${styles.appBorder}`}>
        <div className="flex items-center gap-2">
          <Network className={`w-4 h-4 ${styles.accentText}`} />
          <h1 className={`text-base font-semibold ${styles.cardText}`} data-testid="gw-page-title">
            {t("platform.gateway.title")}
          </h1>
        </div>
        <button
          type="button"
          data-testid="gw-refresh"
          onClick={() => void refresh()}
          aria-busy={refreshing}
          className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs font-medium cursor-pointer border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} hover:opacity-80 transition disabled:opacity-50`}
          disabled={refreshing}
        >
          <RefreshCw className={`w-3.5 h-3.5 ${refreshing ? "animate-spin" : ""}`} />
          {t("platform.gateway.action.refresh")}
        </button>
      </div>

      {/* Tab bar */}
      <div
        role="tablist"
        aria-label={t("platform.gateway.title")}
        data-testid="gw-tablist"
        className={`flex items-center gap-1.5 px-4 py-2 border-b ${styles.appBorder}`}
      >
        {TABS.map((d) => renderTab(d.id, d))}
        <div className="flex-1" />
        {isOffline && asOf && (
          <span className="text-[10px] font-mono" data-testid="gw-asof">
            {asOf}
          </span>
        )}
      </div>

      {/* 内容区 */}
      <div className="flex-1 overflow-y-auto p-4 space-y-3 scrollbar-thin">
        {state.phase === "loading" && !data && renderLoadingSkeleton()}
        {state.phase === "forbidden" && renderForbidden()}
        {state.phase === "error" && (
          <>
            {renderError(state.message, state.traceId)}
            {asOf && (
              <div className={`flex items-center gap-2 text-[10px] ${styles.muted}`}>
                <span>{t("platform.gateway.state.offline")}</span>
                <span className="font-mono">{asOf}</span>
              </div>
            )}
            {data && renderBlocks(data)}
          </>
        )}
        {data && state.phase === "ready" && renderBlocks(data)}
        {(state.phase === "ready" && data && hasEmptyData) && (
          <div className={`text-xs ${styles.muted} flex items-center gap-2`}>
            <Inbox className="w-4 h-4" /> {t("platform.gateway.state.empty.desc")}
          </div>
        )}
      </div>

      {/* 页脚：traceId 显示 + 复制 */}
      <footer className={`flex items-center justify-between px-4 py-2 border-t ${styles.appBorder} text-[10px] font-mono ${styles.muted}`}>
        <span data-testid="gw-footer-trace">
          {traceId ? (
            <span className="flex items-center gap-2">
              {t("platform.gateway.error.traceId")}:<code>{traceId}</code>
              <button
                type="button"
                onClick={copyTrace}
                data-testid="gw-copy-trace"
                aria-label={t("platform.gateway.action.copy")}
                className={`inline-flex items-center gap-1 px-1.5 py-0.5 border rounded border-current cursor-pointer`}
              >
                {copied ? <Check className="w-3 h-3" /> : <Copy className="w-3 h-3" />}
                {copied ? t("platform.gateway.action.copied") : t("platform.gateway.action.copy")}
              </button>
            </span>
          ) : (
            "—"
          )}
        </span>
      </footer>
    </div>
  );
}
