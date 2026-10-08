/**
 * TenantManager 删除确认弹窗 — 从 pages/TenantManager.tsx 结构拆分而来（JSX 逐字保留）
 * @license Apache-2.0
 */

import React from "react";
import { useTheme } from "../../components/ThemeContext";

// ── Delete Confirm Dialog ─────────────────────────────────────

export const DeleteConfirm: React.FC<{
  targetName: string;
  onConfirm: () => void;
  onCancel: () => void;
}> = ({ targetName, onConfirm, onCancel }) => {
  const { styles } = useTheme();
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40" onClick={onCancel} />
      <div className={`relative z-50 w-full max-w-sm mx-4 rounded-xl shadow-2xl p-6 ${styles.cardBg} border ${styles.cardBorder}`}>
        <h3 className={`text-base font-bold mb-2 ${styles.cardText}`}>确认删除</h3>
        <p className={`text-sm ${styles.cardTextMuted} mb-5`}>确定要删除租户「{targetName}」吗？此操作不可撤销。</p>
        <div className="flex gap-2 justify-end">
          <button type="button" onClick={onCancel} className={`px-4 py-1.5 rounded text-xs border ${styles.cardBorder} ${styles.cardTextMuted} hover:bg-white/5`}>取消</button>
          <button onClick={onConfirm} className="px-4 py-1.5 rounded text-xs font-semibold bg-red-600 text-white hover:bg-red-700">删除</button>
        </div>
      </div>
    </div>
  );
};

export default DeleteConfirm;
