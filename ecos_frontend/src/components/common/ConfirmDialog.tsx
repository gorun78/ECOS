/**
 * ConfirmDialog — 通用确认对话框
 * 支持 danger(红) / warning(黄) / default 三种样式
 * @license Apache-2.0
 */

import React from "react";
import { Trash2, AlertTriangle } from "lucide-react";
import { useLanguage } from "../LanguageContext";
import { useTheme } from "../ThemeContext";

interface ConfirmDialogProps {
  visible: boolean;
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  danger?: boolean;
  variant?: "danger" | "warning" | "default";
  confirmClass?: string;
  onConfirm: () => void;
  onCancel: () => void;
}

export default function ConfirmDialog({
  visible,
  title,
  message,
  confirmText,
  cancelText,
  danger,
  variant = "danger",
  confirmClass,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const isZh = locale === "zh";

  if (!visible) return null;

  const defaultConfirm = confirmText || (isZh ? "确认" : "Confirm");
  const defaultCancel = cancelText || (isZh ? "取消" : "Cancel");

  const _danger = danger ?? variant === "danger";

  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40" onClick={onCancel} />
      <div className={`relative z-50 w-full max-w-sm mx-4 rounded-xl shadow-2xl p-6 ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center gap-2 mb-2">
          {variant === "danger" && <Trash2 className="w-4 h-4 text-red-500" />}
          {variant === "warning" && <AlertTriangle className="w-4 h-4 text-amber-500" />}
          <h3 className={`text-base font-bold ${styles.cardText}`}>{title}</h3>
        </div>
        <p className={`text-sm mb-5 ${styles.cardTextMuted}`}>{message}</p>
        <div className="flex gap-2 justify-end">
          <button
            onClick={onCancel}
            className={`px-4 py-1.5 rounded text-xs border ${styles.cardBorder} ${styles.cardText} hover:bg-gray-50 dark:hover:bg-white/5`}
          >
            {defaultCancel}
          </button>
          <button
            onClick={onConfirm}
            className={
              confirmClass ||
              (_danger
                ? "px-4 py-1.5 rounded text-xs font-semibold bg-red-600 text-white hover:bg-red-700"
                : variant === "warning"
                  ? "px-4 py-1.5 rounded text-xs font-semibold bg-amber-500 text-white hover:bg-amber-600"
                  : "px-4 py-1.5 rounded text-xs font-semibold bg-indigo-500 text-white hover:bg-indigo-600")
            }
          >
            {defaultConfirm}
          </button>
        </div>
      </div>
    </div>
  );
}
