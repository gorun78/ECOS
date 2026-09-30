/**
 * Button — 公共按钮原语（H6-T5，见《前端开发规范》§七.7 FE-1 整改）
 * primary 变体承载"品牌填充 + 反色前景"的小面积强调件豁免：
 *   该主色底 + 反色字面量组合只允许出现在本公共组件实现内（见下方 primary 分支），
 *   业务页面/Tab 一律通过 <Button variant="primary"> 使用，不得就地复制（§七.7）。
 * secondary/ghost/danger 一律取 useTheme() 语义 token，主题差异只体现在 token 层，
 * 组件内禁止 if(theme===...) 分支（§七.6）。
 * 文案由调用方通过 children/props 传入，组件内不做 i18n 硬编码（§六）。
 * @license Apache-2.0
 */

import React from "react";
import { useTheme } from "../ThemeContext";

/** 视觉变体：primary 承载反色豁免，其余走语义 token */
export type ButtonVariant = "primary" | "secondary" | "ghost" | "danger";

/** 尺寸（至少 sm/md，另附 lg 供布局选择） */
export type ButtonSize = "sm" | "md" | "lg";

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  /** 视觉变体，默认 primary */
  variant?: ButtonVariant;
  /** 尺寸，默认 md */
  size?: ButtonSize;
}

/** 尺寸 → 内边距/字号（纯排版，不含颜色，可安全跨主题复用） */
const sizeClasses: Record<ButtonSize, string> = {
  sm: "px-2.5 py-1 text-xs",
  md: "px-4 py-2 text-sm",
  lg: "px-5 py-2.5 text-sm",
};

/**
 * Button — 主题感知的公共按钮。
 * disabled 语义：token 层未提供专门的禁用态 token，故用与主题无关的
 * `disabled:opacity-50 disabled:cursor-not-allowed` 工具类实现（不引入硬编码色值，也不做主题分支）。
 */
export default function Button({
  variant = "primary",
  size = "md",
  type = "button",
  disabled = false,
  className = "",
  children,
  ...rest
}: ButtonProps) {
  const { styles } = useTheme();

  // 变体样式映射：primary 是唯一允许出现反色豁免字面量的位置；其余全部取语义 token
  const variantClasses: Record<ButtonVariant, string> = {
    // §七.7 小面积强调件反色豁免（品牌填充底 + 反色前景字，仅此一处，业务页面禁止复制）
    primary: "bg-indigo-600 hover:bg-indigo-700 text-white border border-transparent",
    secondary: `border ${styles.appBorder} ${styles.cardBg} ${styles.cardText} ${styles.sidebarHoverBg}`,
    ghost: `bg-transparent ${styles.accentText} ${styles.sidebarHoverBg}`,
    danger: `border ${styles.dangerBorder} ${styles.dangerBg} ${styles.dangerText}`,
  };

  return (
    <button
      type={type}
      disabled={disabled}
      className={[
        "inline-flex items-center justify-center gap-1.5 rounded-lg font-medium",
        "transition-colors select-none focus-visible:outline-none",
        "disabled:opacity-50 disabled:cursor-not-allowed",
        sizeClasses[size],
        variantClasses[variant],
        className,
      ].join(" ")}
      {...rest}
    >
      {children}
    </button>
  );
}
