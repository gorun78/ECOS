import React, { useState, useEffect, useCallback } from 'react';
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { Settings, Save, AlertCircle, Loader2, Shield, RotateCcw, X, CheckCircle2, AlertTriangle } from 'lucide-react';
import { updateSysConfig, resetSysConfig } from '../api';
import type { SysConfigItem, SysConfigAuditItem } from '../api';
import { apiFetch } from '../services/httpClient';
import ErrorBoundary from '../components/common/ErrorBoundary';
import ConfirmDialog from '../components/common/ConfirmDialog';
import ErrorPanel from '../components/common/status/ErrorPanel';

// ── Security config keys — displayed under the security section ──
const SECURITY_KEYS = new Set([
  'password_min_length',
  'password_require_upper',
  'password_require_digit',
  'password_require_special',
  'password_expire_days',
  'password_history_count',
  'max_login_attempts',
  'lockout_duration_minutes',
  'max_concurrent_sessions',
]);

// ── Global config keys — displayed under global section ──
const GLOBAL_KEYS = new Set([
  'session_timeout_minutes',
  'audit_retention_days',
]);

// ── i18n key map for known config keys ──
const KEY_LABEL_KEYS: Record<string, string> = {
  session_timeout_minutes: 'systemConfig.key.sessionTimeout',
  audit_retention_days: 'systemConfig.key.auditRetention',
  password_min_length: 'systemConfig.key.pwdMinLength',
  password_require_upper: 'systemConfig.key.pwdRequireUpper',
  password_require_digit: 'systemConfig.key.pwdRequireDigit',
  password_require_special: 'systemConfig.key.pwdRequireSpecial',
  password_expire_days: 'systemConfig.key.pwdExpireDays',
  password_history_count: 'systemConfig.key.pwdHistoryCount',
  max_login_attempts: 'systemConfig.key.maxLoginAttempts',
  lockout_duration_minutes: 'systemConfig.key.lockoutMins',
  max_concurrent_sessions: 'systemConfig.key.maxConcurrentSessions',
};

// ── INTEGER 值域（前端地板校验；未列出的 integer 键只做整数格式校验）──
// 报告 §4.3 行167 / §4.7 行302：INTEGER 无范围校验 → 已知安全参数给出显式上下限。
const INTEGER_RANGES: Record<string, { min: number; max: number }> = {
  password_min_length: { min: 6, max: 128 },
  password_expire_days: { min: 0, max: 3650 },
  password_history_count: { min: 0, max: 24 },
  max_login_attempts: { min: 1, max: 1000 },
  lockout_duration_minutes: { min: 1, max: 14400 },
  max_concurrent_sessions: { min: 1, max: 1000 },
  session_timeout_minutes: { min: 1, max: 1440 },
  audit_retention_days: { min: 1, max: 36500 },
};

// ── Toast ──────────────────────────────────────────────────────
// 原实现用 bg-emerald-50/bg-red-50 等裸色板（报告 §三 S4），改主题语义 token。
const Toast: React.FC<{
  toast: { type: "success" | "error"; msg: string };
  onClose: () => void;
}> = ({ toast, onClose }) => {
  const { styles } = useTheme();
  const tone = toast.type === "success"
    ? `${styles.successBg} ${styles.successBorder} ${styles.successText}`
    : `${styles.dangerBg} ${styles.dangerBorder} ${styles.dangerText}`;
  return (
    <div
      role="status"
      aria-live="polite"
      className={`fixed top-6 right-6 z-50 flex items-center gap-2.5 px-4 py-3 rounded-lg shadow-lg text-sm font-medium transition-all border ${tone}`}
    >
      {toast.type === "success" ? <CheckCircle2 className="w-4 h-4 shrink-0" aria-hidden="true" /> : <AlertCircle className="w-4 h-4 shrink-0" aria-hidden="true" />}
      <span>{toast.msg}</span>
      <button type="button" onClick={onClose} aria-label="close" className="ml-2 opacity-60 hover:opacity-100"><X className="w-3.5 h-3.5" /></button>
    </div>
  );
};

/**
 * 页面级配置加载（fail-loud）。
 * api/dictConfigClient.ts 的 fetchSysConfigs/fetchConfigAudit 均 catch→返空数组
 * （报告 §三 S2「API 层 fail-open 使六态成为死代码」），页面拿不到失败信号，
 * 故障会被渲染成「无配置/无审计」。按 S2 口径本应在 transport 层单源修复
 * （已登记 .fixtmp/g2-notes.md 移交 api 层属主），此处先以页面直连 apiFetch
 * 实现「错误横幅 + 重试」，杜绝空表伪装（报告 §4.3 行160 / §4.7 行302）。
 */
async function loadSysConfigsFailLoud(): Promise<SysConfigItem[]> {
  const resp = await apiFetch<{ success: boolean; data: { data: any[] } }>('/v1/system/config');
  const items: any[] = resp?.data?.data || [];
  return items.map((it: any) => ({
    key: it.config_key || it.key,
    value: String(it.config_value ?? it.value ?? ''),
    label: it.config_label || it.label || it.config_key,
    labelZh: it.config_label || it.labelZh || it.config_key,
    description: it.description || '',
    descriptionZh: it.description || '',
    group: it.config_group || it.group || 'global',
    type: (it.config_type || it.type || 'string') as SysConfigItem['type'],
    options: it.config_options || it.options || undefined,
    impactScope: it.impact_scope || it.impactScope || '',
    edition: it.edition || 'all',
    default_value: it.default_value || '',
    isConsumed: it.is_consumed || it.isConsumed || false,
    consumedBy: it.consumed_by || it.consumedBy || '',
  }));
}

async function loadConfigAuditFailLoud(): Promise<SysConfigAuditItem[]> {
  const resp = await apiFetch<{ success: boolean; data: SysConfigAuditItem[] }>('/v1/system/config/audit');
  return resp?.data || [];
}

type PendingAction =
  | { kind: 'save'; key: string; label: string; value: string }
  | { kind: 'reset'; key: string; label: string };

export default function SystemConfigManager() {
  const { t } = useLanguage();
  const { styles } = useTheme();

  const [allConfigs, setAllConfigs] = useState<SysConfigItem[]>([]);
  const [editing, setEditing] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState<Record<string, boolean>>({});
  const [resetting, setResetting] = useState<Record<string, boolean>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  /** 列表加载失败态（报告 §4.3 行160：失败仅 console.error 空表伪装 → 显式 error + 重试） */
  const [loadError, setLoadError] = useState<string | null>(null);
  const [toast, setToast] = useState<{ type: "success" | "error"; msg: string } | null>(null);
  /** 危险操作确认门（§4.3 行167 Reset 无确认 / §4.7 行285 安全参数回车即存） */
  const [pending, setPending] = useState<PendingAction | null>(null);

  // Audit state
  const [auditOpen, setAuditOpen] = useState(false);
  const [auditData, setAuditData] = useState<SysConfigAuditItem[]>([]);
  const [auditLoading, setAuditLoading] = useState(false);
  /** 审计拉取失败与「无审计记录」严格区分（§4.3 行167） */
  const [auditError, setAuditError] = useState<string | null>(null);

  const loadConfigs = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const items = await loadSysConfigsFailLoud();
      setAllConfigs(items);
    } catch (e: any) {
      setAllConfigs([]);
      setLoadError(e?.message || String(e));
    }
    setLoading(false);
  }, []);

  useEffect(() => { loadConfigs(); }, [loadConfigs]);

  // Auto-clear toast after 3s
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), 3000);
    return () => clearTimeout(timer);
  }, [toast]);

  // Categorize configs — 白名单之外的键不再静默隐藏（报告 §4.3 行160），
  // 全量归入「其它配置」区渲染。
  const globalConfigs = allConfigs.filter(c => GLOBAL_KEYS.has(c.key));
  const securityConfigs = allConfigs.filter(c => SECURITY_KEYS.has(c.key));
  const otherConfigs = allConfigs.filter(c => !GLOBAL_KEYS.has(c.key) && !SECURITY_KEYS.has(c.key));

  // ── Determine type for input rendering ──
  function resolveInputType(cfg: SysConfigItem): 'INTEGER' | 'BOOLEAN' | 'STRING' {
    const type = (cfg.type || 'string').toLowerCase();
    if (type === 'integer' || type === 'int' || type === 'number') return 'INTEGER';
    if (type === 'boolean' || type === 'bool') return 'BOOLEAN';
    return 'STRING';
  }

  // ── 值校验：INTEGER 必须为整数；已知键按前端地板范围校验（§4.3 行167）──
  function validateValue(cfg: SysConfigItem, val: string): string | null {
    if (resolveInputType(cfg) !== 'INTEGER' || val === '') return null;
    if (!/^-?\d+$/.test(val.trim())) {
      return t('systemConfig.validation.integer', { label: configLabel(cfg) });
    }
    const num = Number(val.trim());
    const range = INTEGER_RANGES[cfg.key];
    if (range && (num < range.min || num > range.max)) {
      return t('systemConfig.validation.range', { label: configLabel(cfg), min: range.min, max: range.max });
    }
    return null;
  }

  function configLabel(cfg: SysConfigItem): string {
    const labelKey = KEY_LABEL_KEYS[cfg.key];
    return labelKey ? (t(labelKey) || cfg.label || cfg.key) : (cfg.label || cfg.key);
  }

  // ── 实际保存（确认门后 / 非安全键直接执行）──
  const doSave = async (key: string, val: string) => {
    setSaving(prev => ({ ...prev, [key]: true }));
    try {
      await updateSysConfig(key, val);
      setAllConfigs(prev => prev.map(item =>
        item.key === key ? { ...item, value: val } : item
      ));
      setErrors(prev => { const n = { ...prev }; delete n[key]; return n; });
      setToast({ type: 'success', msg: t('systemConfig.updated', { key }) });
    } catch (e: any) {
      const msg = e?.message || String(e);
      setErrors(prev => ({ ...prev, [key]: msg }));
      setToast({ type: 'error', msg: `${t('systemConfig.saveFailed')}: ${msg}` });
    }
    setSaving(prev => { const n = { ...prev }; delete n[key]; return n; });
  };

  const doReset = async (key: string) => {
    setResetting(prev => ({ ...prev, [key]: true }));
    try {
      const updated = await resetSysConfig(key);
      setAllConfigs(prev => prev.map(item =>
        item.key === key ? { ...item, value: updated.value ?? item.default_value ?? '' } : item
      ));
      setToast({ type: 'success', msg: t('systemConfig.resetDone', { key }) });
    } catch (e: any) {
      const msg = e?.message || String(e);
      setErrors(prev => ({ ...prev, [key]: msg }));
      setToast({ type: 'error', msg: `${t('systemConfig.resetFailed')}: ${msg}` });
    }
    setResetting(prev => { const n = { ...prev }; delete n[key]; return n; });
  };

  /**
   * 发起保存：安全参数（密码/锁定等）一律先过确认门（报告 §4.7 行285 P0），
   * 其余键由显式保存按钮直接提交（已去掉失焦即存）。
   */
  const requestSave = (cfg: SysConfigItem) => {
    const val = editing[cfg.key] ?? '';
    const validationError = validateValue(cfg, val);
    if (validationError) {
      setErrors(prev => ({ ...prev, [cfg.key]: validationError }));
      return;
    }
    if (SECURITY_KEYS.has(cfg.key)) {
      setPending({ kind: 'save', key: cfg.key, label: configLabel(cfg), value: val });
      return;
    }
    void doSave(cfg.key, val);
  };

  /** 发起重置：所有键都过确认门（报告 §4.3 行167：Reset 一键即生效无确认） */
  const requestReset = (cfg: SysConfigItem) => {
    setPending({ kind: 'reset', key: cfg.key, label: configLabel(cfg) });
  };

  const confirmPending = async () => {
    if (!pending) return;
    const action = pending;
    setPending(null);
    if (action.kind === 'save') {
      setErrors(prev => { const n = { ...prev }; delete n[action.key]; return n; });
      await doSave(action.key, action.value);
    } else {
      await doReset(action.key);
    }
  };

  const handleAudit = async () => {
    setAuditOpen(true);
    setAuditLoading(true);
    setAuditError(null);
    try {
      const data = await loadConfigAuditFailLoud();
      setAuditData(data);
    } catch (e: any) {
      setAuditData([]);
      setAuditError(e?.message || String(e));
    }
    setAuditLoading(false);
  };

  // ── Render a config row ──
  function renderConfigRow(cfg: SysConfigItem) {
    const curVal = editing[cfg.key] !== undefined ? editing[cfg.key] : cfg.value;
    const inputType = resolveInputType(cfg);
    const isSaving = saving[cfg.key];
    const isResetting = resetting[cfg.key];
    const error = errors[cfg.key];
    const label = configLabel(cfg);
    const desc = cfg.description;
    const defaultVal = cfg.default_value || '—';
    const modifiedAt = (cfg as { modifiedAt?: string }).modifiedAt
      || (cfg as { modified_at?: string }).modified_at
      || (cfg as { updatedAt?: string }).updatedAt || '—';

    return (
      <tr key={cfg.key} className={`border-b ${styles.sidebarBorder} ${styles.text} ${styles.sidebarHoverBg} transition-colors`}>
        {/* Key */}
        <td className="py-3 px-3">
          <code className={`text-xs px-1.5 py-0.5 rounded ${styles.sidebarBg} ${styles.muted}`}>{cfg.key}</code>
        </td>
        {/* Description */}
        <td className="py-3 px-3">
          <div className="text-sm font-medium">{label}</div>
          {desc && <div className={`text-xs ${styles.muted} mt-0.5`}>{desc}</div>}
        </td>
        {/* Current value (editable) — 保存只经显式按钮/回车，不再失焦即存（§4.7 行285） */}
        <td className="py-3 px-3">
          <div className="flex items-center gap-1.5">
            {inputType === 'BOOLEAN' ? (
              <select
                aria-label={label}
                value={curVal}
                onChange={e => setEditing(prev => ({ ...prev, [cfg.key]: e.target.value }))}
                className={`px-2 py-1.5 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              >
                <option value="true">true</option>
                <option value="false">false</option>
              </select>
            ) : (
              <input
                aria-label={label}
                type={inputType === 'INTEGER' ? 'number' : 'text'}
                value={curVal}
                onChange={e => {
                  const v = e.target.value;
                  setEditing(prev => ({ ...prev, [cfg.key]: v }));
                  const err = validateValue({ ...cfg, value: v }, v);
                  setErrors(prev => (err ? { ...prev, [cfg.key]: err } : (() => { const n = { ...prev }; delete n[cfg.key]; return n; })()));
                }}
                onKeyDown={e => { if (e.key === 'Enter') requestSave(cfg); }}
                className={`px-2 py-1.5 rounded text-sm w-40 border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              />
            )}
            <button type="button"
              onClick={() => requestSave(cfg)}
              disabled={isSaving || !!error}
              title={t('systemConfig.action.save')}
              className={`p-1.5 rounded transition-colors disabled:opacity-50 ${styles.accentBg} ${styles.accentSolidText}`}
            >
              {isSaving ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
            </button>
          </div>
          {error && (
            <div className={`flex items-center gap-1 mt-1 text-xs ${styles.dangerText}`}>
              <AlertTriangle size={12} aria-hidden="true" />
              <span>{error}</span>
            </div>
          )}
        </td>
        {/* Default value */}
        <td className={`py-3 px-3 text-sm ${styles.muted} font-mono`}>{defaultVal}</td>
        {/* Modified time */}
        <td className={`py-3 px-3 text-xs ${styles.muted}`}>{modifiedAt}</td>
        {/* Reset to default */}
        <td className="py-3 px-3">
          <button type="button"
            onClick={() => requestReset(cfg)}
            disabled={isResetting}
            className={`flex items-center gap-1 px-2 py-1 rounded text-xs border transition-colors disabled:opacity-50 ${styles.warningBg} ${styles.warningText} ${styles.warningBorder} hover:opacity-80`}
            title={t('systemConfig.action.reset')}
          >
            {isResetting ? <Loader2 size={12} className="animate-spin" /> : <RotateCcw size={12} />}
            <span>{t('systemConfig.action.reset')}</span>
          </button>
        </td>
      </tr>
    );
  }

  // ── Render a config section ──
  function renderSection(title: string, icon: React.ReactNode, configs: SysConfigItem[], bgClass: string) {
    return (
      <div className={bgClass}>
        <div className="px-4 pt-4 pb-1">
          <h3 className={`text-sm font-bold flex items-center gap-2 ${styles.text}`}>
            {icon}
            {title}
            <span className={`text-xs font-normal ${styles.muted}`}>· {configs.length}</span>
          </h3>
          <hr className={`mt-3 mb-2 ${styles.sidebarBorder}`} />
        </div>
        {configs.length === 0 ? (
          <p className={`text-center py-4 text-xs ${styles.muted}`}>
            {t('systemConfig.noConfigs')}
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className={`border-b ${styles.sidebarBorder} ${styles.muted} text-xs uppercase`}>
                  <th className="py-2 px-3 text-left font-semibold">{t('systemConfig.col.key')}</th>
                  <th className="py-2 px-3 text-left font-semibold">{t('systemConfig.col.desc')}</th>
                  <th className="py-2 px-3 text-left font-semibold">{t('systemConfig.col.current')}</th>
                  <th className="py-2 px-3 text-left font-semibold">{t('systemConfig.col.default')}</th>
                  <th className="py-2 px-3 text-left font-semibold">{t('systemConfig.col.modified')}</th>
                  <th className="py-2 px-3 text-left font-semibold">{t('systemConfig.action.reset')}</th>
                </tr>
              </thead>
              <tbody>
                {configs.map(cfg => renderConfigRow(cfg))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    );
  }

  return (
    <ErrorBoundary>
      <div className={`flex flex-col h-full ${styles.cardBg}`}>
        {/* Header */}
        <div className={`flex items-center justify-between px-4 py-3 border-b ${styles.sidebarBorder}`}>
          <div className="flex items-center gap-2">
            <Settings size={18} className={styles.muted} />
            <h2 className={`text-lg font-semibold ${styles.text}`}>
              {t('systemConfig.title')}
            </h2>
          </div>
          <button type="button" onClick={handleAudit}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded text-sm transition-colors ${styles.accentBg} ${styles.accentSolidText}`}
          >
            <Shield size={14} />
            {t('systemConfig.audit')}
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-auto">
          {loading ? (
            <div className="flex items-center gap-2 p-8">
              <Loader2 size={24} className={`animate-spin ${styles.accentText}`} />
              <span className={styles.muted}>{t('common.loading')}</span>
            </div>
          ) : loadError ? (
            /* 错误横幅 + 重试（报告 §4.3 行160：失败不再伪装空表） */
            <div className="p-4">
              <ErrorPanel
                reason={`${t('systemConfig.loadFailed')}: ${loadError}`}
                onRetry={() => void loadConfigs()}
              />
            </div>
          ) : (
            <div>
              {/* Section 1: Global configs */}
              {renderSection(
                t('systemConfig.section.global'),
                <Settings size={15} className={styles.muted} />,
                globalConfigs,
                ''
              )}

              {/* Separator between sections */}
              <div className={`h-2 ${styles.cardBorder}`} />

              {/* Section 2: Security configs */}
              {renderSection(
                t('systemConfig.section.security'),
                <Shield size={15} className={styles.warningText} />,
                securityConfigs,
                styles.appBg
              )}

              <div className={`h-2 ${styles.cardBorder}`} />

              {/* Section 3: 其余全部配置（白名单外不再静默隐藏，报告 §4.3 行160） */}
              {renderSection(
                t('systemConfig.section.other'),
                <Settings size={15} className={styles.muted} />,
                otherConfigs,
                ''
              )}
            </div>
          )}
        </div>

        {/* Audit modal */}
        {auditOpen && (
          <div className={`fixed inset-0 z-50 flex items-center justify-center ${styles.overlayBg}`} onClick={() => setAuditOpen(false)}>
            <div className={`${styles.cardBg} border ${styles.sidebarBorder} rounded-xl w-[700px] max-w-[92vw] max-h-[80vh] flex flex-col shadow-2xl`} onClick={e => e.stopPropagation()}>
              <div className={`flex items-center justify-between p-4 border-b ${styles.sidebarBorder}`}>
                <div className="flex items-center gap-2">
                  <Shield size={18} className={styles.accentText} />
                  <h3 className={`font-semibold ${styles.text}`}>{t('systemConfig.paraAudit.title')}</h3>
                </div>
                <button type="button" onClick={() => setAuditOpen(false)} className={`p-1 rounded ${styles.muted} ${styles.sidebarHoverBg}`}>
                  <X size={18} />
                </button>
              </div>
              <div className="overflow-auto p-4 flex-1">
                {auditLoading ? (
                  <div className="flex items-center gap-2 p-8"><Loader2 size={24} className={`animate-spin ${styles.accentText}`} /><span className={styles.muted}>{t('common.loading')}</span></div>
                ) : auditError ? (
                  /* 拉取失败 ≠ 无审计记录（报告 §4.3 行167） */
                  <ErrorPanel
                    reason={`${t('systemConfig.audit.failed')}: ${auditError}`}
                    onRetry={() => void handleAudit()}
                  />
                ) : auditData.length === 0 ? (
                  <p className={`text-center py-8 ${styles.muted}`}>{t('systemConfig.paraAudit.empty')}</p>
                ) : (
                  <table className="w-full text-sm">
                    <thead>
                      <tr className={`border-b ${styles.sidebarBorder} ${styles.muted} text-left text-xs uppercase`}>
                        <th className="py-2 px-3">{t('systemConfig.paraAudit.col.key')}</th>
                        <th className="py-2 px-3">{t('systemConfig.paraAudit.col.label')}</th>
                        <th className="py-2 px-3">{t('systemConfig.paraAudit.col.value')}</th>
                        <th className="py-2 px-3">{t('systemConfig.paraAudit.col.consumed')}</th>
                        <th className="py-2 px-3">{t('systemConfig.paraAudit.col.by')}</th>
                        <th className="py-2 px-3">{t('systemConfig.paraAudit.col.at')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {auditData.map((item, idx) => (
                        <tr key={idx} className={`border-b ${styles.sidebarBorder} ${styles.text}`}>
                          <td className="py-2 px-3 font-mono text-xs">{item.key}</td>
                          <td className="py-2 px-3">{item.label}</td>
                          <td className="py-2 px-3 font-mono text-xs max-w-[120px] truncate">{item.value}</td>
                          <td className="py-2 px-3">
                            <span className={`px-1.5 py-0.5 rounded text-xs ${item.consumed ? `${styles.warningBg} ${styles.warningText}` : `${styles.successBg} ${styles.successText}`}`}>
                              {item.consumed ? t('systemConfig.paraAudit.yes') : t('systemConfig.paraAudit.no')}
                            </span>
                          </td>
                          <td className="py-2 px-3 text-xs">{item.consumedBy || '—'}</td>
                          <td className="py-2 px-3 text-xs">{item.consumedAt || '—'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </div>
          </div>
        )}

        {/* 危险操作确认门（保存安全参数 / 重置任意参数） */}
        <ConfirmDialog
          open={!!pending}
          variant="warning"
          title={pending?.kind === 'reset' ? t('systemConfig.confirm.resetTitle') : t('systemConfig.confirm.saveTitle')}
          message={pending?.kind === 'reset'
            ? t('systemConfig.confirm.resetMsg', { label: pending.label })
            : pending
              ? t('systemConfig.confirm.saveMsg', { label: pending.label, value: pending.value })
              : ''}
          confirmText={t('common.confirm')}
          cancelText={t('common.cancel')}
          onConfirm={() => void confirmPending()}
          onCancel={() => setPending(null)}
        />

        {/* Toast */}
        {toast && <Toast toast={toast} onClose={() => setToast(null)} />}
      </div>
    </ErrorBoundary>
  );
}
