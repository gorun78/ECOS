/**
 * TenantManager Toast — 从 pages/TenantManager.tsx 结构拆分而来（JSX 逐字保留）
 * @license Apache-2.0
 */

import React from "react";
import { AlertCircle, CheckCircle2, X } from "lucide-react";
import type { ToastState } from "./types";

// ── Toast ──────────────────────────────────────────────────────

export const Toast: React.FC<{
  toast: ToastState;
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

export default Toast;
