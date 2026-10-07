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
  const { t } = useLanguage();
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
      setError(t("platform.tenant.form.nameRequired"));
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
              ? t("platform.tenant.form.create")
              : t("platform.tenant.form.edit")}
          </h3>
          <button onClick={onClose} className="opacity-60 hover:opacity-100"><X className="w-4 h-4" /></button>
        </div>

        {error && (
          <div className="mb-3 p-2 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-xs">{error}</div>
        )}

        <div className="space-y-3 max-h-[60vh] overflow-y-auto">
          <div>
            <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
              {t("platform.tenant.form.nameLabel")}
            </label>
            <input
              value={tenantName}
              onChange={(e) => setTenantName(e.target.value)}
              className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              placeholder={t("platform.tenant.form.namePh")}
            />
          </div>
          <div>
            <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
              {t("platform.tenant.form.codeLabel")}
            </label>
            <input
              value={tenantCode}
              onChange={(e) => setTenantCode(e.target.value)}
              className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              disabled={mode === "edit"}
              placeholder={t("platform.tenant.form.codePh")}
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                {t("platform.tenant.form.statusLabel")}
              </label>
              <select
                value={status}
                onChange={(e) => setStatus(e.target.value)}
                className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
              >
                <option value="ACTIVE">{t("platform.tenant.status.ACTIVE")}</option>
                <option value="SUSPENDED">{t("platform.tenant.status.SUSPENDED")}</option>
                <option value="DELETED">{t("platform.tenant.status.DELETED")}</option>
              </select>
            </div>
            <div>
              <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
                {t("platform.tenant.form.isolationLabel")}
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
                {t("platform.tenant.form.maxUsersLabel")}
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
                {t("platform.tenant.form.maxStorageLabel")}
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
                {t("platform.tenant.form.apiDayLabel")}
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
            {t("platform.tenant.common.cancel")}
          </button>
          <button
            onClick={handleSave}
            disabled={saving}
            className={`px-4 py-2 rounded text-xs font-medium text-white flex items-center gap-1.5 ${styles.accentBg} ${styles.accentHover}`}
          >
            <Check className="w-3.5 h-3.5" />
            {saving ? t("platform.tenant.common.saving") : t("platform.tenant.common.save")}
          </button>
        </div>
      </div>
    </div>
  );
}

export default TenantFormModal;
