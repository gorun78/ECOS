/**
 * TenantManager 租户表单弹窗 — 从 pages/TenantManager.tsx 结构拆分而来（JSX/逻辑逐字保留）
 * @license Apache-2.0
 */

import { useState } from "react";
import { Check, X } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";
import { ISOLATION_MODE_OPTIONS } from "./constants";
import type { Tenant } from "./types";

// ── Tenant Form Modal ─────────────────────────────────────────

export interface TenantFormModalProps {
  mode: "create" | "edit";
  tenant?: Tenant;
  onSave: (data: { tenantName: string; tenantCode?: string; status?: string; maxUsers?: number; maxStorageMb?: number; maxApiPerDay?: number; isolationMode?: string }) => Promise<void>;
  onClose: () => void;
}

export function TenantFormModal({ mode, tenant, onSave, onClose }: TenantFormModalProps) {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const [tenantName, setTenantName] = useState(tenant?.tenantName ?? "");
  const [tenantCode, setTenantCode] = useState(tenant?.tenantCode ?? "");
  const [status, setStatus] = useState(tenant?.status ?? "ACTIVE");
  const [maxUsers, setMaxUsers] = useState(String(tenant?.maxUsers ?? 100));
  const [maxStorageMb, setMaxStorageMb] = useState(String(tenant?.maxStorageMb ?? 1024));
  const [maxApiPerDay, setMaxApiPerDay] = useState(String(tenant?.maxApiPerDay ?? 10000));
  const [isolationMode, setIsolationMode] = useState(tenant?.isolationMode ?? "ROW_FILTER");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const handleSave = async () => {
    if (!tenantName.trim()) {
      setError(locale === "zh" ? "租户名称不能为空" : "Tenant name is required");
      return;
    }
    setSaving(true);
    setError("");
    try {
      await onSave({
        tenantName: tenantName.trim(),
        tenantCode: tenantCode.trim() || undefined,
        status,
        maxUsers: parseInt(maxUsers, 10) || 0,
        maxStorageMb: parseInt(maxStorageMb, 10) || 0,
        maxApiPerDay: parseInt(maxApiPerDay, 10) || 0,
        isolationMode,
      });
      onClose();
    } catch (e: any) {
      setError(e.message || "Save failed");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className={`rounded-lg border p-6 w-full max-w-lg ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center justify-between mb-4">
          <h3 className={`text-sm font-semibold ${styles.cardText}`}>
            {mode === "create"
              ? locale === "zh" ? "新建租户" : "Create Tenant"
              : locale === "zh" ? "编辑租户" : "Edit Tenant"}
          </h3>
          <button onClick={onClose} className="opacity-60 hover:opacity-100"><X className="w-4 h-4" /></button>
        </div>

        {error && (
          <div className="mb-3 p-2 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-xs">{error}</div>
        )}

        <div className="space-y-3 max-h-[60vh] overflow-y-auto">
          <div>
            <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
              {locale === "zh" ? "租户名称 *" : "Tenant Name *"}
            </label>
            <input
              value={tenantName}
              onChange={(e) => setTenantName(e.target.value)}
              className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              placeholder={locale === "zh" ? "例如：Acme Corp" : "e.g. Acme Corp"}
            />
          </div>
          <div>
            <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
              {locale === "zh" ? "租户编码" : "Tenant Code"}
            </label>
            <input
              value={tenantCode}
              onChange={(e) => setTenantCode(e.target.value)}
              className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              disabled={mode === "edit"}
              placeholder={locale === "zh" ? "例如：acme" : "e.g. acme"}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                {locale === "zh" ? "状态" : "Status"}
              </label>
              <select
                value={status}
                onChange={(e) => setStatus(e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              >
                <option value="ACTIVE">{locale === "zh" ? "活跃" : "Active"}</option>
                <option value="SUSPENDED">{locale === "zh" ? "已暂停" : "Suspended"}</option>
                <option value="DELETED">{locale === "zh" ? "已删除" : "Deleted"}</option>
              </select>
            </div>
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                {locale === "zh" ? "隔离模式" : "Isolation Mode"}
              </label>
              <select
                value={isolationMode}
                onChange={(e) => setIsolationMode(e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              >
                {ISOLATION_MODE_OPTIONS.map((m) => (
                  <option key={m} value={m}>{m}</option>
                ))}
              </select>
            </div>
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                {locale === "zh" ? "最大用户数" : "Max Users"}
              </label>
              <input
                type="number"
                min={0}
                value={maxUsers}
                onChange={(e) => setMaxUsers(e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              />
            </div>
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                {locale === "zh" ? "最大存储(MB)" : "Max Storage(MB)"}
              </label>
              <input
                type="number"
                min={0}
                value={maxStorageMb}
                onChange={(e) => setMaxStorageMb(e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              />
            </div>
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                {locale === "zh" ? "日API限额" : "API/Day"}
              </label>
              <input
                type="number"
                min={0}
                value={maxApiPerDay}
                onChange={(e) => setMaxApiPerDay(e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              />
            </div>
          </div>
        </div>

        <div className="flex justify-end gap-2 mt-4">
          <button onClick={onClose} className={`px-4 py-2 rounded text-xs border ${styles.cardBorder} ${styles.cardText}`}>
            {locale === "zh" ? "取消" : "Cancel"}
          </button>
          <button
            onClick={handleSave}
            disabled={saving}
            className={`px-4 py-2 rounded text-xs font-medium text-white flex items-center gap-1.5 ${styles.accentBg} ${styles.accentHover}`}
          >
            <Check className="w-3.5 h-3.5" />
            {saving ? (locale === "zh" ? "保存中…" : "Saving…") : (locale === "zh" ? "保存" : "Save")}
          </button>
        </div>
      </div>
    </div>
  );
}

export default TenantFormModal;
