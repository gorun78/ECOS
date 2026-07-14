/**
 * MarketplaceCardGrid — asset card grid with loading/empty states
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React from "react";
import {
  Store,
  Star,
  Eye,
  Clock,
  User,
  Tag,
  MessageSquare,
  RefreshCw,
  Database,
  Globe,
  Cpu,
  FileText,
  Layers,
} from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";
import { useDict } from "../../hooks/useDict";
import type { MarketplaceBrowserAsset } from "../../api";

// ── Type icon map ──────────────────────────────────────────

export const TYPE_ICONS: Record<string, React.FC<{ className?: string }>> = {
  "\u6570\u636e\u96c6": Database,
  "\u6570\u636e\u670d\u52a1": Globe,
  "AI\u6a21\u578b": Cpu,
  API: Globe,
  "\u62a5\u8868": FileText,
  "\u77e5\u8bc6\u5e93": Layers,
};

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

interface MarketplaceCardGridProps {
  assets: MarketplaceBrowserAsset[];
  total: number;
  loading: boolean;
  onAssetClick: (asset: MarketplaceBrowserAsset) => void;
}

// ── Component ──────────────────────────────────────────────

export default function MarketplaceCardGrid({
  assets,
  total,
  loading,
  onAssetClick,
}: MarketplaceCardGridProps) {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const { getLabel, getColor } = useDict("asset_status", locale);
  const tl = (zh: string, en: string) => (locale === "zh" ? zh : en);

  return (
    <div className="flex-1 overflow-auto px-6 lg:px-8 py-5">
      {loading ? (
        <div className="flex items-center justify-center py-20 opacity-50">
          <RefreshCw className="animate-spin w-6 h-6 mr-3" />
          {tl("加载中…", "Loading...")}
        </div>
      ) : assets.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 opacity-50 gap-3">
          <Store className="w-12 h-12" />
          <span className="text-sm">
            {tl("暂无数据资产，试试发布第一个吧", "No assets found. Publish the first one!")}
          </span>
        </div>
      ) : (
        <>
          <p className="text-xs opacity-50 mb-3">
            {tl(`共 ${total} 个资产`, `${total} assets total`)}
          </p>
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
            {assets.map((asset) => {
              const TypeIcon = TYPE_ICONS[asset.category] || Database;
              const statusLabel = getLabel(asset.status, "published");
              const statusColor = getColor(asset.status, "published");
              return (
                <div
                  key={asset.id}
                  onClick={() => onAssetClick(asset)}
                  className={`${styles.cardBg} border ${styles.sidebarBorder} rounded-lg p-5
                    hover:shadow-lg hover:border-indigo-500/40 transition-all duration-200
                    cursor-pointer group flex flex-col`}
                >
                  {/* Header */}
                  <div className="flex items-start gap-3 mb-3">
                    <div
                      className={`p-2 rounded-lg ${styles.sidebarHoverBg} shrink-0 group-hover:bg-indigo-500/10 transition-colors`}
                    >
                      <TypeIcon className="w-5 h-5 text-indigo-500" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <h3
                        className={`font-semibold text-[15px] ${styles.cardText} truncate leading-snug group-hover:text-indigo-400 transition-colors`}
                      >
                        {asset.name}
                      </h3>
                      <div className="flex items-center gap-2 mt-1">
                        <span className="text-[10px] font-mono uppercase tracking-wider px-1.5 py-0.5 rounded bg-slate-700/50 opacity-70">
                          {asset.category}
                        </span>
                        <span
                          className="text-[10px] px-1.5 py-0.5 rounded border"
                          style={{ color: statusColor, borderColor: statusColor }}
                        >
                          {statusLabel}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Description */}
                  <p className="text-xs leading-relaxed opacity-60 line-clamp-2 mb-3">
                    {asset.description || tl("暂无描述", "No description")}
                  </p>

                  {/* Meta info */}
                  <div className="flex items-center gap-4 text-[11px] opacity-50 mb-2.5">
                    <span className="flex items-center gap-1">
                      <User className="w-3 h-3" />
                      {asset.owner}
                    </span>
                    <span className="flex items-center gap-1">
                      <Clock className="w-3 h-3" />
                      {formatDate(asset.createdAt)}
                    </span>
                  </div>

                  {/* Rating & popularity */}
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center gap-1">
                      <div className="flex items-center gap-0.5">
                        {renderStars(asset.rating)}
                      </div>
                      <span className="text-[11px] opacity-60 ml-1">
                        {asset.rating.toFixed(1)}
                      </span>
                    </div>
                    <div className="flex items-center gap-3 text-[10px] opacity-45">
                      <span className="flex items-center gap-1">
                        <Eye className="w-3 h-3" />
                        {fmtNum(asset.popularity)}
                      </span>
                      {asset.reviews && (
                        <span className="flex items-center gap-1">
                          <MessageSquare className="w-3 h-3" />
                          {asset.reviews.length}
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Tags */}
                  {asset.tags && asset.tags.length > 0 && (
                    <div className="flex flex-wrap gap-1.5">
                      <Tag className="w-3 h-3 opacity-40 mt-0.5 shrink-0" />
                      {asset.tags.slice(0, 4).map((tag) => (
                        <span
                          key={tag}
                          className="text-[10px] px-2 py-0.5 rounded-full bg-slate-700/40 opacity-70"
                        >
                          {tag}
                        </span>
                      ))}
                      {asset.tags.length > 4 && (
                        <span className="text-[10px] opacity-40">
                          +{asset.tags.length - 4}
                        </span>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}
