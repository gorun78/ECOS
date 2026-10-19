/**
 * ECOS EngineMonitor — 引擎管控面板
 *
 * 单一组件通过 `engine` prop 适配:
 *   security | data | ontology | cognitive | knowledge | ai
 *
 * API 端点:
 *   GET /api/v1/engine/{type}/health
 *   GET /api/v1/engine/{type}/config
 *   GET /api/v1/engine/{type}/status
 *
 * 去重约定：MonitoringCenter 卡片展开挂载本组件时若已通过 initialHealth/initialStatus
 * 提供首屏数据，则跳过对应的挂载即拉（刷新仅由用户点击「全部刷新」驱动）。
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback } from 'react';
import {
  Shield, Database, Network, Brain, BookOpen, Sparkles,
  Activity, Server, Settings, Loader2, AlertTriangle, Lock,
  RefreshCw, CheckCircle2, XCircle, ChevronDown,
} from 'lucide-react';
import { useLanguage } from '../components/LanguageContext';
import { useTheme } from '../components/ThemeContext';
import { useToast } from '../components/common/Toast';
import { NoAccessError, isNoAccessError } from '../api';

// ── Types ────────────────────────────────────────────────────

interface EngineMonitorProps {
  engine: 'security' | 'data' | 'ontology' | 'cognitive' | 'knowledge' | 'ai';
  initialHealth?: any;
  initialStatus?: any;
}

interface HealthData {
  status?: string;
  service?: string;
  uptime?: string;
  version?: string;
  [key: string]: any;
}

interface ConfigData {
  [key: string]: any;
}

interface StatusData {
  name?: string;
  status?: string;
  engine?: string;
  [key: string]: any;
}

const ENGINE_META: Record<string, { icon: React.ComponentType<{ className?: string }>; configPath?: string }> = {
  security: { icon: Shield },
  data:     { icon: Database, configPath: `${API_BASE}/v1/engine/data/settings` },
  ontology: { icon: Network },
  cognitive:{ icon: Brain },
  knowledge:{ icon: BookOpen },
  ai:       { icon: Sparkles },
};

// ── Helpers ──────────────────────────────────────────────────

// 传输单源（#143/B5-11g）：本 helper 只把裸 `fetch(` 换成 httpClient 的带身份裸响应通道
// `rawAuthedUrlFetch`，401/403/406→null/content-type 容差/信封解包五面逐字保留在本域
// （单源的 `apiFetch` 语义不同：401 走 handleAuthExpired、403 走 notifyNoAccess+ApiRequestError、
// 且无 content-type 容差 ⇒ 折过去就是语义变更，不属本批授权）。
// 头集合逐键不变：改前 `authHeaders()` = `Content-Type: application/json` +（有 token 才拼的）
// `Authorization: Bearer`；`rawAuthedUrlFetch` 只拼 Authorization，故 Content-Type 由本行显式带上。
// 合流新增的唯一共享面 = PMO-43 网络态归一（传输故障改抛 NetworkError 并派发 ecos-network-down，
// 改前是原生 TypeError "Failed to fetch"；本页 classifyError 只取 e.message ⇒ 上屏文案变化）。
// 同批观察（未改动，避免夹带语义变更）：401 腿仍写 `localStorage.removeItem('token')`，
// 而 `MonitoringCenter` 同类腿已用 auth 单源 `clearAuthToken()`（「禁手删单键」）⇒ 并入 #108 队列。
import { API_BASE, rawAuthedUrlFetch } from '../services/httpClient';

async function apiFetch<T = any>(url: string): Promise<T> {
  const res = await rawAuthedUrlFetch(url, { headers: { 'Content-Type': 'application/json' } });
  if (res.status === 401) {
    // Token expired/invalid → clear auth & re-login (AM-30 event,
    // PMO-43 T4, same behaviour as api.ts handleAuthExpired).
    localStorage.removeItem('token');
    window.location.hash = '#/login';
    throw new Error('HTTP_401');
  }
  if (res.status === 403) {
    // Permission denied on a valid session → canonical NoAccessError
    // (api.ts contract). UI layers detect via isNoAccessError() and
    // render t("common.noPermission"). Never log the user out.
    throw new NoAccessError();
  }
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(text || `HTTP ${res.status}`);
  }
  const ct = res.headers.get('content-type');
  if (!ct || !ct.includes('application/json')) return null as unknown as T;
  const json = await res.json();
  // unwrap common envelopes
  if (json && typeof json === 'object' && json.data !== undefined && 'data' in json) return json.data as T;
  return json as T;
}

/** 加载某端点：区分 403（noAccess）与真实错误（message），不再把 NoAccessError 的原始文本上屏。 */
function classifyError(e: unknown): { noAccess: boolean; message: string | null } {
  if (isNoAccessError(e)) return { noAccess: true, message: null };
  return { noAccess: false, message: e instanceof Error ? e.message : 'Unknown error' };
}

// ── Component ────────────────────────────────────────────────

export default function EngineMonitor({ engine, initialHealth, initialStatus }: EngineMonitorProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const { showToast } = useToast();
  const meta = ENGINE_META[engine];

  // ── State ──（父卡片已拉取到的 health/status 直接作为初值，挂载不重复拉取）
  const [health, setHealth] = useState<HealthData | null>(initialHealth ?? null);
  const [healthLoading, setHealthLoading] = useState(initialHealth == null);
  const [healthError, setHealthError] = useState<string | null>(null);
  const [healthNoAccess, setHealthNoAccess] = useState(false);

  const [config, setConfig] = useState<ConfigData | null>(null);
  const [configLoading, setConfigLoading] = useState(true);
  const [configError, setConfigError] = useState<string | null>(null);
  const [configNoAccess, setConfigNoAccess] = useState(false);

  const [status, setStatus] = useState<StatusData | null>(initialStatus ?? null);
  const [statusLoading, setStatusLoading] = useState(initialStatus == null);
  const [statusError, setStatusError] = useState<string | null>(null);
  const [statusNoAccess, setStatusNoAccess] = useState(false);

  // Raw JSON 面板默认折叠（三份 stringify 不再首屏常驻）
  const [showRaw, setShowRaw] = useState(false);

  // ── Load health ──
  const loadHealth = useCallback(async () => {
    setHealthLoading(true);
    setHealthError(null);
    setHealthNoAccess(false);
    try {
      const data = await apiFetch<HealthData>(`${API_BASE}/v1/engine/${engine}/health`);
      setHealth(data);
    } catch (e) {
      const c = classifyError(e);
      setHealthNoAccess(c.noAccess);
      setHealthError(c.noAccess ? null : c.message);
      if (!c.noAccess) showToast('error', t('common.loadFailed'));
    } finally {
      setHealthLoading(false);
    }
  }, [engine, showToast, t]);

  // ── Load config ──
  const loadConfig = useCallback(async () => {
    setConfigLoading(true);
    setConfigError(null);
    setConfigNoAccess(false);
    try {
      const data = await apiFetch<ConfigData>(meta.configPath || `${API_BASE}/v1/engine/${engine}/config`);
      setConfig(data);
    } catch (e) {
      const c = classifyError(e);
      setConfigNoAccess(c.noAccess);
      setConfigError(c.noAccess ? null : c.message);
      if (!c.noAccess) showToast('error', t('common.loadFailed'));
    } finally {
      setConfigLoading(false);
    }
  }, [engine, meta.configPath, showToast, t]);

  // ── Load status ──
  const loadStatus = useCallback(async () => {
    setStatusLoading(true);
    setStatusError(null);
    setStatusNoAccess(false);
    try {
      const data = await apiFetch<StatusData>(`${API_BASE}/v1/engine/${engine}/status`);
      setStatus(data);
    } catch (e) {
      const c = classifyError(e);
      setStatusNoAccess(c.noAccess);
      setStatusError(c.noAccess ? null : c.message);
      if (!c.noAccess) showToast('error', t('common.loadFailed'));
    } finally {
      setStatusLoading(false);
    }
  }, [engine, showToast, t]);

  // ── Initial load：initialHealth/initialStatus 已在 state 初值中提供则跳过对应拉取（去重） ──
  useEffect(() => {
    if (initialHealth == null) loadHealth();
    loadConfig();
    if (initialStatus == null) loadStatus();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ── Refresh all（用户显式动作，三条全拉） ──
  const handleRefreshAll = () => {
    loadHealth();
    loadConfig();
    loadStatus();
  };

  // ── Derive health status ──
  const healthStatus = health?.status?.toUpperCase?.() || 'UNKNOWN';
  const isUp = healthStatus === 'UP' || healthStatus === 'OK' || healthStatus === 'HEALTHY';

  // ── Config keys ──
  const configKeys = config ? Object.keys(config).filter(k => k !== 'data' && typeof config[k] !== 'object') : [];
  const configDisplayKeys = configKeys.length > 0 ? configKeys : (config ? Object.keys(config) : []);

  const statusName = status?.name || status?.engine || t(`dashboard.monitoring.engine.${engine}`);
  const statusValue = status?.status || healthStatus;

  const Icon = meta.icon;
  const title = t(`dashboard.monitoring.engine.${engine}`);

  /** 错误/403 行：403 走友好文案，其余显示真实错误信息。 */
  const ErrorLine = ({ error, noAccess }: { error: string | null; noAccess: boolean }) => (
    <div className={`flex items-center gap-2 text-sm ${noAccess ? styles.warningText : styles.dangerText}`}>
      {noAccess ? <Lock className="w-4 h-4 shrink-0" /> : <AlertTriangle className="w-4 h-4 shrink-0" />}
      <span>{noAccess ? t('common.noPermission') : error}</span>
    </div>
  );

  return (
    <div className={`${styles.appBg} min-h-screen p-6`}>
      <div className="max-w-6xl mx-auto space-y-6">

        {/* ── Header ── */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Icon className={`w-7 h-7 ${styles.accentText}`} />
            <div>
              <h1 className={`text-2xl font-bold ${styles.cardText}`}>{title}</h1>
              <p className={`text-xs ${styles.muted} mt-0.5`}>
                {t("platform.monitoring.em.subtitle")}
              </p>
            </div>
          </div>
          <button type="button"
            onClick={handleRefreshAll}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg ${styles.inputBg} ${styles.inputText} text-sm transition-colors border ${styles.inputBorder} ${styles.sidebarHoverBg} cursor-pointer`}
          >
            <RefreshCw className="w-3.5 h-3.5" />
            {t("platform.monitoring.refreshAll")}
          </button>
        </div>

        {/* ── 3 Cards Grid ── */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-5">

          {/* ──────────────── Card 1: Engine Health ──────────────── */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 space-y-4`}>
            <div className="flex items-center gap-2">
              <Activity className={`w-4 h-4 ${styles.cardTextMuted}`} />
              <h3 className={`text-sm font-semibold uppercase tracking-wider ${styles.muted}`}>
                {t("platform.monitoring.em.healthTitle")}
              </h3>
            </div>

            {healthLoading ? (
              <div className={`flex items-center gap-2 ${styles.cardTextMuted} text-sm`}>
                <Loader2 className="w-4 h-4 animate-spin" />
                {t("platform.monitoring.em.loading")}
              </div>
            ) : healthNoAccess || healthError ? (
              <ErrorLine error={healthError} noAccess={healthNoAccess} />
            ) : health ? (
              <div className="space-y-3">
                {/* Status badge */}
                <div className="flex items-center gap-2">
                  {isUp ? (
                    <CheckCircle2 className={`w-5 h-5 ${styles.successText}`} />
                  ) : (
                    <XCircle className={`w-5 h-5 ${styles.dangerText}`} />
                  )}
                  <span className={`text-lg font-bold ${isUp ? styles.successText : styles.dangerText}`}>
                    {healthStatus}
                  </span>
                </div>
                {/* Extra info */}
                {health?.service && (
                  <div className="flex items-center justify-between text-xs">
                    <span className={`${styles.muted}`}>{t("dashboard.monitoring.field.service")}</span>
                    <span className={styles.cardText}>{health.service}</span>
                  </div>
                )}
                {health?.version && (
                  <div className="flex items-center justify-between text-xs">
                    <span className={`${styles.muted}`}>{t("dashboard.monitoring.field.version")}</span>
                    <span className={styles.cardText}>{health.version}</span>
                  </div>
                )}
                {health?.uptime && (
                  <div className="flex items-center justify-between text-xs">
                    <span className={`${styles.muted}`}>{t("dashboard.monitoring.field.uptime")}</span>
                    <span className={styles.cardText}>{health.uptime}</span>
                  </div>
                )}
              </div>
            ) : (
              <p className={`text-xs ${styles.muted}`}>{t("platform.monitoring.em.noHealth")}</p>
            )}
          </div>

          {/* ──────────────── Card 2: Config Info ──────────────── */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 space-y-4`}>
            <div className="flex items-center gap-2">
              <Settings className={`w-4 h-4 ${styles.cardTextMuted}`} />
              <h3 className={`text-sm font-semibold uppercase tracking-wider ${styles.muted}`}>
                {t("platform.monitoring.em.configTitle")}
              </h3>
            </div>

            {configLoading ? (
              <div className={`flex items-center gap-2 ${styles.cardTextMuted} text-sm`}>
                <Loader2 className="w-4 h-4 animate-spin" />
                {t("platform.monitoring.em.loading")}
              </div>
            ) : configNoAccess || configError ? (
              <ErrorLine error={configError} noAccess={configNoAccess} />
            ) : config && Object.keys(config).length > 0 ? (
              <div className="space-y-2 max-h-64 overflow-y-auto">
                {Object.entries(config).map(([key, value]) => {
                  const displayValue = typeof value === 'object' && value !== null
                    ? JSON.stringify(value)
                    : String(value ?? '—');
                  return (
                    <div key={key} className="flex items-start justify-between gap-2 text-xs">
                      <span className={`${styles.muted} font-mono shrink-0`}>{key}</span>
                      <span className={`${styles.cardText} text-right break-all`}>{displayValue}</span>
                    </div>
                  );
                })}
              </div>
            ) : (
              <p className={`text-xs ${styles.muted}`}>{t("platform.monitoring.em.noConfig")}</p>
            )}
          </div>

          {/* ──────────────── Card 3: Runtime Status ──────────────── */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 space-y-4`}>
            <div className="flex items-center gap-2">
              <Server className={`w-4 h-4 ${styles.cardTextMuted}`} />
              <h3 className={`text-sm font-semibold uppercase tracking-wider ${styles.muted}`}>
                {t("platform.monitoring.em.statusTitle")}
              </h3>
            </div>

            {statusLoading ? (
              <div className={`flex items-center gap-2 ${styles.cardTextMuted} text-sm`}>
                <Loader2 className="w-4 h-4 animate-spin" />
                {t("platform.monitoring.em.loading")}
              </div>
            ) : statusNoAccess || statusError ? (
              <ErrorLine error={statusError} noAccess={statusNoAccess} />
            ) : status ? (
              <div className="space-y-3">
                <div className="flex items-center justify-between text-sm">
                  <span className={`${styles.muted}`}>{t("platform.monitoring.em.name")}</span>
                  <span className={`font-semibold ${styles.cardText}`}>{statusName}</span>
                </div>
                <div className="flex items-center justify-between text-sm">
                  <span className={`${styles.muted}`}>{t("dashboard.monitoring.field.status")}</span>
                  <span className={`font-bold ${statusValue === 'UP' || statusValue === 'running' ? styles.successText : styles.dangerText}`}>
                    {statusValue}
                  </span>
                </div>
                {/* Extra status fields */}
                {status && Object.entries(status)
                  .filter(([k]) => !['name', 'status', 'engine', 'service'].includes(k))
                  .slice(0, 6)
                  .map(([key, val]) => (
                    <div key={key} className="flex items-center justify-between text-xs">
                      <span className={`${styles.muted} font-mono`}>{key}</span>
                      <span className={styles.cardText}>
                        {typeof val === 'object' ? JSON.stringify(val) : String(val ?? '—')}
                      </span>
                    </div>
                  ))}
              </div>
            ) : (
              <p className={`text-xs ${styles.muted}`}>{t("platform.monitoring.em.noStatus")}</p>
            )}
          </div>
        </div>

        {/* ── Raw Data Panel：默认折叠，用户显式展开 ── */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 space-y-3`}>
          <button
            type="button"
            onClick={() => setShowRaw(v => !v)}
            className={`w-full flex items-center justify-between gap-2 cursor-pointer ${styles.muted}`}
            aria-expanded={showRaw}
          >
            <h3 className={`text-xs font-semibold uppercase tracking-wider`}>
              {t("platform.monitoring.em.rawTitle")}
            </h3>
            <span className="flex items-center gap-1 text-[10px] font-mono">
              <ChevronDown className={`w-3.5 h-3.5 transition-transform ${showRaw ? "rotate-180" : ""}`} />
              {showRaw ? t("dashboard.monitoring.raw.hide") : t("dashboard.monitoring.raw.show")}
            </span>
          </button>
          {showRaw && (
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="space-y-1">
                <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>/health</span>
                <pre className={`text-[11px] font-mono ${styles.islandBg} ${styles.islandText} rounded-lg p-3 overflow-x-auto max-h-48`}>
                  {health ? JSON.stringify(health, null, 2) : (healthError || '—')}
                </pre>
              </div>
              <div className="space-y-1">
                <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>{meta.configPath ? '/settings' : '/config'}</span>
                <pre className={`text-[11px] font-mono ${styles.islandBg} ${styles.islandText} rounded-lg p-3 overflow-x-auto max-h-48`}>
                  {config ? JSON.stringify(config, null, 2) : (configError || '—')}
                </pre>
              </div>
              <div className="space-y-1">
                <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>/status</span>
                <pre className={`text-[11px] font-mono ${styles.islandBg} ${styles.islandText} rounded-lg p-3 overflow-x-auto max-h-48`}>
                  {status ? JSON.stringify(status, null, 2) : (statusError || '—')}
                </pre>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
