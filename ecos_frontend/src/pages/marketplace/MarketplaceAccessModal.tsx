/**
 * MarketplaceAccessModal — access request dialog
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React from "react";
import { X, Send, RefreshCw } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";
import type { MarketplaceBrowserAsset } from "../../api";

// ── Props ──────────────────────────────────────────────────

interface MarketplaceAccessModalProps {
  asset: MarketplaceBrowserAsset | null;
  reason: string;
  submitting: boolean;
  onReasonChange: (v: string) => void;
  onSubmit: () => void;
  onClose: () => void;
}

// ── Component ──────────────────────────────────────────────

export default function MarketplaceAccessModal({
  asset,
  reason,
  submitting,
  onReasonChange,
  onSubmit,
  onClose,
}: MarketplaceAccessModalProps) {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const tl = (zh: string, en: string) => (locale === "zh" ? zh : en);

  if (!asset) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/40"
        onClick={() => !submitting && onClose()}
      />

      {/* Modal */}
      <div
        className={`relative z-50 w-full max-w-md mx-4 ${styles.cardBg} border ${styles.sidebarBorder}
          rounded-xl shadow-2xl p-6`}
      >
        {/* Header */}
        <div className="flex items-center justify-between mb-4">
          <h2 className={`text-lg font-semibold ${styles.cardText}`}>
            {tl("申请访问权限", "Request Access")}
          </h2>
          <button
            onClick={onClose}
            disabled={submitting}
            className="opacity-50 hover:opacity-100 transition-opacity disabled:opacity-30"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Asset preview */}
        <div className={`p-3 rounded-lg mb-4 ${styles.sidebarHoverBg}`}>
          <p className={`text-sm font-medium ${styles.cardText}`}>
            {asset.name}
          </p>
          <p className="text-xs opacity-60 mt-0.5">
            {asset.category} · {asset.owner}
          </p>
        </div>

        {/* Reason */}
        <label
          className={`block text-sm font-medium mb-1.5 ${styles.cardText}`}
        >
          {tl("申请原因", "Reason")}
        </label>
        <textarea
          value={reason}
          onChange={(e) => onReasonChange(e.target.value)}
          placeholder={tl(
            "请描述您需要访问此数据资产的原因…",
            "Describe why you need access to this asset..."
          )}
          rows={3}
          className={`w-full px-3 py-2.5 rounded-lg border ${styles.sidebarBorder}
            ${styles.cardBg} ${styles.cardText} text-sm resize-none
            focus:outline-none focus:ring-2 focus:ring-indigo-500/40
            placeholder:opacity-40`}
          disabled={submitting}
        />

        {/* Buttons */}
        <div className="flex gap-2.5 mt-4">
          <button
            onClick={onClose}
            disabled={submitting}
            className={`flex-1 py-2.5 rounded-lg text-sm font-medium border ${styles.sidebarBorder}
              ${styles.sidebarHoverBg} transition-colors disabled:opacity-50`}
          >
            {tl("取消", "Cancel")}
          </button>
          <button
            onClick={onSubmit}
            disabled={submitting || !reason.trim()}
            className="flex-1 py-2.5 rounded-lg text-sm font-medium text-white
              bg-indigo-500 hover:bg-indigo-600 disabled:opacity-40 transition-colors
              flex items-center justify-center gap-1.5"
          >
            {submitting ? (
              <RefreshCw className="animate-spin w-4 h-4" />
            ) : (
              <Send className="w-3.5 h-3.5" />
            )}
            {tl("提交申请", "Submit")}
          </button>
        </div>
      </div>
    </div>
  );
}
