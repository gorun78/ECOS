/**
 * EngineConfigView — 本体引擎配置面板（E-2 补前端消费）
 *
 * 后端：OntologyConfigController（config_group=ontology-engine）
 *   GET  /api/v1/engine/ontology/settings           → ApiResponse<OntologyConfigVO[]>
 *   GET  /api/v1/engine/ontology/settings/defaults  → ApiResponse<OntologyConfigDefaultVO>
 *   PUT  /api/v1/engine/ontology/settings           → body { items: [{configKey, configValue}] }
 *   POST /api/v1/engine/ontology/settings/refresh   → 刷新引擎侧缓存
 *
 * 无假真理：加载失败显式错误横幅 + 重试；保存只提交变更项；默认值事实源是后端 /settings/defaults。
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertTriangle, RefreshCw, RotateCcw, Save, Settings } from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import {
  fetchEngineSettings,
  fetchEngineSettingsDefaults,
  updateEngineSettings,
  refreshEngineSettings,
} from '../../services/ontologyApi';

interface Props {
  showToast?: (type: 'success' | 'info' | 'error', message: string) => void;
}

/** 后端 VO 字段（snake_case JSON） */
interface ConfigRow {
  configKey?: string;
  config_key?: string;
  configValue?: string;
  config_value?: string;
  configLabel?: string;
  config_label?: string;
  description?: string;
}

function rowKey(r: ConfigRow): string {
  return String(r.configKey ?? r.config_key ?? '');
}

function rowValue(r: ConfigRow): string {
  return String(r.configValue ?? r.config_value ?? '');
}

export default function EngineConfigView({ showToast }: Props) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  const [rows, setRows] = useState<ConfigRow[]>([]);
  const [values, setValues] = useState<Record<string, string>>({});
  const [originalValues, setOriginalValues] = useState<Record<string, string>>({});
  const [defaults, setDefaults] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  const tf = useCallback(
    (key: string, params?: Record<string, string | number>) => {
      let s = t(key);
      if (params) {
        for (const [k, v] of Object.entries(params)) {
          s = s.replace(new RegExp(`\\{${k}\\}`, 'g'), String(v));
        }
      }
      return s;
    },
    [t],
  );

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const [settingsResp, defaultsResp] = await Promise.all([
        fetchEngineSettings(),
        fetchEngineSettingsDefaults().catch((): null => null),
      ]);
      const list: ConfigRow[] = Array.isArray(settingsResp)
        ? settingsResp
        : ((settingsResp as { data?: ConfigRow[] })?.data ?? []);
      setRows(list);

      const nextValues: Record<string, string> = {};
      for (const r of list) {
        const k = rowKey(r);
        if (k) nextValues[k] = rowValue(r);
      }

      // defaults 形态宽容处理：可能是扁平 Map 或带 defaults 字段的对象
      const flatDefaults: Record<string, string> = {};
      const raw = (defaultsResp as { data?: unknown })?.data ?? defaultsResp;
      const container = (raw ?? {}) as Record<string, unknown>;
      const defaultsMap = (container.defaults ?? container) as Record<string, unknown>;
      if (defaultsMap && typeof defaultsMap === 'object') {
        for (const [k, v] of Object.entries(defaultsMap)) {
          if (v === null || typeof v !== 'object') flatDefaults[k] = String(v ?? '');
        }
      }

      setDefaults(flatDefaults);
      setValues(nextValues);
      setOriginalValues({ ...nextValues });
    } catch (e) {
      setLoadError(tf('ow.engineConfig.loadFailed', { error: e instanceof Error ? e.message : String(e) }));
    } finally {
      setLoading(false);
    }
  }, [tf]);

  useEffect(() => {
    load();
  }, [load]);

  const changedKeys = useMemo(
    () => Object.keys(values).filter((k) => String(values[k] ?? '') !== String(originalValues[k] ?? '')),
    [values, originalValues],
  );

  const handleChange = useCallback((key: string, value: string) => {
    setValues((prev) => ({ ...prev, [key]: value }));
  }, []);

  const handleRestoreDefaults = useCallback(() => {
    if (Object.keys(defaults).length === 0) return;
    setValues((prev) => ({ ...prev, ...defaults }));
    showToast?.('info', tf('ow.engineConfig.restored'));
  }, [defaults, showToast, tf]);

  const handleSave = useCallback(async () => {
    if (changedKeys.length === 0) {
      showToast?.('info', tf('ow.engineConfig.noChanges'));
      return;
    }
    setSaving(true);
    try {
      await updateEngineSettings(
        changedKeys.map((k) => ({ config_key: k, config_value: String(values[k] ?? '') })),
      );
      setOriginalValues({ ...values });
      showToast?.('success', tf('ow.engineConfig.saved', { count: changedKeys.length }));
    } catch (e) {
      showToast?.('error', tf('ow.engineConfig.saveFailed', { error: e instanceof Error ? e.message : String(e) }));
    } finally {
      setSaving(false);
    }
  }, [changedKeys, values, showToast, tf]);

  const handleRefresh = useCallback(async () => {
    setRefreshing(true);
    try {
      await refreshEngineSettings();
      showToast?.('success', tf('ow.engineConfig.refreshed'));
    } catch (e) {
      showToast?.('error', tf('ow.engineConfig.refreshFailed', { error: e instanceof Error ? e.message : String(e) }));
    } finally {
      setRefreshing(false);
    }
  }, [showToast, tf]);

  return (
    <div className={`flex-1 flex flex-col min-h-0 ${styles.cardBg}`}>
      {/* Header */}
      <div className={`flex items-center gap-2 px-4 py-2.5 border-b ${styles.cardBorder} ${styles.cardBg} shrink-0`}>
        <Settings size={16} className={styles.cardTextMuted} />
        <div className="min-w-0">
          <div className={`text-sm font-bold ${styles.cardText}`}>{t('ow.engineConfig.title')}</div>
          <div className={`text-[10px] ${styles.cardTextMuted} truncate`}>{t('ow.engineConfig.desc')}</div>
        </div>
        <div className="ml-auto flex items-center gap-1.5">
          <button
            type="button"
            onClick={handleRestoreDefaults}
            disabled={loading || Object.keys(defaults).length === 0}
            className={`flex items-center gap-1 px-2.5 py-1 rounded text-[10px] font-semibold border ${styles.cardBorder} ${styles.cardText} disabled:opacity-50`}
          >
            <RotateCcw size={11} />
            {t('ow.engineConfig.restoreDefaults')}
          </button>
          <button
            type="button"
            onClick={handleRefresh}
            disabled={refreshing}
            className={`flex items-center gap-1 px-2.5 py-1 rounded text-[10px] font-semibold border ${styles.cardBorder} ${styles.cardText} disabled:opacity-50`}
          >
            <RefreshCw size={11} className={refreshing ? 'animate-spin' : ''} />
            {refreshing ? t('ow.engineConfig.refreshing') : t('ow.engineConfig.refresh')}
          </button>
          <button
            type="button"
            onClick={handleSave}
            disabled={saving || loading || changedKeys.length === 0}
            className={`flex items-center gap-1 px-2.5 py-1 rounded text-[10px] font-semibold ${styles.accentBg} text-white disabled:opacity-50`}
          >
            <Save size={11} />
            {saving ? t('ow.engineConfig.saving') : t('ow.engineConfig.save')}
            {changedKeys.length > 0 && !saving ? ` (${changedKeys.length})` : ''}
          </button>
        </div>
      </div>

      {/* Body */}
      <div className="flex-1 min-h-0 overflow-auto p-4">
        {loadError && (
          <div className={`mb-3 flex items-center justify-between gap-3 rounded-lg border px-3 py-2 ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
            <span className="flex items-center gap-1.5 min-w-0">
              <AlertTriangle size={13} className="shrink-0" />
              <span className="truncate">{loadError}</span>
            </span>
            <button
              type="button"
              onClick={load}
              className={`shrink-0 rounded border px-2 py-0.5 text-xs font-semibold ${styles.dangerBorder}`}
            >
              {t('ow.engineConfig.retry')}
            </button>
          </div>
        )}
        {loading && (
          <div className={`rounded-lg border px-3 py-2 text-xs ${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`}>
            {t('ow.engineConfig.loading')}
          </div>
        )}
        {!loading && !loadError && rows.length === 0 && (
          <div className={`text-xs ${styles.cardTextMuted}`}>{t('ow.engineConfig.empty')}</div>
        )}
        {!loading && rows.length > 0 && (
          <div className="space-y-2 max-w-3xl">
            {rows.map((r) => {
              const key = rowKey(r);
              if (!key) return null;
              const modified = String(values[key] ?? '') !== String(originalValues[key] ?? '');
              const label = r.configLabel ?? r.config_label ?? key;
              return (
                <div
                  key={key}
                  className={`rounded-lg border px-3 py-2 ${styles.cardBorder} ${modified ? 'border-amber-400' : ''}`}
                >
                  <div className="flex items-center justify-between gap-2 mb-1">
                    <div className="min-w-0">
                      <div className={`text-xs font-semibold ${styles.cardText} truncate`}>{label}</div>
                      <div className={`text-[10px] ${styles.cardTextMuted} font-mono truncate`}>{key}</div>
                    </div>
                    {modified && (
                      <span className="shrink-0 text-[10px] font-semibold text-amber-600">●</span>
                    )}
                  </div>
                  <input
                    type="text"
                    value={values[key] ?? ''}
                    onChange={(e) => handleChange(key, e.target.value)}
                    className={`w-full rounded border px-2 py-1 text-xs ${styles.cardBorder} ${styles.inputBg ?? ''} ${styles.cardText}`}
                  />
                  {defaults[key] !== undefined && (
                    <div className={`mt-1 text-[10px] ${styles.cardTextMuted}`}>
                      default: <span className="font-mono">{defaults[key]}</span>
                    </div>
                  )}
                  {r.description && (
                    <div className={`mt-0.5 text-[10px] ${styles.cardTextMuted}`}>{r.description}</div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
