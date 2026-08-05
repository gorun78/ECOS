import React, { useState, useEffect, useCallback } from 'react';
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import { Settings, Save, Check, AlertCircle, Loader2, Shield, RotateCcw, X, CheckCircle2, Info } from 'lucide-react';
import { fetchSysConfigs, updateSysConfig, resetSysConfig, fetchConfigAudit } from '../api';
import type { SysConfigItem, SysConfigAuditItem } from '../api';
import ErrorBoundary from '../components/common/ErrorBoundary';

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

// ── Labels for known config keys ──
const KEY_LABELS: Record<string, { zh: string; en: string }> = {
  session_timeout_minutes: { zh: '会话超时（分钟）', en: 'Session Timeout (min)' },
  audit_retention_days: { zh: '审计保留天数', en: 'Audit Retention (days)' },
  password_min_length: { zh: '密码最小长度', en: 'Password Min Length' },
  password_require_upper: { zh: '密码需含大写', en: 'Password Require Upper' },
  password_require_digit: { zh: '密码需含数字', en: 'Password Require Digit' },
  password_require_special: { zh: '密码需含特殊字符', en: 'Password Require Special' },
  password_expire_days: { zh: '密码过期天数', en: 'Password Expire Days' },
  password_history_count: { zh: '密码历史数量', en: 'Password History Count' },
  max_login_attempts: { zh: '最大登录尝试次数', en: 'Max Login Attempts' },
  lockout_duration_minutes: { zh: '锁定持续时间（分钟）', en: 'Lockout Duration (min)' },
  max_concurrent_sessions: { zh: '最大并发会话数', en: 'Max Concurrent Sessions' },
};

// ── Toast ──────────────────────────────────────────────────────
const Toast: React.FC<{
  toast: { type: "success" | "error"; msg: string };
  onClose: () => void;
}> = ({ toast, onClose }) => (
  <div
    className={`fixed top-6 right-6 z-50 flex items-center gap-2.5 px-4 py-3 rounded-lg shadow-lg text-sm font-medium transition-all
      ${toast.type === "success"
        ? "bg-emerald-50 dark:bg-emerald-950 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-200"
        : "bg-red-50 dark:bg-red-950 border border-red-200 dark:border-red-800 text-red-800 dark:text-red-200"
      }`}
  >
    {toast.type === "success" ? <CheckCircle2 className="w-4 h-4 shrink-0" /> : <AlertCircle className="w-4 h-4 shrink-0" />}
    <span>{toast.msg}</span>
    <button onClick={onClose} className="ml-2 opacity-60 hover:opacity-100"><X className="w-3.5 h-3.5" /></button>
  </div>
);

export default function SystemConfigManager() {
  const { locale } = useLanguage() as any;
  const { styles } = useTheme() as any;

  const isZh = locale !== 'en';

  const [allConfigs, setAllConfigs] = useState<SysConfigItem[]>([]);
  const [editing, setEditing] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState<Record<string, boolean>>({});
  const [resetting, setResetting] = useState<Record<string, boolean>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [toast, setToast] = useState<{ type: "success" | "error"; msg: string } | null>(null);

  // Audit state
  const [auditOpen, setAuditOpen] = useState(false);
  const [auditData, setAuditData] = useState<SysConfigAuditItem[]>([]);
  const [auditLoading, setAuditLoading] = useState(false);

  const loadConfigs = useCallback(async () => {
    setLoading(true);
    try {
      const grouped = await fetchSysConfigs();
      // Flatten all items from all groups
      const items: SysConfigItem[] = [];
      for (const g of grouped) {
        items.push(...g.items);
      }
      setAllConfigs(items);
    } catch (e) { console.error(e); }
    setLoading(false);
  }, []);

  useEffect(() => { loadConfigs(); }, [loadConfigs]);

  // Auto-clear toast after 3s
  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(null), 3000);
    return () => clearTimeout(t);
  }, [toast]);

  // Categorize configs
  const globalConfigs = allConfigs.filter(c => GLOBAL_KEYS.has(c.key));
  const securityConfigs = allConfigs.filter(c => SECURITY_KEYS.has(c.key));

  // ── Determine type for input rendering ──
  function resolveInputType(cfg: SysConfigItem): 'INTEGER' | 'BOOLEAN' | 'STRING' {
    const t = (cfg.type || 'string').toLowerCase();
    if (t === 'integer' || t === 'int' || t === 'number') return 'INTEGER';
    if (t === 'boolean' || t === 'bool') return 'BOOLEAN';
    return 'STRING';
  }

  // ── Handlers ──
  const handleSave = async (key: string) => {
    const val = editing[key] ?? '';
    setSaving(prev => ({ ...prev, [key]: true }));
    try {
      await updateSysConfig(key, val);
      // Update local state
      setAllConfigs(prev => prev.map(item =>
        item.key === key ? { ...item, value: val } : item
      ));
      setToast({ type: 'success', msg: `${isZh ? '配置' : 'Config'} ${key} ${isZh ? '已更新' : 'updated'}` });
    } catch (e: any) {
      setErrors(prev => ({ ...prev, [key]: e.message }));
      setToast({ type: 'error', msg: `${isZh ? '保存失败' : 'Save failed'}: ${e.message}` });
    }
    setSaving(prev => { const n = { ...prev }; delete n[key]; return n; });
  };

  const handleReset = async (key: string) => {
    setResetting(prev => ({ ...prev, [key]: true }));
    try {
      const updated = await resetSysConfig(key);
      // Update local state with returned value
      setAllConfigs(prev => prev.map(item =>
        item.key === key ? { ...item, value: updated.value ?? item.default_value ?? '' } : item
      ));
      setToast({ type: 'success', msg: `${isZh ? '配置' : 'Config'} ${key} ${isZh ? '已恢复默认值' : 'reset to default'}` });
    } catch (e: any) {
      setErrors(prev => ({ ...prev, [key]: e.message }));
      setToast({ type: 'error', msg: `${isZh ? '恢复失败' : 'Reset failed'}: ${e.message}` });
    }
    setResetting(prev => { const n = { ...prev }; delete n[key]; return n; });
  };

  const handleKeyDown = (key: string) => (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') handleSave(key);
  };

  const handleAudit = async () => {
    setAuditLoading(true);
    setAuditOpen(true);
    try {
      const data = await fetchConfigAudit();
      setAuditData(data);
    } catch (e) {
      console.error('Audit fetch failed', e);
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
    const label = isZh ? (KEY_LABELS[cfg.key]?.zh || cfg.labelZh || cfg.label) : (KEY_LABELS[cfg.key]?.en || cfg.label);
    const desc = isZh && cfg.descriptionZh ? cfg.descriptionZh : cfg.description;
    const defaultVal = cfg.default_value || '—';
    const modifiedAt = (cfg as any).modifiedAt || (cfg as any).modified_at || (cfg as any).updatedAt || '—';

    return (
      <tr key={cfg.key} className={`border-b ${styles.sidebarBorder} ${styles.text} hover:bg-gray-50/50 dark:hover:bg-white/[0.03] transition-colors`}>
        {/* Key */}
        <td className="py-3 px-3">
          <code className="text-xs px-1.5 py-0.5 rounded bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 font-mono">{cfg.key}</code>
        </td>
        {/* 描述 */}
        <td className="py-3 px-3">
          <div className="text-sm font-medium">{label}</div>
          {desc && <div className={`text-xs ${styles.muted} mt-0.5`}>{desc}</div>}
        </td>
        {/* 当前值（可编辑） */}
        <td className="py-3 px-3">
          {inputType === 'BOOLEAN' ? (
            <select
              value={curVal}
              onChange={e => setEditing(prev => ({ ...prev, [cfg.key]: e.target.value }))}
              onBlur={() => { if (editing[cfg.key] !== undefined) handleSave(cfg.key); }}
              className="px-2 py-1.5 rounded text-sm border"
              style={{ background: '#0f172a', color: '#e2e8f0', borderColor: '#334155' }}
            >
              <option value="true">{isZh ? '是' : 'true'}</option>
              <option value="false">{isZh ? '否' : 'false'}</option>
            </select>
          ) : (
            <div className="flex items-center gap-1.5">
              <input
                type={inputType === 'INTEGER' ? 'number' : 'text'}
                value={curVal}
                onChange={e => setEditing(prev => ({ ...prev, [cfg.key]: e.target.value }))}
                onKeyDown={handleKeyDown(cfg.key)}
                className="px-2 py-1.5 rounded text-sm w-40"
                style={{ background: '#0f172a', color: '#e2e8f0', border: '1px solid #334155' }}
              />
              <button
                onClick={() => handleSave(cfg.key)}
                disabled={isSaving}
                className="p-1.5 rounded bg-indigo-600 hover:bg-indigo-700 text-white transition-colors disabled:opacity-50"
                title={isZh ? '保存' : 'Save'}
              >
                {isSaving ? <Loader2 size={14} className="animate-spin" /> : <Save size={14} />}
              </button>
            </div>
          )}
        </td>
        {/* 默认值 */}
        <td className={`py-3 px-3 text-sm ${styles.muted} font-mono`}>{defaultVal}</td>
        {/* 修改时间 */}
        <td className={`py-3 px-3 text-xs ${styles.muted}`}>{modifiedAt}</td>
        {/* 恢复默认 */}
        <td className="py-3 px-3">
          <button
            onClick={() => handleReset(cfg.key)}
            disabled={isResetting}
            className="flex items-center gap-1 px-2 py-1 rounded text-xs border transition-colors hover:bg-amber-50 dark:hover:bg-amber-900/20 disabled:opacity-50"
            style={{ borderColor: '#d97706', color: '#d97706' }}
            title={isZh ? '恢复默认值' : 'Reset to default'}
          >
            {isResetting ? <Loader2 size={12} className="animate-spin" /> : <RotateCcw size={12} />}
            <span>{isZh ? '恢复' : 'Reset'}</span>
          </button>
        </td>
      </tr>
    );
  }

  // ── Render a config section ──
  function renderSection(title: string, icon: React.ReactNode, configs: SysConfigItem[], bgClass: string) {
    if (configs.length === 0) {
      return (
        <div className={bgClass}>
          <div className="px-4 pt-4 pb-1">
            <h3 className={`text-sm font-bold flex items-center gap-2 ${styles.text}`}>
              {icon}
              {title}
            </h3>
            <hr className={`mt-3 mb-2 ${styles.sidebarBorder}`} />
          </div>
          <p className={`text-center py-4 text-xs ${styles.muted}`}>
            {isZh ? '暂无配置项' : 'No configs available'}
          </p>
        </div>
      );
    }

    return (
      <div className={bgClass}>
        <div className="px-4 pt-4 pb-1">
          <h3 className={`text-sm font-bold flex items-center gap-2 ${styles.text}`}>
            {icon}
            {title}
          </h3>
          <hr className={`mt-3 mb-2 ${styles.sidebarBorder}`} />
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className={`border-b ${styles.sidebarBorder} ${styles.muted} text-xs uppercase`}>
                <th className="py-2 px-3 text-left font-semibold">{isZh ? '配置键' : 'Key'}</th>
                <th className="py-2 px-3 text-left font-semibold">{isZh ? '描述' : 'Description'}</th>
                <th className="py-2 px-3 text-left font-semibold">{isZh ? '当前值' : 'Current Value'}</th>
                <th className="py-2 px-3 text-left font-semibold">{isZh ? '默认值' : 'Default'}</th>
                <th className="py-2 px-3 text-left font-semibold">{isZh ? '修改时间' : 'Modified'}</th>
                <th className="py-2 px-3 text-left font-semibold">{isZh ? '恢复默认' : 'Reset'}</th>
              </tr>
            </thead>
            <tbody>
              {configs.map(cfg => renderConfigRow(cfg))}
            </tbody>
          </table>
        </div>
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
              {isZh ? '系统配置管理' : 'System Config Manager'}
            </h2>
          </div>
          <button onClick={handleAudit}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded bg-indigo-600 hover:bg-indigo-700 text-white text-sm transition-colors">
            <Shield size={14} />
            {isZh ? '参数审计' : 'Audit'}
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-auto">
          {loading ? (
            <div className="flex items-center gap-2 p-8">
              <Loader2 size={24} className="animate-spin text-indigo-400" />
              <span className={styles.muted}>{isZh ? '加载中...' : 'Loading...'}</span>
            </div>
          ) : (
            <div>
              {/* Section 1: Global configs */}
              {renderSection(
                isZh ? '全局通用配置' : 'Global Configuration',
                <Settings size={15} className="text-indigo-400" />,
                globalConfigs,
                ''
              )}

              {/* Grey separator between sections */}
              <div className="h-2 bg-gray-100 dark:bg-gray-800" />

              {/* Section 2: Security configs — grey background */}
              {renderSection(
                isZh ? '安全配置' : 'Security Configuration',
                <Shield size={15} className="text-amber-400" />,
                securityConfigs,
                'bg-slate-50 dark:bg-slate-900'
              )}
            </div>
          )}
        </div>

        {/* Audit modal */}
        {auditOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60" onClick={() => setAuditOpen(false)}>
            <div className={`${styles.cardBg} border ${styles.sidebarBorder} rounded-xl w-[700px] max-h-[80vh] flex flex-col shadow-2xl`} onClick={e => e.stopPropagation()}>
              <div className={`flex items-center justify-between p-4 border-b ${styles.sidebarBorder}`}>
                <div className="flex items-center gap-2">
                  <Shield size={18} className="text-indigo-400" />
                  <h3 className={`font-semibold ${styles.text}`}>{isZh ? '参数消耗审计' : 'Config Consumption Audit'}</h3>
                </div>
                <button onClick={() => setAuditOpen(false)} className={`p-1 rounded hover:bg-gray-600/30 ${styles.muted}`}>
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M18 6L6 18M6 6l12 12" /></svg>
                </button>
              </div>
              <div className="overflow-auto p-4 flex-1">
                {auditLoading ? (
                  <div className="flex items-center gap-2 p-8"><Loader2 size={24} className="animate-spin text-indigo-400" /><span className={styles.muted}>{isZh ? '加载中...' : 'Loading...'}</span></div>
                ) : auditData.length === 0 ? (
                  <p className={`text-center py-8 ${styles.muted}`}>{isZh ? '暂无审计数据' : 'No audit data available'}</p>
                ) : (
                  <table className="w-full text-sm">
                    <thead>
                      <tr className={`border-b ${styles.sidebarBorder} ${styles.muted} text-left text-xs uppercase`}>
                        <th className="py-2 px-3">{isZh ? '参数键' : 'Key'}</th>
                        <th className="py-2 px-3">{isZh ? '标签' : 'Label'}</th>
                        <th className="py-2 px-3">{isZh ? '当前值' : 'Value'}</th>
                        <th className="py-2 px-3">{isZh ? '消耗状态' : 'Consumed'}</th>
                        <th className="py-2 px-3">{isZh ? '消耗者' : 'Consumed By'}</th>
                        <th className="py-2 px-3">{isZh ? '消耗时间' : 'Consumed At'}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {auditData.map((item, idx) => (
                        <tr key={idx} className={`border-b ${styles.sidebarBorder} ${styles.text}`}>
                          <td className="py-2 px-3 font-mono text-xs">{item.key}</td>
                          <td className="py-2 px-3">{item.label}</td>
                          <td className="py-2 px-3 font-mono text-xs max-w-[120px] truncate">{item.value}</td>
                          <td className="py-2 px-3">
                            <span className={`px-1.5 py-0.5 rounded text-xs ${item.consumed ? 'bg-amber-700 text-amber-200' : 'bg-emerald-700 text-emerald-200'}`}>
                              {item.consumed ? (isZh ? '已消耗' : 'Yes') : (isZh ? '未消耗' : 'No')}
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

        {/* Toast */}
        {toast && <Toast toast={toast} onClose={() => setToast(null)} />}
      </div>
    </ErrorBoundary>
  );
}
