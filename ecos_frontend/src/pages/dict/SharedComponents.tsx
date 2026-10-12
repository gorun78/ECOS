import React, { useState, useEffect, useRef } from "react";
import { CheckCircle2, AlertCircle, X, ChevronDown } from "lucide-react";
import { COLUMN_TYPE_CATEGORIES } from "./constants";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";

// ── Toast ──
export const Toast: React.FC<{
  toast: { type: "success" | "error"; msg: string };
  onClose: () => void;
}> = ({ toast, onClose }) => (
  <div
    className={`fixed top-6 right-6 z-50 flex items-center gap-2.5 px-4 py-3
      rounded-lg shadow-lg text-sm font-medium transition-all
      ${toast.type === "success"
        ? "bg-emerald-50 dark:bg-emerald-950 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-200"
        : "bg-red-50 dark:bg-red-950 border border-red-200 dark:border-red-800 text-red-800 dark:text-red-200"
      }`}
  >
    {toast.type === "success"
      ? <CheckCircle2 className="w-4 h-4 shrink-0" />
      : <AlertCircle className="w-4 h-4 shrink-0" />
    }
    <span>{toast.msg}</span>
    <button type="button" onClick={onClose} className="ml-2 opacity-60 hover:opacity-100">
      <X className="w-3.5 h-3.5" />
    </button>
  </div>
);

// ── Delete Confirm Dialog ──
export const DeleteConfirm: React.FC<{
  targetName: string;
  targetType: "table" | "column";
  onConfirm: () => void;
  onCancel: () => void;
}> = ({ targetName, targetType, onConfirm, onCancel }) => {
  const { t } = useLanguage();
  const entityLabel = targetType === "table" ? t('platform.dictionary.dataTable') : t('platform.dictionary.field');
  return (
  <div className="fixed inset-0 z-40 flex items-center justify-center">
    <div className="absolute inset-0 bg-black/40" onClick={onCancel} />
    <div
      className="relative z-50 w-full max-w-sm mx-4 rounded-xl shadow-2xl p-6"
      style={{
        background: "var(--content-bg, #fff)",
        border: "1px solid var(--border-color, #e0e0e0)",
      }}
    >
      <h3 style={{ fontSize: 16, fontWeight: 700, marginBottom: 8, color: "var(--text-primary, #222)" }}>
        {t('platform.dictionary.confirmDelete')}
      </h3>
      <p style={{ fontSize: 14, color: "var(--text-muted, #888)", marginBottom: 20 }}>
        {t('platform.dictionary.deleteConfirmMsg', { type: entityLabel, name: targetName })}
      </p>
      <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
        <button
          type="button"
          onClick={onCancel}
          style={{
            padding: "6px 16px", borderRadius: 6, border: "1px solid var(--border-color, #d0d0d0)",
            background: "transparent", cursor: "pointer", fontSize: 13,
            color: "var(--text-primary, #333)",
          }}
        >
          {t('common.cancel')}
        </button>
        <button
          type="button"
          onClick={onConfirm}
          style={{
            padding: "6px 16px", borderRadius: 6, border: "none",
            background: "#c62828", color: "#fff", cursor: "pointer", fontSize: 13, fontWeight: 600,
          }}
        >
          {t('platform.dictionary.delete')}
        </button>
      </div>
    </div>
  </div>
  );
};

// ── Column Type Selector (categorized) ──
export const ColumnTypeSelect: React.FC<{
  value: string;
  onChange: (v: string) => void;
  disabled?: boolean;
}> = ({ value, onChange, disabled }) => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        disabled={disabled}
        className={`w-full px-3 py-2 rounded-lg border ${styles.inputBorder} ${styles.inputBg} text-xs ${styles.cardText}
          flex items-center justify-between outline-none disabled:opacity-50`}
        onClick={() => setOpen(!open)}
      >
        <span className="font-mono">{value}</span>
        <ChevronDown size={14} className={styles.cardTextMuted} />
      </button>
      {open && (
        <div className={`absolute z-30 left-0 right-0 mt-1 ${styles.cardBg} border ${styles.appBorder} rounded-lg shadow-lg max-h-64 overflow-y-auto`}>
          {Object.entries(COLUMN_TYPE_CATEGORIES).map(([cat, group]) => (
            <div key={cat}>
              <div className={`px-3 py-1.5 text-[10px] font-semibold ${styles.cardTextMuted} uppercase ${styles.appBg}`}>
                {t(group.labelKey)}
              </div>
              {group.types.map(t => (
                <div
                  key={t}
                  className={`px-3 py-1.5 text-xs font-mono cursor-pointer hover:bg-indigo-50 ${
                    t === value ? "bg-indigo-100 text-indigo-700 font-semibold" : styles.cardText
                  }`}
                  onClick={() => { onChange(t); setOpen(false); }}
                >
                  {t}
                </div>
              ))}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
