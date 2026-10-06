/**
 * Marketplace — 数据市场：浏览数据资产并申请访问。
 * API: GET /api/marketplace/assets (4 Tab 排序), POST /api/marketplace/request-access
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
import {
  Flame,
  Star,
  Clock,
  TrendingUp,
  User,
  Tag,
  Database,
  Cpu,
  Globe,
  FileText,
  Send,
  X,
  CheckCircle2,
  AlertCircle,
} from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import {
  fetchMarketplaceAssets,
  requestMarketplaceAccess,
  type MarketplaceAsset,
} from "../api";

const TABS: { key: string; labelZh: string; labelEn: string; icon: React.FC<{ className?: string }>; sortParam: string }[] = [
  { key: "popular", labelZh: "热门", labelEn: "Popular", icon: Flame, sortParam: "popular" },
  { key: "recommended", labelZh: "推荐", labelEn: "Recommended", icon: Star, sortParam: "recommended" },
  { key: "newest", labelZh: "最新", labelEn: "Newest", icon: Clock, sortParam: "newest" },
  { key: "highvalue", labelZh: "高价值", labelEn: "High Value", icon: TrendingUp, sortParam: "highvalue" },
];

const TYPE_ICONS: Record<string, React.FC<{ className?: string }>> = {
  "数据集": Database,
  "数据服务": Globe,
  "AI模型": Cpu,
  "API": Globe,
  "报表": FileText,
};

function formatDate(iso: string): string {
  try {
    const d = new Date(iso);
    return d.toLocaleDateString("zh-CN", { year: "numeric", month: "short", day: "numeric" });
  } catch {
    return iso.substring(0, 10);
  }
}

function fmtNum(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + "万";
  if (n >= 1000) return (n / 1000).toFixed(1) + "k";
  return String(n);
}

export default function Marketplace() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const isZh = locale === "zh";

  const [activeTab, setActiveTab] = useState("popular");
  const [assets, setAssets] = useState<MarketplaceAsset[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);

  // 申请访问弹窗
  const [modalAsset, setModalAsset] = useState<MarketplaceAsset | null>(null);
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [toast, setToast] = useState<{ type: "success" | "error"; msg: string } | null>(null);

  const loadAssets = useCallback(async (sort: string) => {
    setLoading(true);
    const res = await fetchMarketplaceAssets(sort, 10);
    setAssets(res.items || []);
    setTotal(res.total || 0);
    setLoading(false);
  }, []);

  useEffect(() => {
    loadAssets(activeTab);
  }, [activeTab, loadAssets]);

  const handleTabSwitch = (key: string) => setActiveTab(key);

  const handleRequestAccess = (asset: MarketplaceAsset) => {
    setModalAsset(asset);
    setReason("");
  };

  const handleSubmitRequest = async () => {
    if (!modalAsset || !reason.trim()) return;
    setSubmitting(true);
    try {
      const result = await requestMarketplaceAccess(modalAsset.assetId, reason.trim());
      setToast({
        type: "success",
        msg: isZh
          ? `申请已提交！编号 ${result.requestId}，状态：${result.status === "PENDING" ? "待审批" : result.status}`
          : `Request submitted! ID: ${result.requestId}, Status: ${result.status}`,
      });
    } catch (err: any) {
      setToast({
        type: "error",
        msg: isZh ? `申请失败：${err.message}` : `Request failed: ${err.message}`,
      });
    } finally {
      setSubmitting(false);
      setModalAsset(null);
      setReason("");
      // 3 秒后自动关闭 toast
      setTimeout(() => setToast(null), 4000);
    }
  };

  const activeTabDef = TABS.find(t => t.key === activeTab);

  return (
    <div className={`flex-1 overflow-auto ${styles.appBg}`}>
      {/* ── 页头 ── */}
      <div className={`px-8 pt-8 pb-2 border-b ${styles.sidebarBorder}`}>
        <h1 className={`text-2xl font-bold ${styles.cardText} tracking-tight`}>
          {isZh ? "数据市场" : "Data Marketplace"}
        </h1>
        <p className={`text-sm mt-1 opacity-60`}>
          {isZh
            ? `浏览 ${total} 个数据资产，发现高质量数据，申请访问权限`
            : `Browse ${total} data assets. Discover and request access.`}
        </p>

        {/* Tab 导航 */}
        <div className="flex gap-1 mt-5">
          {TABS.map(tab => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.key;
            return (
              <button
                key={tab.key}
                onClick={() => handleTabSwitch(tab.key)}
                className={`flex items-center gap-1.5 px-4 py-2 text-sm font-medium rounded-t-lg transition-all duration-150
                  ${isActive
                    ? `${styles.sidebarActiveBg} ${styles.sidebarActiveText} border-b-2 -mb-[1px] ${styles.sidebarBorder}`
                    : `opacity-60 hover:opacity-100 ${styles.sidebarHoverBg}`
                  }`}
              >
                <Icon className="w-4 h-4" />
                {isZh ? tab.labelZh : tab.labelEn}
              </button>
            );
          })}
        </div>
      </div>

      {/* ── 资产卡片列表 ── */}
      <div className="px-8 py-6">
        {loading ? (
          <div className="flex items-center justify-center py-20 opacity-50">
            <div className="animate-spin rounded-full h-8 w-8 border-2 border-current border-t-transparent mr-3" />
            {isZh ? "加载中..." : "Loading..."}
          </div>
        ) : assets.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-20 opacity-50 gap-2">
            <Database className="w-10 h-10" />
            <span>{isZh ? "暂无数据资产" : "No assets found"}</span>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {assets.map(asset => {
              const TypeIcon = TYPE_ICONS[asset.type] || Database;
              return (
                <div
                  key={asset.assetId}
                  className={`${styles.cardBg} border ${styles.sidebarBorder} rounded-lg p-5
                    hover:shadow-lg hover:border-indigo-500/40 transition-all duration-200 flex flex-col`}
                >
                  {/* 头部：类型图标 + 名称 + 类型标签 */}
                  <div className="flex items-start gap-3 mb-3">
                    <div className={`p-2 rounded-lg ${styles.sidebarHoverBg} shrink-0`}>
                      <TypeIcon className="w-5 h-5 text-indigo-500" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <h3 className={`font-semibold text-[15px] ${styles.cardText} truncate leading-snug`}>
                        {asset.name}
                      </h3>
                      <span className={`inline-block text-[10px] font-mono uppercase tracking-wider px-1.5 py-0.5 rounded mt-1 ${styles.sidebarHoverBg} opacity-70`}>
                        {asset.type}
                      </span>
                    </div>
                  </div>

                  {/* 描述 */}
                  <p className={`text-xs leading-relaxed opacity-65 line-clamp-2 mb-3`}>
                    {asset.summary}
                  </p>

                  {/* 元信息：owner + 日期 */}
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

                  {/* 标签 */}
                  {asset.tags && asset.tags.length > 0 && (
                    <div className="flex flex-wrap gap-1.5 mb-3">
                      <Tag className="w-3 h-3 opacity-40 mt-0.5 shrink-0" />
                      {asset.tags.map(tag => (
                        <span
                          key={tag}
                          className={`text-[10px] px-2 py-0.5 rounded-full ${styles.sidebarHoverBg} opacity-70`}
                        >
                          {tag}
                        </span>
                      ))}
                    </div>
                  )}

                  {/* 热度指标 */}
                  <div className="flex items-center gap-3 text-[10px] opacity-45 mb-3">
                    <span>🔥 {fmtNum(asset.views)}</span>
                    <span>⭐ {asset.stars}</span>
                    {asset.downloads > 0 && <span>⬇ {fmtNum(asset.downloads)}</span>}
                  </div>

                  {/* 申请按钮 */}
                  <button
                    onClick={() => handleRequestAccess(asset)}
                    className={`mt-auto w-full flex items-center justify-center gap-1.5 py-2 rounded-md text-sm font-medium
                      bg-indigo-500 hover:bg-indigo-600 text-white transition-colors duration-150`}
                  >
                    <Send className="w-3.5 h-3.5" />
                    {isZh ? "申请访问" : "Request Access"}
                  </button>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* ── Toast 通知 ── */}
      {toast && (
        <div className={`fixed top-6 right-6 z-50 flex items-center gap-2.5 px-4 py-3 rounded-lg shadow-lg text-sm font-medium
          ${toast.type === "success"
            ? "bg-emerald-50 dark:bg-emerald-950 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-200"
            : "bg-red-50 dark:bg-red-950 border border-red-200 dark:border-red-800 text-red-800 dark:text-red-200"
          }`}
        >
          {toast.type === "success" ? <CheckCircle2 className="w-4 h-4 shrink-0" /> : <AlertCircle className="w-4 h-4 shrink-0" />}
          <span>{toast.msg}</span>
          <button onClick={() => setToast(null)} className="ml-2 opacity-60 hover:opacity-100">
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      )}

      {/* ── 申请访问弹窗 ── */}
      {modalAsset && (
        <div className="fixed inset-0 z-40 flex items-center justify-center">
          {/* 遮罩 */}
          <div
            className="absolute inset-0 bg-black/40"
            onClick={() => !submitting && setModalAsset(null)}
          />

          {/* 弹窗 */}
          <div className={`relative z-50 w-full max-w-md mx-4 ${styles.cardBg} border ${styles.sidebarBorder} rounded-xl shadow-2xl p-6`}>
            <div className="flex items-center justify-between mb-4">
              <h2 className={`text-lg font-semibold ${styles.cardText}`}>
                {isZh ? "申请访问权限" : "Request Access"}
              </h2>
              <button
                onClick={() => setModalAsset(null)}
                disabled={submitting}
                className="opacity-50 hover:opacity-100 transition-opacity disabled:opacity-30"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* 资产信息 */}
            <div className={`p-3 rounded-lg mb-4 ${styles.sidebarHoverBg}`}>
              <p className={`text-sm font-medium ${styles.cardText}`}>{modalAsset.name}</p>
              <p className="text-xs opacity-60 mt-0.5">
                {modalAsset.type} · {modalAsset.owner}
              </p>
            </div>

            {/* 申请原因 */}
            <label className={`block text-sm font-medium mb-1.5 ${styles.cardText}`}>
              {isZh ? "申请原因" : "Reason"}
            </label>
            <textarea
              value={reason}
              onChange={e => setReason(e.target.value)}
              placeholder={isZh ? "请描述您需要访问此数据资产的原因..." : "Describe why you need access to this asset..."}
              rows={3}
              className={`w-full px-3 py-2.5 rounded-lg border ${styles.sidebarBorder} ${styles.cardBg} ${styles.cardText}
                text-sm resize-none focus:outline-none focus:ring-2 focus:ring-indigo-500/50 focus:border-indigo-500/50
                placeholder:opacity-40`}
              disabled={submitting}
            />

            {/* 按钮 */}
            <div className="flex gap-2.5 mt-4">
              <button
                onClick={() => setModalAsset(null)}
                disabled={submitting}
                className={`flex-1 py-2.5 rounded-lg text-sm font-medium border ${styles.sidebarBorder}
                  ${styles.sidebarHoverBg} transition-colors disabled:opacity-50`}
              >
                {isZh ? "取消" : "Cancel"}
              </button>
              <button
                onClick={handleSubmitRequest}
                disabled={submitting || !reason.trim()}
                className={`flex-1 py-2.5 rounded-lg text-sm font-medium text-white
                  bg-indigo-500 hover:bg-indigo-600 disabled:opacity-40 transition-colors flex items-center justify-center gap-1.5`}
              >
                {submitting ? (
                  <div className="animate-spin rounded-full h-4 w-4 border-2 border-white/30 border-t-white" />
                ) : (
                  <Send className="w-3.5 h-3.5" />
                )}
                {isZh ? "提交申请" : "Submit Request"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
