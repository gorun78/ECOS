/**
 * Toast — 轻量级通知组件 + ToastContext
 * 支持 success(绿) / error(红) / info(蓝) 三种类型，自动3秒消失
 * @license Apache-2.0
 */

import React, { createContext, useContext, useState, useCallback, useRef, useEffect } from "react";
import { CheckCircle2, AlertCircle, Info, X } from "lucide-react";

// ── Types ────────────────────────────────────────────────────

export type ToastType = "success" | "error" | "info";

export interface ToastMessage {
  id: number;
  type: ToastType;
  message: string;
}

interface ToastContextType {
  showToast: (type: ToastType, message: string) => void;
}

const ToastContext = createContext<ToastContextType | undefined>(undefined);

// ── Toast Item ───────────────────────────────────────────────

const typeStyles: Record<ToastType, { bg: string; border: string; text: string; icon: React.FC<{ className?: string }> }> = {
  success: {
    bg: "bg-emerald-50 dark:bg-emerald-950",
    border: "border-emerald-200 dark:border-emerald-800",
    text: "text-emerald-800 dark:text-emerald-200",
    icon: CheckCircle2,
  },
  error: {
    bg: "bg-red-50 dark:bg-red-950",
    border: "border-red-200 dark:border-red-800",
    text: "text-red-800 dark:text-red-200",
    icon: AlertCircle,
  },
  info: {
    bg: "bg-blue-50 dark:bg-blue-950",
    border: "border-blue-200 dark:border-blue-800",
    text: "text-blue-800 dark:text-blue-200",
    icon: Info,
  },
};

const ToastItem: React.FC<{
  toast: ToastMessage;
  onClose: (id: number) => void;
}> = ({ toast, onClose }) => {
  const s = typeStyles[toast.type];
  const Icon = s.icon;
  return (
    <div
      className={`fixed top-6 right-6 z-50 flex items-center gap-2.5 px-4 py-3 rounded-lg shadow-lg text-sm font-medium transition-all border ${s.bg} ${s.border} ${s.text}`}
    >
      <Icon className="w-4 h-4 shrink-0" />
      <span>{toast.message}</span>
      <button onClick={() => onClose(toast.id)} className="ml-2 opacity-60 hover:opacity-100">
        <X className="w-3.5 h-3.5" />
      </button>
    </div>
  );
};

// ── Toast Provider ───────────────────────────────────────────

let toastIdCounter = 0;

/**
 * PMO-43 T2: 模块级全局 toast 句柄 — 供 ToastProvider 之外的代码（路由守卫、
 * apiFetch 401/403 事件监听、测试环境无 Provider 场景）发送 toast。
 * 页面已收到 propShowToast 时仍以 prop 为准，仅在 prop 缺席时降级到此处。
 */
const toastSubscribers: Array<(type: ToastType, message: string) => void> = [];

export function showToastGlobal(type: ToastType, message: string): void {
  if (toastSubscribers.length > 0) {
    toastSubscribers.forEach(handler => handler(type, message));
  } else {
    // 测试/Story 环境下无 Provider 时静默降级
    if (typeof console !== "undefined") console.log(`[toast:${type}] ${message}`);
  }
}

export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [toasts, setToasts] = useState<ToastMessage[]>([]);
  const timersRef = useRef<Map<number, ReturnType<typeof setTimeout>>>(new Map());

  const removeToast = useCallback((id: number) => {
    setToasts(prev => prev.filter(t => t.id !== id));
    const timer = timersRef.current.get(id);
    if (timer) {
      clearTimeout(timer);
      timersRef.current.delete(id);
    }
  }, []);

  const showToast = useCallback((type: ToastType, message: string) => {
    const id = ++toastIdCounter;
    setToasts(prev => [...prev, { id, type, message }]);
    const timer = setTimeout(() => {
      removeToast(id);
    }, 3000);
    timersRef.current.set(id, timer);
  }, [removeToast]);

  const showToastRef = useRef(showToast);
  showToastRef.current = showToast;

  useEffect(() => {
    const handler: (type: ToastType, message: string) => void = (type, message) => showToastRef.current(type, message);
    toastSubscribers.push(handler);
    return () => {
      const idx = toastSubscribers.indexOf(handler);
      if (idx >= 0) toastSubscribers.splice(idx, 1);
    };
  }, []);

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      {toasts.map(t => (
        <ToastItem key={t.id} toast={t} onClose={removeToast} />
      ))}
    </ToastContext.Provider>
  );
};

// ── Hook ─────────────────────────────────────────────────────

export function useToast(): ToastContextType {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast must be used within a ToastProvider");
  }
  return context;
}
