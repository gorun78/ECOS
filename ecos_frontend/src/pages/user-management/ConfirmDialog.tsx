/**
 * ConfirmDialog — 操作确认对话框（从 UserManagement.tsx 抽取，逐字搬迁）
 * @license Apache-2.0
 */

import React from "react";
import { Trash2, AlertTriangle } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";

export default function ConfirmDialog({ title, message, confirmLabel, confirmClass, onConfirm, onCancel, variant = "danger" }: {
  title: string;
  message: string;
  confirmLabel?: string;
  confirmClass?: string;
  onConfirm: () => void;
  onCancel: () => void;
  variant?: "danger" | "warning" | "default";
}) {
  const { styles } = useTheme();
  return (
  <div className="fixed inset-0 z-40 flex items-center justify-center">
    <div className="absolute inset-0 bg-black/40" onClick={onCancel} />
    <div className="relative z-50 w-full max-w-sm mx-4 rounded-xl shadow-2xl p-6 bg-white dark:bg-[#141924] border border-[#E2E8F0] dark:border-[#1E293B]">
      <div className="flex items-center gap-2 mb-2">
        {variant === "danger" && <Trash2 className="w-4 h-4 text-red-500" />}
        {variant === "warning" && <AlertTriangle className="w-4 h-4 text-amber-500" />}
        <h3 className={`text-base font-bold ${styles.cardText}`}>{title}</h3>
      </div>
      <p className={`text-sm ${styles.cardTextMuted} mb-5`}>{message}</p>
      <div className="flex gap-2 justify-end">
        <button onClick={onCancel}
          className="px-4 py-1.5 rounded text-xs border border-[#E2E8F0] dark:border-[#1E293B] text-[#334155] dark:text-[#cbd5e1] hover:bg-gray-50 dark:hover:bg-white/5">
          取消
        </button>
        <button onClick={onConfirm}
          className={confirmClass || "px-4 py-1.5 rounded text-xs font-semibold bg-red-600 text-white hover:bg-red-700"}>
          {confirmLabel || "确认"}
        </button>
      </div>
    </div>
  </div>
  );
}
