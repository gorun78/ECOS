/**
 * MarketplaceHeader — header, stat cards, search/filter bar
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React from "react";
import {
  Store,
  Search,
  Plus,
  Star,
  Clock,
  Layers,
  Filter,
  ChevronRight,
} from "lucide-react";
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";
import type { MarketplaceDashboard } from "../../api";

// ── Constants ──────────────────────────────────────────────

export const CATEGORIES = [
  { value: "", labelZh: "全部", labelEn: "All" },
  { value: "\u6570\u636e\u96c6", labelZh: "数据集", labelEn: "Dataset" },
  { value: "\u6570\u636e\u670d\u52a1", labelZh: "数据服务", labelEn: "Data Service" },
  { value: "AI\u6a21\u578b", labelZh: "AI模型", labelEn: "AI Model" },
  { value: "API", labelZh: "API", labelEn: "API" },
  { value: "\u62a5\u8868", labelZh: "报表", labelEn: "Report" },
  { value: "\u77e5\u8bc6\u5e93", labelZh: "知识库", labelEn: "Knowledge Base" },
];

// ── Props ──────────────────────────────────────────────────

interface MarketplaceHeaderProps {
  dashboard: MarketplaceDashboard;
  keyword: string;
  category: string;
  onKeywordChange: (v: string) => void;
  onCategoryChange: (v: string) => void;
  onSearchSubmit: (e: React.FormEvent) => void;
  onPublishClick: () => void;
}

// ── Stat Card data helper ──────────────────────────────────

interface StatDef {
  labelZh: string;
  labelEn: string;
  value: string | number;
  icon: React.FC<{ className?: string }>;
  color: string;
  bg: string;
  suffix?: string;
}

// ── Component ──────────────────────────────────────────────

export default function MarketplaceHeader({
  dashboard,
  keyword,
  category,
  onKeywordChange,
  onCategoryChange,
  onSearchSubmit,
  onPublishClick,
}: MarketplaceHeaderProps) {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const tl = (zh: string, en: string) => (locale === "zh" ? zh : en);

  const statDefs: StatDef[] = [
    {
      labelZh: "资产总数",
      labelEn: "Total Assets",
      value: dashboard.totalAssets,
      icon: Layers,
      color: "text-indigo-400",
      bg: "bg-indigo-500/10",
    },
    {
      labelZh: "平均评分",
      labelEn: "Avg Rating",
      value: dashboard.avgRating.toFixed(1),
      icon: Star,
      color: "text-amber-400",
      bg: "bg-amber-500/10",
      suffix: " / 5",
    },
    {
      labelZh: "待审批",
      labelEn: "Pending",
      value: dashboard.pendingRequests,
      icon: Clock,
      color: "text-orange-400",
      bg: "bg-orange-500/10",
    },
  ];

  return (
    <div
      className={`px-6 lg:px-8 pt-6 pb-4 border-b ${styles.sidebarBorder} shrink-0`}
    >
      {/* Title + Publish button */}
      <div className="flex items-center justify-between mb-4">
        <div>
          <h1
            className={`text-2xl font-bold ${styles.cardText} tracking-tight`}
          >
            {tl("数据市场", "Data Marketplace")}
          </h1>
          <p className="text-sm mt-0.5 opacity-60">
            {tl(
              "浏览、搜索数据资产，发布您的数据产品",
              "Browse, search and publish data assets"
            )}
          </p>
        </div>
        <button
          onClick={onPublishClick}
          className="flex items-center gap-2 px-4 py-2.5 rounded-lg bg-indigo-500 hover:bg-indigo-600
            text-white text-sm font-medium transition-colors duration-150 shadow-lg shadow-indigo-500/20"
        >
          <Plus className="w-4 h-4" />
          {tl("发布资产", "Publish Asset")}
        </button>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-3 gap-3 mb-4">
        {statDefs.map((card, i) => {
          const Icon = card.icon;
          return (
            <div
              key={i}
              className={`${styles.cardBg} border ${styles.sidebarBorder} rounded-lg px-4 py-3
                flex items-center gap-3 hover:border-indigo-500/30 transition-colors duration-150`}
            >
              <div className={`p-2 rounded-lg ${card.bg}`}>
                <Icon className={`w-5 h-5 ${card.color}`} />
              </div>
              <div>
                <p className="text-[11px] opacity-50 font-medium uppercase tracking-wide">
                  {tl(card.labelZh, card.labelEn)}
                </p>
                <p className={`text-xl font-bold ${styles.cardText}`}>
                  {card.value}
                  {card.suffix && (
                    <span className="text-sm font-normal opacity-50">
                      {card.suffix}
                    </span>
                  )}
                </p>
              </div>
            </div>
          );
        })}
      </div>

      {/* Search + Filter */}
      <form onSubmit={onSearchSubmit} className="flex gap-2">
        <div className="relative flex-1 max-w-lg">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 opacity-40" />
          <input
            type="text"
            value={keyword}
            onChange={(e) => onKeywordChange(e.target.value)}
            placeholder={tl(
              "搜索资产名称、描述、标签…",
              "Search by name, description, tags..."
            )}
            className={`w-full pl-9 pr-3 py-2.5 rounded-lg border ${styles.sidebarBorder}
              ${styles.cardBg} ${styles.cardText} text-sm
              focus:outline-none focus:ring-2 focus:ring-indigo-500/40 focus:border-indigo-500/40
              placeholder:opacity-40 transition-all duration-150`}
          />
        </div>
        <div className="relative">
          <Filter className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 opacity-40 pointer-events-none z-10" />
          <select
            value={category}
            onChange={(e) => onCategoryChange(e.target.value)}
            className={`appearance-none pl-9 pr-8 py-2.5 rounded-lg border ${styles.sidebarBorder}
              ${styles.cardBg} ${styles.cardText} text-sm cursor-pointer
              focus:outline-none focus:ring-2 focus:ring-indigo-500/40 transition-all duration-150`}
          >
            {CATEGORIES.map((c) => (
              <option key={c.value} value={c.value}>
                {tl(c.labelZh, c.labelEn)}
              </option>
            ))}
          </select>
          <ChevronRight className="absolute right-2 top-1/2 -translate-y-1/2 w-3.5 h-3.5 opacity-40 rotate-90 pointer-events-none" />
        </div>
        <button
          type="submit"
          className="px-4 py-2.5 rounded-lg bg-indigo-500 hover:bg-indigo-600 text-white text-sm font-medium
            transition-colors duration-150 flex items-center gap-1.5"
        >
          <Search className="w-4 h-4" />
          {tl("搜索", "Search")}
        </button>
      </form>
    </div>
  );
}
