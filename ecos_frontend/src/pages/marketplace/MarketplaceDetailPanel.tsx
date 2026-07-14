/**
 * MarketplaceDetailPanel — slide-in detail side panel for an asset
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React from "react";
import {
  X,
  Star,
  User,
  Clock,
  TrendingUp,
  Send,
  MessageSquare,
  RefreshCw,
  Database,
} from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";
import { useDict } from "../../hooks/useDict";
import type { MarketplaceBrowserAsset } from "../../api";
import { TYPE_ICONS } from "./MarketplaceCardGrid";

// ── Helpers ────────────────────────────────────────────────

function formatDate(iso: string): string {
  try {
    const d = new Date(iso);
    return d.toLocaleDateString("zh-CN", {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
  } catch {
    return iso?.substring(0, 10) || "";
  }
}

function fmtNum(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + "\u4e07";
  if (n >= 1000) return (n / 1000).toFixed(1) + "k";
  return String(n);
}

function renderStars(rating: number, size = "w-3.5 h-3.5") {
  const full = Math.floor(rating);
  const hasHalf = rating - full >= 0.5;
  const stars: React.ReactNode[] = [];
  for (let i = 0; i < 5; i++) {
    if (i < full) {
      stars.push(
        <Star key={i} className={`${size} fill-amber-400 text-amber-400`} />
      );
    } else if (i === full && hasHalf) {
      stars.push(
        <span key={i} className="relative inline-block">
          <Star className={`${size} text-slate-600`} />
          <span className="absolute inset-0 overflow-hidden w-1/2">
            <Star className={`${size} fill-amber-400 text-amber-400`} />
          </span>
        </span>
      );
    } else {
      stars.push(<Star key={i} className={`${size} text-slate-600`} />);
    }
  }
  return stars;
}

// ── Props ──────────────────────────────────────────────────

interface MarketplaceDetailPanelProps {
  asset: MarketplaceBrowserAsset | null;
  loading: boolean;
  onClose: () => void;
  onRequestAccess: (asset: MarketplaceBrowserAsset) => void;
}

// ── Component ──────────────────────────────────────────────

export default function MarketplaceDetailPanel({
  asset,
  loading,
  onClose,
  onRequestAccess,
}: MarketplaceDetailPanelProps) {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const { getLabel, getColor } = useDict("asset_status", locale);
  const tl = (zh: string, en: string) => (locale === "zh" ? zh : en);

  if (!asset) return null;

  return (
    <div className="fixed inset-0 z-40 flex justify-end">
      {/* Backdrop */}
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />

      {/* Panel */}
      <div
        className={`relative z-50 w-full max-w-lg h-full overflow-auto ${styles.appBg}
          border-l ${styles.sidebarBorder} shadow-2xl animate-slide-in-right`}
      >
        {loading ? (
          <div className="flex items-center justify-center h-full opacity-50">
            <RefreshCw className="animate-spin w-6 h-6 mr-3" />
            {tl("加载详情…", "Loading details...")}
          </div>
        ) : (
          <div className="p-6">
            {/* Close button */}
            <div className="flex items-center justify-between mb-5">
              <h2 className={`text-lg font-bold ${styles.cardText}`}>
                {tl("资产详情", "Asset Detail")}
              </h2>
              <button
                onClick={onClose}
                className="p-1.5 rounded-lg hover:bg-slate-700/50 transition-colors opacity-60 hover:opacity-100"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Asset info card */}
            <div
              className={`p-4 rounded-lg ${styles.sidebarHoverBg} mb-5`}
            >
              <div className="flex items-center gap-3 mb-3">
                {(() => {
                  const TI = TYPE_ICONS[asset.category] || Database;
                  return (
                    <div className="p-2 rounded-lg bg-indigo-500/10">
                      <TI className="w-6 h-6 text-indigo-400" />
                    </div>
                  );
                })()}
                <div>
                  <h3
                    className={`font-semibold text-lg ${styles.cardText}`}
                  >
                    {asset.name}
                  </h3>
                  <div className="flex items-center gap-2 mt-0.5">
                    <span className="text-xs font-mono uppercase tracking-wider px-1.5 py-0.5 rounded bg-slate-700/50 opacity-70">
                      {asset.category}
                    </span>
                    <span
                      className="text-[10px] px-1.5 py-0.5 rounded border"
                      style={{
                        color: getColor(asset.status, "published"),
                        borderColor: getColor(asset.status, "published"),
                      }}
                    >
                      {getLabel(asset.status, "published")}
                    </span>
                  </div>
                </div>
              </div>

              <p
                className={`text-sm leading-relaxed opacity-80 mb-3 ${styles.cardText}`}
              >
                {asset.description || tl("暂无描述", "No description")}
              </p>

              <div className="flex items-center gap-4 text-xs opacity-50">
                <span className="flex items-center gap-1">
                  <User className="w-3.5 h-3.5" /> {asset.owner}
                </span>
                <span className="flex items-center gap-1">
                  <Clock className="w-3.5 h-3.5" />{" "}
                  {formatDate(asset.createdAt)}
                </span>
                <span className="flex items-center gap-1">
                  <TrendingUp className="w-3.5 h-3.5" />{" "}
                  {fmtNum(asset.popularity)} views
                </span>
              </div>

              {/* Rating */}
              <div className="flex items-center gap-2 mt-3">
                <div className="flex items-center gap-0.5">
                  {renderStars(asset.rating, "w-5 h-5")}
                </div>
                <span className={`text-lg font-bold ${styles.cardText}`}>
                  {asset.rating.toFixed(1)}
                </span>
                <span className="text-xs opacity-50">/ 5</span>
              </div>

              {/* Tags */}
              {asset.tags && asset.tags.length > 0 && (
                <div className="flex flex-wrap gap-1.5 mt-3">
                  {asset.tags.map((tag) => (
                    <span
                      key={tag}
                      className="text-[10px] px-2 py-0.5 rounded-full bg-indigo-500/15 text-indigo-400"
                    >
                      {tag}
                    </span>
                  ))}
                </div>
              )}
            </div>

            {/* Request Access button */}
            <button
              onClick={() => onRequestAccess(asset)}
              className="w-full flex items-center justify-center gap-2 py-2.5 rounded-lg
                bg-indigo-500 hover:bg-indigo-600 text-white text-sm font-medium
                transition-colors duration-150 mb-6"
            >
              <Send className="w-4 h-4" />
              {tl("申请访问权限", "Request Access")}
            </button>

            {/* Reviews */}
            <div>
              <h3
                className={`text-sm font-semibold ${styles.cardText} mb-3 flex items-center gap-2`}
              >
                <MessageSquare className="w-4 h-4 opacity-60" />
                {tl("用户评价", "Reviews")}
                {asset.reviews && (
                  <span className="text-xs font-normal opacity-50">
                    ({asset.reviews.length})
                  </span>
                )}
              </h3>

              {!asset.reviews || asset.reviews.length === 0 ? (
                <p className="text-xs opacity-40 py-4 text-center">
                  {tl("暂无评价", "No reviews yet")}
                </p>
              ) : (
                <div className="space-y-3">
                  {asset.reviews.map((review) => (
                    <div
                      key={review.id}
                      className={`p-3 rounded-lg ${styles.sidebarHoverBg}`}
                    >
                      <div className="flex items-center justify-between mb-1.5">
                        <span
                          className={`text-xs font-medium ${styles.cardText}`}
                        >
                          {review.userName}
                        </span>
                        <span className="text-[10px] opacity-40">
                          {formatDate(review.createdAt)}
                        </span>
                      </div>
                      <div className="flex items-center gap-0.5 mb-1">
                        {renderStars(review.rating, "w-3 h-3")}
                      </div>
                      <p className="text-xs leading-relaxed opacity-70">
                        {review.comment}
                      </p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
