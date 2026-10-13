/**
 * Toast — 轻量级通知组件 + ToastContext
 * 支持 success(绿) / error(红) / warning(琥珀) / info(蓝) 四种类型，自动3秒消失。
 * W196 收口（报告 §4.8 P1）：
 *  - 类型样式改 useTheme() 语义 token（旧裸 `bg-emerald-50 dark:...` 靠 index.css
 *    反改表续命，白名单外即漂移）；
 *  - 多条 toast 由列容器堆叠（旧实现每条各自 fixed 同坐标 = 完全叠死）；
 *  - 无订阅者兜底日志走 services/logger（dev 才输出，生产路径不再 console.log）。
 * @license Apache-2.0
 */

import React, { createContext, useContext, useState, useCallback, useRef, useEffect } from "react";
import { CheckCircle2, AlertCircle, AlertTriangle, Info, X } from "lucide-react";
import { useTheme } from "../ThemeContext";
import logger from "../../services/logger";

// ── Types ────────────────────────────────────────────────────

export type ToastType = "success" | "error" | "warning" | "info";

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

type ToastVisuals = { bg: string; border: string; text: string; icon: React.FC<{ className?: string }> };

const toastVisuals = (styles: Record<string, string>): Record<ToastType, ToastVisuals> => ({
  // token 语义底/字/框（4 主题一致反相），不再落裸色类
  success: { bg: styles.successBg, border: styles.cardBorder, text: styles.successText, icon: CheckCircle2 },
  error: { bg: styles.dangerBg, border: styles.cardBorder, text: styles.dangerText, icon: AlertCircle },
  warning: { bg: styles.warningBg, border: styles.cardBorder, text: styles.warningText, icon: AlertTriangle },
  info: { bg: styles.infoBg, border: styles.cardBorder, text: styles.infoText ?? styles.accentText, icon: Info },
});

const ToastItem: React.FC<{
  toast: ToastMessage;
  onClose: (id: number) => void;
}> = ({ toast, onClose }) => {
  const { styles } = useTheme();
  // Provider 面外（理论不发生，防御兜底）：中性 scrim token，不写裸色
  const safeStyles = (styles ?? {}) as Record<string, string>;
  const fallback: ToastVisuals = {
    bg: safeStyles.cardBg ?? "",
    border: safeStyles.cardBorder ?? "border",
    text: safeStyles.cardText ?? "",
    icon: Info,
  };
  const s = toastVisuals(safeStyles)[toast.type] ?? fallback;
  const Icon = s.icon;
  return (
    <div
      role="status"
      className={`flex items-center gap-2.5 px-4 py-3 rounded-lg shadow-lg text-sm font-medium transition-all border ${s.bg} ${s.border} ${s.text}`}
    >
      <Icon className="w-4 h-4 shrink-0" />
      <span>{toast.message}</span>
      <button type="button" onClick={() => onClose(toast.id)} className="ml-2 opacity-60 hover:opacity-100 cursor-pointer" aria-label="close">
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
    // 测试/Story 环境下无 Provider 时静默降级；logger.debug 仅 dev 输出
    // （报告 §4.8 P2：旧实现生产路径裸 console.log）
    logger.debug(`[toast:${type}] ${message}`);
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
      {/* 列容器堆叠（报告 §4.8 P1：旧实现每条 toast 各自 fixed 同一坐标 = 完全叠死） */}
      {toasts.length > 0 && (
        <div className="fixed top-6 right-6 z-50 flex flex-col items-end gap-2 pointer-events-none [&>*]:pointer-events-auto">
          {toasts.map(t => (
            <ToastItem key={t.id} toast={t} onClose={removeToast} />
          ))}
        </div>
      )}
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
