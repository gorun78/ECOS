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
  visible?: boolean;
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  danger?: boolean;
  variant?: "danger" | "warning" | "default";
  confirmClass?: string;
  /**
   * 兼容别名：调用方（Topbar / 部分新页）以 `open` 传可见性。
   * 两者任一为 true 即显示——避免组件在非受控命名下静默不渲染（= 确认门失效）。
   */
  open?: boolean;
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
  open,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  /* visible 可选后统一收敛：两者任一为 true 即显示（open 别名语义保留）。 */
  const shown = visible ?? open ?? false;
  if (!shown) return null;

  const defaultConfirm = confirmText || t("common.confirm");
  const defaultCancel = cancelText || t("common.cancel");

  const _danger = danger ?? variant === "danger";

  return (
    <div
      className="fixed inset-0 z-40 flex items-center justify-center"
      role="alertdialog"
      aria-modal="true"
      aria-label={title}
    >
      {/* overlay：旧实现 bg-black/40 为写死色，浅色主题下压不住白底弹窗边框；改 styles.overlayBg */}
      <div className={`absolute inset-0 ${styles.overlayBg}`} onClick={onCancel} />
      <div className={`relative z-50 w-full max-w-sm mx-4 rounded-xl shadow-2xl p-6 border ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center gap-2 mb-2">
          {variant === "danger" && <Trash2 className={`w-4 h-4 ${styles.dangerText}`} />}
          {variant === "warning" && <AlertTriangle className={`w-4 h-4 ${styles.warningText}`} />}
          {variant === "default" && <AlertTriangle className={`w-4 h-4 ${styles.accentText}`} />}
          <h3 className={`text-base font-bold ${styles.cardText}`}>{title}</h3>
        </div>
        <p className={`text-sm mb-5 ${styles.cardTextMuted}`}>{message}</p>
        <div className="flex gap-2 justify-end">
          <button
            type="button"
            onClick={onCancel}
            className={`px-4 py-1.5 rounded text-xs border ${styles.cardBorder} ${styles.cardText} ${styles.sidebarHoverBg}`}
          >
            {defaultCancel}
          </button>
          {/* 确认按钮：三档字面色板（red/amber/indigo-500~700 + text-white）→ 语义 token。
              slate-light 的 index.css 反 patch 会把 text-white 翻成近黑，故实心态配
              accentSolidText（per-theme 反相前景）。 */}
          <button type="button"
            onClick={onConfirm}
            className={
              confirmClass ||
              (`px-4 py-1.5 rounded text-xs font-semibold border ${
                _danger
                  ? `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`
                  : variant === "warning"
                    ? `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`
                    : `${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`
              }`)
            }
          >
            {defaultConfirm}
          </button>
        </div>
      </div>
    </div>
  );
}
