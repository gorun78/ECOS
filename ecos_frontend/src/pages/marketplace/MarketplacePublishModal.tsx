/**
 * MarketplacePublishModal — modal dialog for publishing a new asset
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React from "react";
import { X, Upload, RefreshCw } from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";
import { CATEGORIES } from "./MarketplaceHeader";

// ── Props ──────────────────────────────────────────────────

interface MarketplacePublishModalProps {
  open: boolean;
  name: string;
  description: string;
  category: string;
  tags: string;
  submitting: boolean;
  onNameChange: (v: string) => void;
  onDescriptionChange: (v: string) => void;
  onCategoryChange: (v: string) => void;
  onTagsChange: (v: string) => void;
  onSubmit: () => void;
  onClose: () => void;
}

// ── Component ──────────────────────────────────────────────

export default function MarketplacePublishModal({
  open,
  name,
  description,
  category,
  tags,
  submitting,
  onNameChange,
  onDescriptionChange,
  onCategoryChange,
  onTagsChange,
  onSubmit,
  onClose,
}: MarketplacePublishModalProps) {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const tl = (zh: string, en: string) => (locale === "zh" ? zh : en);

  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/40"
        onClick={() => {
          if (!submitting) onClose();
        }}
      />

      {/* Modal */}
      <div
        className={`relative z-50 w-full max-w-md mx-4 ${styles.cardBg} border ${styles.sidebarBorder}
          rounded-xl shadow-2xl p-6`}
      >
        {/* Header */}
        <div className="flex items-center justify-between mb-5">
          <h2
            className={`text-lg font-semibold ${styles.cardText} flex items-center gap-2`}
          >
            <Upload className="w-5 h-5 text-indigo-400" />
            {t("marketplace.publishBtn")}
          </h2>          <button
            onClick={onClose}
            disabled={submitting}
            className="opacity-50 hover:opacity-100 transition-opacity disabled:opacity-30"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Name */}
        <label
          className={`block text-sm font-medium mb-1.5 ${styles.cardText}`}
        >
          {t("marketplace.publish.nameLabel")}{" "}
          <span className="text-red-400">*</span>
        </label>
        <input
          type="text"
          value={name}
          onChange={(e) => onNameChange(e.target.value)}
          placeholder={t("marketplace.publish.namePlaceholder")}
          className={`w-full px-3 py-2.5 rounded-lg border ${styles.sidebarBorder}
            ${styles.cardBg} ${styles.cardText} text-sm mb-4
            focus:outline-none focus:ring-2 focus:ring-indigo-500/40
            placeholder:opacity-40`}
          disabled={submitting}
        />

        {/* Category */}
        <label
          className={`block text-sm font-medium mb-1.5 ${styles.cardText}`}
        >
          {t("marketplace.publish.categoryLabel")}
        </label>
        <select
          value={category}
          onChange={(e) => onCategoryChange(e.target.value)}
          className={`w-full px-3 py-2.5 rounded-lg border ${styles.sidebarBorder}
            ${styles.cardBg} ${styles.cardText} text-sm mb-4
            focus:outline-none focus:ring-2 focus:ring-indigo-500/40`}
          disabled={submitting}
        >
          {CATEGORIES.filter((c) => c.value).map((c) => (
            <option key={c.value} value={c.value}>
              {tl(c.labelZh, c.labelEn)}
            </option>
          ))}
        </select>

        {/* Description */}
        <label
          className={`block text-sm font-medium mb-1.5 ${styles.cardText}`}
        >
          {t("marketplace.publish.descLabel")}{" "}
          <span className="text-red-400">*</span>
        </label>
        <textarea
          value={description}
          onChange={(e) => onDescriptionChange(e.target.value)}
          placeholder={t("marketplace.publish.descPlaceholder")}
          rows={3}
          className={`w-full px-3 py-2.5 rounded-lg border ${styles.sidebarBorder}
            ${styles.cardBg} ${styles.cardText} text-sm resize-none mb-4
            focus:outline-none focus:ring-2 focus:ring-indigo-500/40
            placeholder:opacity-40`}
          disabled={submitting}
        />

        {/* Tags */}
        <label
          className={`block text-sm font-medium mb-1.5 ${styles.cardText}`}
        >
          {t("marketplace.publish.tagsLabel")}
        </label>
        <input
          type="text"
          value={tags}
          onChange={(e) => onTagsChange(e.target.value)}
          placeholder={t("marketplace.publish.tagsPlaceholder")}
          className={`w-full px-3 py-2.5 rounded-lg border ${styles.sidebarBorder}
            ${styles.cardBg} ${styles.cardText} text-sm mb-5
            focus:outline-none focus:ring-2 focus:ring-indigo-500/40
            placeholder:opacity-40`}
          disabled={submitting}
        />

        {/* Buttons */}
        <div className="flex gap-2.5">
          <button
            onClick={onClose}
            disabled={submitting}
            className={`flex-1 py-2.5 rounded-lg text-sm font-medium border ${styles.sidebarBorder}
              ${styles.sidebarHoverBg} transition-colors disabled:opacity-50`}
          >
            {t("common.cancel")}
          </button>
          <button
            onClick={onSubmit}
            disabled={submitting || !name.trim() || !description.trim()}
            className="flex-1 py-2.5 rounded-lg text-sm font-medium text-white
              bg-indigo-500 hover:bg-indigo-600 disabled:opacity-40 transition-colors
              flex items-center justify-center gap-1.5"
          >
            {submitting ? (
              <RefreshCw className="animate-spin w-4 h-4" />
            ) : (
              <Upload className="w-4 h-4" />
            )}
            {t("marketplace.publish.publishBtn")}
          </button>
        </div>
      </div>
    </div>
  );
}
