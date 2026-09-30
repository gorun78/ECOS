/**
 * ObjectExplorerFormField — 表单字段（自 ObjectExplorer.tsx 底部 helper 原样迁出，H6-T4）
 * @license Apache-2.0
 */

import { useTheme } from "../../components/ThemeContext";

export function FormField({ label, placeholder, required, value, onChange }: {
  label: string;
  placeholder?: string;
  required?: boolean;
  value: string;
  onChange: (val: string) => void;
}) {
  const { styles } = useTheme();
  return (
    <div>
      <label className={`block text-[10px] font-semibold ${styles.cardTextMuted} uppercase tracking-wider mb-1`}>
        {label}
        {required && <span className="text-red-400 ml-0.5">*</span>}
      </label>
      <input
        type="text"
        value={value}
        onChange={e => onChange(e.target.value)}
        placeholder={placeholder || label}
        className={`w-full ${styles.appBg} border ${styles.cardBorder} rounded-lg px-3 py-2 text-xs ${styles.cardText} outline-none placeholder:opacity-50 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 transition font-mono`}
      />
    </div>
  );
}
