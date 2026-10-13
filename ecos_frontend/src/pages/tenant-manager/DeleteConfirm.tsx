/**
 * TenantManager 删除确认弹窗 — 从 pages/TenantManager.tsx 结构拆分而来
 * 增强（G-22）：i18n 全面接入 + 输名强确认（键入租户名后方可点删除）
 * @license Apache-2.0
 */

import React, { useState } from "react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";

// ── Delete Confirm Dialog ─────────────────────────────────────

export const DeleteConfirm: React.FC<{
  targetName: string;
  onConfirm: () => void;
  onCancel: () => void;
}> = ({ targetName, onConfirm, onCancel }) => {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [typed, setTyped] = useState("");
  const matches = typed === targetName;

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center">
      <div className={`absolute inset-0 ${styles.overlayBg}`} onClick={onCancel} />
      <div className={`relative z-50 w-full max-w-sm mx-4 rounded-xl shadow-2xl p-6 ${styles.cardBg} border ${styles.cardBorder}`}>
        <h3 className={`text-base font-bold mb-2 ${styles.cardText}`}>{t("platform.tenant.deleteConfirmTitle")}</h3>
        <p className={`text-sm ${styles.cardTextMuted} mb-4`}>
          {t("platform.tenant.deleteConfirmDesc").replace("{name}", targetName)}
        </p>
        <label className={`block text-xs mb-1 ${styles.cardTextMuted}`}>
          {t("platform.tenant.deleteConfirmTypeToConfirm").replace("{name}", targetName)}
        </label>
        <input
          value={typed}
          onChange={(e) => setTyped(e.target.value)}
          autoComplete="off"
          spellCheck={false}
          aria-label={targetName}
          className={`w-full px-3 py-2 rounded text-xs border mb-5 ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
        />
        <div className="flex gap-2 justify-end">
          <button type="button" onClick={onCancel} className={`px-4 py-1.5 rounded text-xs border ${styles.cardBorder} ${styles.cardTextMuted} ${styles.sidebarHoverBg}`}>
            {t("platform.tenant.common.cancel")}
          </button>
          <button type="button" onClick={onConfirm} disabled={!matches}
            className={`px-4 py-1.5 rounded text-xs font-semibold border disabled:opacity-40 disabled:cursor-not-allowed ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
            {t("common.delete")}
          </button>
        </div>
      </div>
    </div>
  );
};

export default DeleteConfirm;
