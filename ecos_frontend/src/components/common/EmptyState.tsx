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
  const { t, locale } = useLanguage();

  const defaultTitle =
    title ?? (locale === "zh" ? "暂无数据" : "No Data");
  const defaultDescription =
    description ??
    (locale === "zh"
      ? "当前没有可显示的内容"
      : "Nothing to display here yet.");

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
        <button
          onClick={action.onClick}
          className={`mt-5 px-5 py-2 text-xs font-bold rounded-lg cursor-pointer transition ${styles.accentBg} ${styles.accentText} hover:opacity-90 shadow-xs`}
        >
          {action.label}
        </button>
      )}
    </div>
  );
}
