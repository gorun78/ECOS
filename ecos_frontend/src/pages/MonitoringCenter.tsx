import React, { useState, useEffect, useCallback } from "react";
import { BarChart3, CpuIcon, ChevronDown, Activity, Shield, Database, Network, Brain, BookOpen, Sparkles, RefreshCw, CheckCircle2, XCircle, Loader2, AlertTriangle, Lock } from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import BasicMonitoringTab from "./monitoring/tabs/BasicMonitoringTab";
import DigitalTwinTab from "./monitoring/tabs/DigitalTwinTab";
import EngineMonitor from "./EngineMonitor";
import { useToast } from "../components/common/Toast";

interface TabDef {
  id: string;
  labelKey: string;
  icon: React.ComponentType<{ className?: string }>;
}

const TABS: TabDef[] = [
  { id: "metrics", labelKey: "dashboard.monitoring.tab.metrics", icon: BarChart3 },
  { id: "engines", labelKey: "dashboard.monitoring.tab.engines", icon: Activity },
  { id: "twins", labelKey: "dashboard.monitoring.tab.twins", icon: CpuIcon },
];

interface EngineDef {
  id: string;
  labelKey: string;
  icon: React.ComponentType<{ className?: string }>;
  engine: "security" | "data" | "ontology" | "cognitive" | "knowledge" | "ai";
  apiBase: string;
}

const ENGINES: EngineDef[] = [
  { id: "eng-security",  labelKey: "dashboard.monitoring.engine.security",  icon: Shield,   engine: "security",  apiBase: `${API_BASE}/v1/engine/security` },
  { id: "eng-data",      labelKey: "dashboard.monitoring.engine.data",      icon: Database, engine: "data",      apiBase: `${API_BASE}/v1/engine/data` },
  { id: "eng-ontology",  labelKey: "dashboard.monitoring.engine.ontology",  icon: Network,  engine: "ontology",  apiBase: `${API_BASE}/v1/engine/ontology` },
  { id: "eng-cognitive", labelKey: "dashboard.monitoring.engine.cognitive", icon: Brain,    engine: "cognitive", apiBase: `${API_BASE}/v1/engine/cognitive` },
  { id: "eng-knowledge", labelKey: "dashboard.monitoring.engine.knowledge", icon: BookOpen, engine: "knowledge", apiBase: `${API_BASE}/v1/engine/knowledge` },
  { id: "eng-ai",        labelKey: "dashboard.monitoring.engine.ai",        icon: Sparkles, engine: "ai",        apiBase: `${API_BASE}/v1/engine/ai` },
];

// 鉴权头单源定义见 services/auth.ts (H6-T1)；传输单源见 services/httpClient.ts（#143/B5-11g）
import { clearAuthToken } from '../services/auth';
import { API_BASE, rawAuthedUrlFetch } from '../services/httpClient';

/**
 * 请求结果分类：403(denied) / 网络不可达(offline) / 5xx 等 HTTP 错误(error) 不再同归 null→UNKNOWN。
 *
 * <p>#143/B5-11g 传输合流：裸 `fetch(` 换成单源 `rawAuthedUrlFetch`，头集合逐键不变
 * （改前 `authHeaders()` = Content-Type + 条件 Authorization，单源只拼 Authorization，
 * 故 Content-Type 由调用面显式带上）；401/403/`ecos-403` 事件、content-type 容差、
 * 信封解包、`offline` 归类四面逐字留在本域。合流新增的唯一共享面 = PMO-43 网络态归一，
 * 它把传输故障改抛 `NetworkError` 并派发 `ecos-network-down`——本函数 `catch` 不分错误类型
 * 一律返回 `{kind:"offline"}` ⇒ 分类结果不变。</p>
 */
type FetchOutcome<T> =
  | { kind: "ok"; data: T | null }
  | { kind: "denied" }
  | { kind: "offline" }
  | { kind: "error"; status: number };

async function apiFetch<T>(url: string, id?: string): Promise<FetchOutcome<T>> {
  try {
    const res = await rawAuthedUrlFetch(url, { headers: { 'Content-Type': 'application/json' } });
    if (res.status === 401) {
      // Login expired — force re-login (PMO-43 T4). auth 单源清理，禁手删单键。
      clearAuthToken();
      window.location.hash = "#/login";
      return { kind: "error", status: 401 };
    }
    if (res.status === 403) {
      // Permission denied on a valid session — never log out.
      // Surface via the global toast (ToastProvider mounted at main.tsx);
      // the EngineMonitor child renders its own inline no-permission state.
      // H-001: `id` is the owning card's identity — lets each EngineCard
      // filter out 403s raised by sibling cards (cross-card mis-report).
      window.dispatchEvent(new CustomEvent("ecos-403", { detail: { id, url, timestamp: Date.now() } }));
      return { kind: "denied" };
    }
    if (!res.ok) return { kind: "error", status: res.status };
    const ct = res.headers.get("content-type");
    if (!ct || !ct.includes("application/json")) return { kind: "ok", data: null };
    const json = await res.json();
    if (json && typeof json === "object" && json.data !== undefined && "data" in json) return { kind: "ok", data: json.data as T };
    return { kind: "ok", data: json as T };
  } catch {
    return { kind: "offline" };
  }
}

interface HealthSummary {
  status: string;
  service?: string;
  version?: string;
  uptime?: string;
  components?: Record<string, any>;
}

interface StatusSummary {
  name?: string;
  status?: string;
  engine?: string;
}

type CardClassification = "up" | "down" | "denied" | "offline" | "error" | "unknown";

interface EngineCardProps {
  def: EngineDef;
  isExpanded: boolean;
  onToggle: () => void;
  onRefresh: () => void;
}

function EngineCard({ def, isExpanded, onToggle, onRefresh }: EngineCardProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const { showToast } = useToast();

  const [health, setHealth] = useState<HealthSummary | null>(null);
  const [status, setStatus] = useState<StatusSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [classification, setClassification] = useState<CardClassification>("unknown");
  const [errStatus, setErrStatus] = useState<number>(0);

  const load = useCallback(async () => {
    setLoading(true);
    const on403 = (e: Event) => {
      const detail = (e as CustomEvent).detail;
      if (detail?.id !== def.id) return; // ignore sibling cards' 403s
      showToast("error", t("common.noPermission"));
    };
    window.addEventListener("ecos-403", on403);
    const [h, s] = await Promise.all([
      apiFetch<HealthSummary>(`${def.apiBase}/health`, def.id),
      apiFetch<StatusSummary>(`${def.apiBase}/status`, def.id),
    ]);
    window.removeEventListener("ecos-403", on403);

    // 分类判定：denied > offline > error > ok(按状态值)
    let cls: CardClassification;
    if (h.kind === "denied" || s.kind === "denied") {
      cls = "denied";
    } else if (h.kind === "offline" && s.kind === "offline") {
      cls = "offline";
    } else if (h.kind === "error" || s.kind === "error") {
      const errStatus = h.kind === "error" ? h.status : (s.kind === "error" ? s.status : 0);
      cls = "error";
      setErrStatus(errStatus);
    } else {
      const hs = (h.kind === "ok" ? h.data?.status : undefined) || (s.kind === "ok" ? s.data?.status : undefined) || "";
      const up = hs.toUpperCase() === "UP" || hs.toUpperCase() === "OK" || hs.toUpperCase() === "HEALTHY" || hs.toUpperCase() === "RUNNING";
      cls = h.kind === "ok" || s.kind === "ok" ? (up ? "up" : "down") : "unknown";
    }
    setClassification(cls);
    setHealth(h.kind === "ok" ? h.data : null);
    setStatus(s.kind === "ok" ? s.data : null);
    setLoading(false);
  }, [def.id, def.apiBase, showToast, t]);

  useEffect(() => { load(); }, [load]);

  const healthStatus = (classification === "up"
    ? (health?.status || status?.status || "UP")
    : classification === "down"
      ? (health?.status || status?.status || "DOWN")
      : "").toUpperCase();

  const classLabel: Record<CardClassification, string> = {
    up: t("dashboard.monitoring.class.up"),
    down: t("dashboard.monitoring.class.down"),
    denied: t("dashboard.monitoring.class.denied"),
    offline: t("dashboard.monitoring.class.offline"),
    error: t("dashboard.monitoring.class.error", { status: errStatus }),
    unknown: t("dashboard.monitoring.class.unknown"),
  };

  const Icon = def.icon;

  return (
    <div className={`rounded-xl border ${styles.cardBorder} ${styles.cardBg} overflow-hidden transition-all`}>
      <button type="button" onClick={onToggle} className="w-full flex items-center gap-3 px-4 py-3 transition-colors hover:opacity-90">
        <Icon className={`w-5 h-5 ${styles.accentText}`} />
        <span className="flex-1 text-left font-semibold text-sm" style={{ color: "var(--cardText)" }}>{t(def.labelKey)}</span>
        {loading ? (
          <Loader2 className={`w-4 h-4 animate-spin ${styles.cardTextMuted}`} />
        ) : classification === "up" ? (
          <span className={`flex items-center gap-1 ${styles.successText}`}>
            <CheckCircle2 className="w-4 h-4" />
            <span className="text-[10px] font-bold">{healthStatus}</span>
          </span>
        ) : classification === "denied" ? (
          <span className={`flex items-center gap-1 ${styles.warningText}`}>
            <Lock className="w-4 h-4" />
            <span className="text-[10px] font-bold">{classLabel[classification]}</span>
          </span>
        ) : classification === "unknown" ? (
          <span className={`flex items-center gap-1 ${styles.cardTextMuted}`}>
            <AlertTriangle className="w-4 h-4" />
            <span className="text-[10px] font-bold">{classLabel[classification]}</span>
          </span>
        ) : (
          <span className={`flex items-center gap-1 ${styles.dangerText}`}>
            <XCircle className="w-4 h-4" />
            <span className="text-[10px] font-bold">{classification === "error" || classification === "offline" || classification === "down" ? classLabel[classification] : healthStatus}</span>
          </span>
        )}
        <ChevronDown className={`w-4 h-4 ${styles.cardTextMuted} transition-transform duration-200 ${isExpanded ? "rotate-180" : ""}`} />
      </button>

      {isExpanded && (
        <div className={`border-t ${styles.cardBorder}`}>
          <div className={`flex items-center justify-end px-3 py-1.5 border-b ${styles.cardBorder}`}>
            <button type="button" onClick={() => { onRefresh(); load(); }} className={`flex items-center gap-1 px-2 py-1 rounded text-[10px] font-semibold ${styles.appBg} ${styles.cardTextMuted} ${styles.sidebarHoverBg} transition-colors`}>
              <RefreshCw className="w-3 h-3" />
              {t("platform.monitoring.cardRefresh")}
            </button>
          </div>
          {classification === "denied" ? (
            /* 403：呈现专门的无权限卡片，不再混入 UNKNOWN */
            <div className={`flex items-center gap-2 px-4 py-6 text-xs ${styles.warningBg} ${styles.warningText}`}>
              <Lock className="w-4 h-4 shrink-0" />
              <span>{t("dashboard.monitoring.deniedCard")}</span>
            </div>
          ) : (
            <EngineMonitor engine={def.engine} initialHealth={health} initialStatus={status} />
          )}
        </div>
      )}
    </div>
  );
}

export default function MonitoringCenter() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [activeTab, setActiveTab] = useState(TABS[0].id);
  // 默认全部折叠：避免挂载即 6 卡 × (health+status) 并发风暴；展开由用户驱动
  const [expandedEngines, setExpandedEngines] = useState<Set<string>>(() => new Set());
  const [refreshKey, setRefreshKey] = useState(0);

  const toggleEngine = (id: string) => {
    setExpandedEngines(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  return (
    <div className="flex flex-col h-full overflow-hidden">
      <div className={`flex shrink-0 border-b ${styles.appBorder} ${styles.cardBg} px-2`}>
        {TABS.map(tab => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;
          return (
            <button type="button" key={tab.id} onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-1.5 px-4 py-2.5 text-xs font-medium transition-all border-b-2 -mb-px whitespace-nowrap cursor-pointer ${
                isActive
                  ? `${styles.accentBorder} ${styles.accentText} ${styles.badgeBg}`
                  : `border-transparent ${styles.cardTextMuted} hover:opacity-80`
              }`}>
              <Icon className="w-3.5 h-3.5" />
              <span>{t(tab.labelKey)}</span>
            </button>
          );
        })}
      </div>

      <div className="flex-1 overflow-auto min-h-0">
        {activeTab === "metrics" && <BasicMonitoringTab />}
        {activeTab === "engines" && (
          <div className="p-4 space-y-3">
            <div className="flex items-center justify-between mb-3">
              <div className={`text-sm font-semibold ${styles.cardTextMuted}`}>
                {t("platform.monitoring.enginesHeader")}
              </div>
              <button type="button" onClick={() => setRefreshKey(k => k + 1)}
                className={`flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-[10px] font-semibold ${styles.appBg} ${styles.cardTextMuted} ${styles.sidebarHoverBg} transition-colors cursor-pointer`}>
                <RefreshCw className="w-3 h-3" />
                {t("platform.monitoring.refreshAll")}
              </button>
            </div>
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
              {ENGINES.map(def => (
                <EngineCard
                  key={`${def.id}-${refreshKey}`}
                  def={def}
                  isExpanded={expandedEngines.has(def.id)}
                  onToggle={() => toggleEngine(def.id)}
                  onRefresh={() => {}}
                />
              ))}
            </div>
          </div>
        )}
        {activeTab === "twins" && <DigitalTwinTab />}
      </div>
    </div>
  );
}
