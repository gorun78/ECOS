/**
 * ECOS EngineMonitor — 四引擎管控面板
 *
 * 单一组件通过 `engine` prop 适配:
 *   security | data | ontology | cognitive
 *
 * API 端点:
 *   GET /api/v1/engine/{type}/health
 *   GET /api/v1/engine/{type}/config
 *   GET /api/v1/engine/{type}/status
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback } from 'react';
import {
  Shield, Database, Network, Brain,
  Activity, Server, Settings, Loader2, AlertTriangle,
  RefreshCw, CheckCircle2, XCircle,
} from 'lucide-react';
import { useLanguage } from '../components/LanguageContext';
import { useTheme } from '../components/ThemeContext';

// ── Types ────────────────────────────────────────────────────

interface EngineMonitorProps {
  engine: 'security' | 'data' | 'ontology' | 'cognitive';
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

const ENGINE_META: Record<string, { label: string; labelZh: string; icon: React.ComponentType<{ className?: string }>; color: string }> = {
  security: { label: 'Security Engine', labelZh: '安全引擎', icon: Shield, color: 'text-emerald-400' },
  data:     { label: 'Data Engine', labelZh: '数据引擎', icon: Database, color: 'text-blue-400' },
  ontology: { label: 'Ontology Engine', labelZh: '本体引擎', icon: Network, color: 'text-purple-400' },
  cognitive:{ label: 'Cognitive Engine', labelZh: '认知引擎', icon: Brain, color: 'text-amber-400' },
};

// ── Helpers ──────────────────────────────────────────────────

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem('token') || '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return headers;
}

async function apiFetch<T = any>(url: string): Promise<T> {
  const res = await fetch(url, { headers: authHeaders() });
  if (res.status === 401 || res.status === 403) {
    localStorage.removeItem('token');
    window.location.hash = '#/login';
    throw new Error('登录已过期，请重新登录');
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

// ── Component ────────────────────────────────────────────────

export default function EngineMonitor({ engine }: EngineMonitorProps) {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const meta = ENGINE_META[engine];

  // ── Toast ──
  const [toast, setToast] = useState<{ type: 'success' | 'info' | 'error'; msg: string } | null>(null);
  const showToast = useCallback((type: 'success' | 'info' | 'error', msg: string) => {
    setToast({ type, msg });
    setTimeout(() => setToast(null), 3500);
  }, []);

  // ── State ──
  const [health, setHealth] = useState<HealthData | null>(null);
  const [healthLoading, setHealthLoading] = useState(true);
  const [healthError, setHealthError] = useState<string | null>(null);

  const [config, setConfig] = useState<ConfigData | null>(null);
  const [configLoading, setConfigLoading] = useState(true);
  const [configError, setConfigError] = useState<string | null>(null);

  const [status, setStatus] = useState<StatusData | null>(null);
  const [statusLoading, setStatusLoading] = useState(true);
  const [statusError, setStatusError] = useState<string | null>(null);

  // ── Load health ──
  const loadHealth = useCallback(async () => {
    setHealthLoading(true);
    setHealthError(null);
    try {
      const data = await apiFetch<HealthData>(`/api/v1/engine/${engine}/health`);
      setHealth(data);
    } catch (e: any) {
      setHealthError(e.message || 'Unknown error');
      showToast('error', `健康检查失败: ${e.message}`);
    } finally {
      setHealthLoading(false);
    }
  }, [engine, showToast]);

  // ── Load config ──
  const loadConfig = useCallback(async () => {
    setConfigLoading(true);
    setConfigError(null);
    try {
      const data = await apiFetch<ConfigData>(`/api/v1/engine/${engine}/config`);
      setConfig(data);
    } catch (e: any) {
      setConfigError(e.message || 'Unknown error');
      showToast('error', `配置加载失败: ${e.message}`);
    } finally {
      setConfigLoading(false);
    }
  }, [engine, showToast]);

  // ── Load status ──
  const loadStatus = useCallback(async () => {
    setStatusLoading(true);
    setStatusError(null);
    try {
      const data = await apiFetch<StatusData>(`/api/v1/engine/${engine}/status`);
      setStatus(data);
    } catch (e: any) {
      setStatusError(e.message || 'Unknown error');
      showToast('error', `状态加载失败: ${e.message}`);
    } finally {
      setStatusLoading(false);
    }
  }, [engine, showToast]);

  // ── Initial load ──
  useEffect(() => {
    loadHealth();
    loadConfig();
    loadStatus();
  }, [loadHealth, loadConfig, loadStatus]);

  // ── Refresh all ──
  const handleRefreshAll = () => {
    loadHealth();
    loadConfig();
    loadStatus();
    showToast('info', '正在刷新引擎数据...');
  };

  // ── Derive health status ──
  const healthStatus = health?.status?.toUpperCase?.() || 'UNKNOWN';
  const isUp = healthStatus === 'UP' || healthStatus === 'OK' || healthStatus === 'HEALTHY';

  // ── Config keys ──
  const configKeys = config ? Object.keys(config).filter(k => k !== 'data' && typeof config[k] !== 'object') : [];
  const configDisplayKeys = configKeys.length > 0 ? configKeys : (config ? Object.keys(config) : []);

  const statusName = status?.name || status?.engine || meta.label;
  const statusValue = status?.status || healthStatus;

  const Icon = meta.icon;
  const title = locale === 'zh' ? meta.labelZh : meta.label;

  return (
    <div className={`${styles.appBg} min-h-screen p-6`}>
      <div className="max-w-6xl mx-auto space-y-6">

        {/* ── Header ── */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Icon className={`w-7 h-7 ${meta.color}`} />
            <div>
              <h1 className={`text-2xl font-bold ${styles.cardText}`}>{title}</h1>
              <p className={`text-xs ${styles.muted} mt-0.5`}>
                {locale === 'zh' ? '引擎健康监控、配置信息与运行状态' : 'Engine health, configuration & runtime status'}
              </p>
            </div>
          </div>
          <button
            onClick={handleRefreshAll}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 text-sm transition-colors border border-slate-700"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            {locale === 'zh' ? '刷新全部' : 'Refresh All'}
          </button>
        </div>

        {/* ── 3 Cards Grid ── */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-5">

          {/* ──────────────── Card 1: Engine Health ──────────────── */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 space-y-4`}>
            <div className="flex items-center gap-2">
              <Activity className="w-4 h-4 text-slate-400" />
              <h3 className={`text-sm font-semibold uppercase tracking-wider ${styles.muted}`}>
                {locale === 'zh' ? '引擎状态' : 'Engine Health'}
              </h3>
            </div>

            {healthLoading ? (
              <div className="flex items-center gap-2 text-slate-400 text-sm">
                <Loader2 className="w-4 h-4 animate-spin" />
                {locale === 'zh' ? '加载中...' : 'Loading...'}
              </div>
            ) : healthError ? (
              <div className="flex items-center gap-2 text-red-400 text-sm">
                <AlertTriangle className="w-4 h-4" />
                {healthError}
              </div>
            ) : health ? (
              <div className="space-y-3">
                {/* Status badge */}
                <div className="flex items-center gap-2">
                  {isUp ? (
                    <CheckCircle2 className="w-5 h-5 text-emerald-400" />
                  ) : (
                    <XCircle className="w-5 h-5 text-red-400" />
                  )}
                  <span className={`text-lg font-bold ${isUp ? 'text-emerald-400' : 'text-red-400'}`}>
                    {healthStatus}
                  </span>
                </div>
                {/* Extra info */}
                {health?.service && (
                  <div className="flex items-center justify-between text-xs">
                    <span className={`${styles.muted}`}>Service</span>
                    <span className={styles.cardText}>{health.service}</span>
                  </div>
                )}
                {health?.version && (
                  <div className="flex items-center justify-between text-xs">
                    <span className={`${styles.muted}`}>Version</span>
                    <span className={styles.cardText}>{health.version}</span>
                  </div>
                )}
                {health?.uptime && (
                  <div className="flex items-center justify-between text-xs">
                    <span className={`${styles.muted}`}>Uptime</span>
                    <span className={styles.cardText}>{health.uptime}</span>
                  </div>
                )}
              </div>
            ) : (
              <p className={`text-xs ${styles.muted}`}>{locale === 'zh' ? '无数据' : 'No data'}</p>
            )}
          </div>

          {/* ──────────────── Card 2: Config Info ──────────────── */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 space-y-4`}>
            <div className="flex items-center gap-2">
              <Settings className="w-4 h-4 text-slate-400" />
              <h3 className={`text-sm font-semibold uppercase tracking-wider ${styles.muted}`}>
                {locale === 'zh' ? '配置信息' : 'Configuration'}
              </h3>
            </div>

            {configLoading ? (
              <div className="flex items-center gap-2 text-slate-400 text-sm">
                <Loader2 className="w-4 h-4 animate-spin" />
                {locale === 'zh' ? '加载中...' : 'Loading...'}
              </div>
            ) : configError ? (
              <div className="flex items-center gap-2 text-red-400 text-sm">
                <AlertTriangle className="w-4 h-4" />
                {configError}
              </div>
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
              <p className={`text-xs ${styles.muted}`}>{locale === 'zh' ? '无配置数据' : 'No config data'}</p>
            )}
          </div>

          {/* ──────────────── Card 3: Runtime Status ──────────────── */}
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 space-y-4`}>
            <div className="flex items-center gap-2">
              <Server className="w-4 h-4 text-slate-400" />
              <h3 className={`text-sm font-semibold uppercase tracking-wider ${styles.muted}`}>
                {locale === 'zh' ? '运行状态' : 'Runtime Status'}
              </h3>
            </div>

            {statusLoading ? (
              <div className="flex items-center gap-2 text-slate-400 text-sm">
                <Loader2 className="w-4 h-4 animate-spin" />
                {locale === 'zh' ? '加载中...' : 'Loading...'}
              </div>
            ) : statusError ? (
              <div className="flex items-center gap-2 text-red-400 text-sm">
                <AlertTriangle className="w-4 h-4" />
                {statusError}
              </div>
            ) : status ? (
              <div className="space-y-3">
                <div className="flex items-center justify-between text-sm">
                  <span className={`${styles.muted}`}>{locale === 'zh' ? '名称' : 'Name'}</span>
                  <span className={`font-semibold ${styles.cardText}`}>{statusName}</span>
                </div>
                <div className="flex items-center justify-between text-sm">
                  <span className={`${styles.muted}`}>Status</span>
                  <span className={`font-bold ${statusValue === 'UP' || statusValue === 'running' ? 'text-emerald-400' : 'text-red-400'}`}>
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
              <p className={`text-xs ${styles.muted}`}>{locale === 'zh' ? '无状态数据' : 'No status data'}</p>
            )}
          </div>
        </div>

        {/* ── Raw Data Panel ── */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 space-y-3`}>
          <h3 className={`text-xs font-semibold uppercase tracking-wider ${styles.muted}`}>
            {locale === 'zh' ? '原始响应数据' : 'Raw API Responses'}
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="space-y-1">
              <span className="text-[10px] font-mono text-slate-500">/health</span>
              <pre className={`text-[11px] font-mono ${styles.cardText} bg-black/20 dark:bg-black/40 rounded-lg p-3 overflow-x-auto max-h-48`}>
                {health ? JSON.stringify(health, null, 2) : (healthError || '—')}
              </pre>
            </div>
            <div className="space-y-1">
              <span className="text-[10px] font-mono text-slate-500">/config</span>
              <pre className={`text-[11px] font-mono ${styles.cardText} bg-black/20 dark:bg-black/40 rounded-lg p-3 overflow-x-auto max-h-48`}>
                {config ? JSON.stringify(config, null, 2) : (configError || '—')}
              </pre>
            </div>
            <div className="space-y-1">
              <span className="text-[10px] font-mono text-slate-500">/status</span>
              <pre className={`text-[11px] font-mono ${styles.cardText} bg-black/20 dark:bg-black/40 rounded-lg p-3 overflow-x-auto max-h-48`}>
                {status ? JSON.stringify(status, null, 2) : (statusError || '—')}
              </pre>
            </div>
          </div>
        </div>

        {/* ── Toast ── */}
        {toast && (
          <div className="fixed bottom-6 right-6 z-50 animate-fade-in">
            <div className={`px-4 py-2.5 rounded-lg shadow-lg text-sm font-medium flex items-center gap-2
              ${toast.type === 'success' ? 'bg-emerald-600 text-white' : ''}
              ${toast.type === 'error' ? 'bg-red-600 text-white' : ''}
              ${toast.type === 'info' ? 'bg-slate-700 text-slate-100' : ''}
            `}>
              {toast.type === 'success' && <CheckCircle2 className="w-4 h-4" />}
              {toast.type === 'error' && <AlertTriangle className="w-4 h-4" />}
              {toast.type === 'info' && <Activity className="w-4 h-4" />}
              {toast.msg}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
