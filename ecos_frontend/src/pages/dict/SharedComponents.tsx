import React, { useState, useEffect, useRef } from "react";
import { CheckCircle2, AlertCircle, X, ChevronDown } from "lucide-react";
import { COLUMN_TYPE_CATEGORIES } from "./constants";

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
    <button onClick={onClose} className="ml-2 opacity-60 hover:opacity-100">
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
}> = ({ targetName, targetType, onConfirm, onCancel }) => (
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
        确认删除
      </h3>
      <p style={{ fontSize: 14, color: "var(--text-muted, #888)", marginBottom: 20 }}>
        确定要删除{targetType === "table" ? "数据表" : "字段"}「{targetName}」吗？此操作不可撤销。
      </p>
      <div style={{ display: "flex", gap: 8, justifyContent: "flex-end" }}>
        <button
          onClick={onCancel}
          style={{
            padding: "6px 16px", borderRadius: 6, border: "1px solid var(--border-color, #d0d0d0)",
            background: "transparent", cursor: "pointer", fontSize: 13,
            color: "var(--text-primary, #333)",
          }}
        >
          取消
        </button>
        <button
          onClick={onConfirm}
          style={{
            padding: "6px 16px", borderRadius: 6, border: "none",
            background: "#c62828", color: "#fff", cursor: "pointer", fontSize: 13, fontWeight: 600,
          }}
        >
          删除
        </button>
      </div>
    </div>
  </div>
);

// ── Column Type Selector (categorized) ──
export const ColumnTypeSelect: React.FC<{
  value: string;
  onChange: (v: string) => void;
  disabled?: boolean;
}> = ({ value, onChange, disabled }) => {
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
        className="w-full px-3 py-2 rounded-lg border border-slate-200 bg-white text-xs text-slate-700
          flex items-center justify-between outline-none disabled:opacity-50"
        onClick={() => setOpen(!open)}
      >
        <span className="font-mono">{value}</span>
        <ChevronDown size={14} className="text-slate-400" />
      </button>
      {open && (
        <div className="absolute z-30 left-0 right-0 mt-1 bg-white border border-slate-200 rounded-lg shadow-lg max-h-64 overflow-y-auto">
          {Object.entries(COLUMN_TYPE_CATEGORIES).map(([cat, types]) => (
            <div key={cat}>
              <div className="px-3 py-1.5 text-[10px] font-semibold text-slate-400 uppercase bg-slate-50">
                {cat}
              </div>
              {types.map(t => (
                <div
                  key={t}
                  className={`px-3 py-1.5 text-xs font-mono cursor-pointer hover:bg-indigo-50 ${
                    t === value ? "bg-indigo-100 text-indigo-700 font-semibold" : "text-slate-600"
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
