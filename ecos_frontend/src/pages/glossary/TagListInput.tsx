/**
 * TagListInput — 字符串标签数组的通用输入子组件。
 *
 * 用途：词条的 aliases / examples / tags 三处复用（输入框 + 回车/按钮添加 + chip 可删除）。
 * 行为：自动去除首尾空白、忽略空值与重复项。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from "react";
import { Plus, X } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";

interface TagListInputProps {
  /** 当前标签数组 */
  values: string[];
  /** 变更回调 */
  onChange: (next: string[]) => void;
  /** 输入框占位文案 */
  placeholder: string;
  /** 添加按钮无障碍文案 */
  addLabel: string;
  /** 删除单个标签的提示文案 */
  removeLabel: string;
  /** 是否禁用 */
  disabled?: boolean;
}

export default function TagListInput({
  values,
  onChange,
  placeholder,
  addLabel,
  removeLabel,
  disabled = false,
}: TagListInputProps) {
  const { styles } = useTheme();
  const [draft, setDraft] = useState("");

  /** 追加一个标签（去空白、去空、去重） */
  const addTag = () => {
    const value = draft.trim();
    if (!value) {
      return;
    }
    if (values.includes(value)) {
      setDraft("");
      return;
    }
    onChange([...values, value]);
    setDraft("");
  };

  /** 回车提交，避免触发表单默认行为 */
  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      e.preventDefault();
      addTag();
    }
  };

  /** 删除指定位置的标签 */
  const removeTag = (index: number) => {
    onChange(values.filter((_, i) => i !== index));
  };

  return (
    <div className="space-y-1.5">
      {/* 已有标签 */}
      {values.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {values.map((value, index) => (
            <span
              key={`${value}-${index}`}
              className={`inline-flex items-center gap-1 px-2 py-0.5 rounded border text-[11px]
                ${styles.badgeBg} ${styles.badgeText} ${styles.cardBorder}`}
            >
              {value}
              <button
                type="button"
                onClick={() => removeTag(index)}
                disabled={disabled}
                className="opacity-60 hover:opacity-100 disabled:opacity-30"
                title={removeLabel}
              >
                <X size={10} />
              </button>
            </span>
          ))}
        </div>
      )}

      {/* 输入 + 添加 */}
      <div className="flex items-center gap-2">
        <input
          className={`flex-1 px-3 py-2 rounded-lg border text-xs outline-none
            ${styles.inputBorder} ${styles.inputBg} ${styles.inputText}
            focus:border-indigo-400 disabled:opacity-50`}
          placeholder={placeholder}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={disabled}
        />
        <button
          type="button"
          onClick={addTag}
          disabled={disabled || !draft.trim()}
          className={`p-2 rounded-lg border transition disabled:opacity-40
            ${styles.cardBorder} ${styles.sidebarHoverBg} ${styles.muted}`}
          title={addLabel}
        >
          <Plus size={14} />
        </button>
      </div>
    </div>
  );
}