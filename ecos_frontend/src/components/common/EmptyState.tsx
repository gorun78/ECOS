/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from "react";
import { Inbox } from "lucide-react";
import { useTheme } from "../ThemeContext";
import { useLanguage } from "../LanguageContext";

export interface EmptyStateProps {
  /** Custom icon component. Defaults to Inbox */
  icon?: React.ReactNode;
  /** Title text. Default: localized "暂无数据" */
  title?: string;
  /** Description text. Default: localized "当前没有可显示的内容" */
  description?: string;
  /** Optional action button config */
  action?: {
    label: string;
    onClick: () => void;
  };
  /** Optional CSS class name */
  className?: string;
}

export default function EmptyState({
  icon,
  title,
  description,
  action,
  className = "",
}: EmptyStateProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const defaultTitle = title ?? t("common.empty.title");
  const defaultDescription = description ?? t("common.empty.desc");

  return (
    <div
      className={`flex flex-col items-center justify-center py-16 px-6 text-center select-none ${className}`}
    >
      <div className={`mb-4 ${styles.cardTextMuted}`}>
        {icon ?? <Inbox className="w-12 h-12 opacity-40" />}
      </div>
      <h3 className={`text-sm font-bold mb-1.5 ${styles.cardText}`}>
        {defaultTitle}
      </h3>
      <p className={`text-xs max-w-xs leading-relaxed ${styles.cardTextMuted}`}>
        {defaultDescription}
      </p>
      {action && (
        // 报告 §4.8 P1：旧实现 `${accentBg} ${accentText}` = 靛蓝底 + 靛蓝字（同色系叠加，
        // 对比度 ≈1:1，CTA 视觉上不存在）。实心态一律配 accentSolidText 反相前景。
        <button type="button"
          onClick={action.onClick}
          className={`mt-5 px-5 py-2 text-xs font-bold rounded-lg cursor-pointer transition ${styles.accentBg} ${styles.accentSolidText} hover:opacity-90 shadow-xs`}
        >
          {action.label}
        </button>
      )}
    </div>
  );
}
