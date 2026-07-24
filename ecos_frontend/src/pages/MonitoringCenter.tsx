import React, { useState, useEffect, useCallback } from "react";
import { BarChart3, CpuIcon, ChevronDown, Activity, Shield, Database, Network, Brain, BookOpen, Sparkles, RefreshCw, CheckCircle2, XCircle, Loader2, AlertTriangle, Server, Settings } from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import BasicMonitoringTab from "./monitoring/tabs/BasicMonitoringTab";
import DigitalTwinTab from "./monitoring/tabs/DigitalTwinTab";
import EngineMonitor from "./EngineMonitor";

interface TabDef {
  id: string;
  label: string;
  labelZh: string;
  icon: React.ComponentType<{ className?: string }>;
}

const TABS: TabDef[] = [
  { id: "metrics", label: "Metrics", labelZh: "基础指标", icon: BarChart3 },
  { id: "engines", label: "Engines", labelZh: "引擎监控", icon: Activity },
  { id: "twins", label: "Digital Twin", labelZh: "数字孪生", icon: CpuIcon },
];

interface EngineDef {
  id: string;
  label: string;
  labelZh: string;
  icon: React.ComponentType<{ className?: string }>;
  engine: "security" | "data" | "ontology" | "cognitive" | "knowledge" | "ai";
  color: string;
  apiBase: string;
}

const ENGINES: EngineDef[] = [
  { id: "eng-security",  label: "Security Engine",  labelZh: "安全引擎", icon: Shield,    engine: "security",  color: "text-emerald-500", apiBase: "/api/v1/engine/security" },
  { id: "eng-data",      label: "Data Engine",      labelZh: "数据引擎", icon: Database,  engine: "data",      color: "text-blue-500",    apiBase: "/api/v1/engine/data" },
  { id: "eng-ontology",  label: "Ontology Engine",  labelZh: "本体引擎", icon: Network,   engine: "ontology",  color: "text-purple-500",  apiBase: "/api/v1/engine/ontology" },
  { id: "eng-cognitive", label: "Cognitive Engine", labelZh: "认知引擎", icon: Brain,     engine: "cognitive", color: "text-amber-500",   apiBase: "/api/v1/engine/cognitive" },
  { id: "eng-knowledge", label: "Knowledge Engine", labelZh: "知识引擎", icon: BookOpen,  engine: "knowledge", color: "text-cyan-500",    apiBase: "/api/v1/engine/knowledge" },
  { id: "eng-ai",        label: "AI Engine",        labelZh: "AI引擎",  icon: Sparkles,  engine: "ai",        color: "text-rose-500",    apiBase: "/api/v1/engine/ai" },
];

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem("token") || "";
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (token) headers["Authorization"] = `Bearer ${token}`;
  return headers;
}

async function apiFetch<T>(url: string): Promise<T | null> {
  try {
    const res = await fetch(url, { headers: authHeaders() });
    if (res.status === 401 || res.status === 403) {
      localStorage.removeItem("token");
      window.location.hash = "#/login";
      return null;
    }
    if (!res.ok) return null;
    const ct = res.headers.get("content-type");
    if (!ct || !ct.includes("application/json")) return null;
    const json = await res.json();
    if (json && typeof json === "object" && json.data !== undefined && "data" in json) return json.data as T;
    return json as T;
  } catch {
    return null;
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

interface EngineCardProps {
  def: EngineDef;
  isExpanded: boolean;
  onToggle: () => void;
  onRefresh: () => void;
}

function EngineCard({ def, isExpanded, onToggle, onRefresh }: EngineCardProps) {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const Icon = def.icon;

  const [health, setHealth] = useState<HealthSummary | null>(null);
  const [status, setStatus] = useState<StatusSummary | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    const [h, s] = await Promise.all([
      apiFetch<HealthSummary>(`${def.apiBase}/health`),
      apiFetch<StatusSummary>(`${def.apiBase}/status`),
    ]);
    setHealth(h);
    setStatus(s);
    setLoading(false);
  }, [def.apiBase]);

  useEffect(() => { load(); }, [load]);

  const healthStatus = health?.status?.toUpperCase() || status?.status?.toUpperCase() || "UNKNOWN";
  const isUp = healthStatus === "UP" || healthStatus === "OK" || healthStatus === "HEALTHY" || healthStatus === "RUNNING";

  return (
    <div className={`rounded-xl border ${styles.cardBorder} ${styles.cardBg} overflow-hidden transition-all`}>
      <button onClick={onToggle} className={`w-full flex items-center gap-3 px-4 py-3 transition-colors hover:opacity-90`}>
        <Icon className={`w-5 h-5 ${def.color}`} />
        <span className="flex-1 text-left font-semibold text-sm">{locale === "zh" ? def.labelZh : def.label}</span>
        {loading ? (
          <Loader2 className="w-4 h-4 animate-spin text-slate-400" />
        ) : isUp ? (
          <span className="flex items-center gap-1 text-emerald-500">
            <CheckCircle2 className="w-4 h-4" />
            <span className="text-[10px] font-bold">{healthStatus}</span>
          </span>
        ) : (
          <span className="flex items-center gap-1 text-red-400">
            <XCircle className="w-4 h-4" />
            <span className="text-[10px] font-bold">{healthStatus}</span>
          </span>
        )}
        <ChevronDown className={`w-4 h-4 ${styles.cardTextMuted} transition-transform duration-200 ${isExpanded ? "rotate-180" : ""}`} />
      </button>

      {isExpanded && (
        <div className={`border-t ${styles.cardBorder}`}>
          <div className={`flex items-center justify-end px-3 py-1.5 border-b ${styles.cardBorder}`}>
            <button onClick={() => { onRefresh(); load(); }} className="flex items-center gap-1 px-2 py-1 rounded text-[10px] font-semibold bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors">
              <RefreshCw className="w-3 h-3" />
              {locale === "zh" ? "刷新" : "Refresh"}
            </button>
          </div>
          <EngineMonitor engine={def.engine} initialHealth={health} initialStatus={status} />
        </div>
      )}
    </div>
  );
}

export default function MonitoringCenter() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const [activeTab, setActiveTab] = useState(TABS[0].id);
  const [expandedEngines, setExpandedEngines] = useState<Set<string>>(() => new Set(ENGINES.map(e => e.id)));
  const [refreshKey, setRefreshKey] = useState(0);

  const tl = (zh: string, en: string) => locale === "zh" ? zh : en;

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
            <button key={tab.id} onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-1.5 px-4 py-2.5 text-xs font-medium transition-all border-b-2 -mb-px whitespace-nowrap ${
                isActive
                  ? "border-indigo-500 text-indigo-600 dark:text-indigo-400 bg-indigo-50/50 dark:bg-indigo-900/20"
                  : `border-transparent ${styles.cardTextMuted} hover:opacity-80`
              }`}>
              <Icon className="w-3.5 h-3.5" />
              <span>{tl(tab.labelZh, tab.label)}</span>
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
                {tl("六引擎健康状态监控 — 点击展开查看详情", "Six-engine health monitoring — click to expand details")}
              </div>
              <button onClick={() => setRefreshKey(k => k + 1)}
                className="flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-[10px] font-semibold bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors">
                <RefreshCw className="w-3 h-3" />
                {tl("刷新全部", "Refresh All")}
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
