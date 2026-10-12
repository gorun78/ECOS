/**
 * TenantManager 配额编辑弹窗 — 从 pages/TenantManager.tsx 结构拆分而来（JSX/逻辑逐字保留）
 * @license Apache-2.0
 */

import { useState } from "react";
import { Check, X } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";
import type { QuotaItem } from "./types";

// ── Edit Quota Modal ───────────────────────────────────────────

export interface EditQuotaModalProps {
  quota: QuotaItem;
  onSave: (dailyLimit: number, monthlyLimit: number) => Promise<void>;
  onClose: () => void;
}

export function EditQuotaModal({ quota, onSave, onClose }: EditQuotaModalProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [dailyLimit, setDailyLimit] = useState(String(quota.dailyLimit));
  const [monthlyLimit, setMonthlyLimit] = useState(String(quota.monthlyLimit));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");

  const handleSave = async () => {
    const d = Number(dailyLimit);
    const m = Number(monthlyLimit);
    if (isNaN(d) || isNaN(m) || d < 0 || m < 0 || d > m) {
      setError(t("platform.tenant.eqq.validationErr"));
      return;
    }
    setSaving(true);
    setError("");
    try {
      await onSave(d, m);
      onClose();
    } catch (e: any) {
      setError(e.message || "Save failed");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
      <div className={`rounded-lg border p-6 w-full max-w-md ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center justify-between mb-4">
          <h3 className={`text-sm font-semibold ${styles.cardText}`}>
            {t("platform.tenant.eqq.title")} — {quota.quotaType}
          </h3>
          <button type="button" onClick={onClose} className="opacity-60 hover:opacity-100"><X className="w-4 h-4" /></button>
        </div>
        {error && <div className="mb-3 p-2 rounded bg-red-500/10 border border-red-500/30 text-red-400 text-xs">{error}</div>}
        <div className="space-y-3">
          <div>
            <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{t("platform.tenant.eqq.dailyLabel")}</label>
            <input type="number" min={0} value={dailyLimit} onChange={(e) => setDailyLimit(e.target.value)}
              className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`} />
          </div>
          <div>
            <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>{t("platform.tenant.eqq.monthlyLabel")}</label>
            <input type="number" min={0} value={monthlyLimit} onChange={(e) => setMonthlyLimit(e.target.value)}
              className={`w-full px-3 py-2 rounded text-sm border ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`} />
          </div>
        </div>
        <div className="flex justify-end gap-2 mt-4">
          <button type="button" onClick={onClose} className={`px-4 py-2 rounded text-xs border ${styles.cardBorder} ${styles.cardText}`}>
            {t("platform.tenant.common.cancel")}
          </button>
          <button type="button" onClick={handleSave} disabled={saving}
            className={`px-4 py-2 rounded text-xs font-medium text-white flex items-center gap-1.5 ${styles.accentBg} ${styles.accentHover}`}>
            <Check className="w-3.5 h-3.5" />
            {saving ? t("platform.tenant.common.saving") : t("platform.tenant.common.save")}
          </button>
        </div>
      </div>
    </div>
  );
}

export default EditQuotaModal;
