/**
 * MarketplaceBrowser — ECOS P1-4 数据市场浏览器
 * 浏览资产卡片、搜索/筛选、详情面板、发布资产、申请访问
 *
 * API:
 *   GET  /api/marketplace/assets        — 资产列表（支持 keyword / category）
 *   GET  /api/marketplace/assets/{id}   — 资产详情（含 reviews）
 *   GET  /api/marketplace/dashboard     — 统计仪表盘
 *   POST /api/marketplace/assets        — 发布新资产
 *   GET  /api/marketplace/search        — 搜索资产
 *   POST /api/marketplace/request-access — 申请访问
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
import { X, CheckCircle2, AlertCircle } from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";
import {
  fetchMarketAssets,
  fetchMarketDashboard,
  fetchMarketAssetDetail,
  publishMarketAsset,
  searchMarketAssets,
  requestAccess,
  type MarketplaceBrowserAsset,
  type MarketplaceDashboard,
} from "../api";

import MarketplaceHeader from "./marketplace/MarketplaceHeader";
import MarketplaceCardGrid from "./marketplace/MarketplaceCardGrid";
import MarketplaceDetailPanel from "./marketplace/MarketplaceDetailPanel";
import MarketplacePublishModal from "./marketplace/MarketplacePublishModal";
import MarketplaceAccessModal from "./marketplace/MarketplaceAccessModal";

// ── Main Component ─────────────────────────────────────────

export default function MarketplaceBrowser() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const isZh = locale === "zh";
  const tl = (zh: string, en: string) => isZh ? zh : en;

  // State
  const [assets, setAssets] = useState<MarketplaceBrowserAsset[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [dashboard, setDashboard] = useState<MarketplaceDashboard>({
    totalAssets: 0,
    avgRating: 0,
    pendingRequests: 0,
  });

  // Search & filter
  const [keyword, setKeyword] = useState("");
  const [category, setCategory] = useState("");

  // Detail panel
  const [detailAsset, setDetailAsset] = useState<MarketplaceBrowserAsset | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // Publish modal
  const [publishOpen, setPublishOpen] = useState(false);
  const [publishName, setPublishName] = useState("");
  const [publishDesc, setPublishDesc] = useState("");
  const [publishCategory, setPublishCategory] = useState("数据集");
  const [publishTags, setPublishTags] = useState("");
  const [publishing, setPublishing] = useState(false);

  // Access modal
  const [accessAsset, setAccessAsset] = useState<MarketplaceBrowserAsset | null>(null);
  const [accessReason, setAccessReason] = useState("");
  const [accessSubmitting, setAccessSubmitting] = useState(false);

  // Toast
  const [toast, setToast] = useState<{ type: "success" | "error"; msg: string } | null>(null);
  const showToast = useCallback((type: "success" | "error", msg: string) => {
    setToast({ type, msg });
    setTimeout(() => setToast(null), 4000);
  }, []);

  // ── Load dashboard ──────────────────────────────────────
  const loadDashboard = useCallback(async () => {
    try {
      const d = await fetchMarketDashboard();
      setDashboard(d);
    } catch {
      // silent fail, keep defaults
    }
  }, []);

  // ── Load assets ─────────────────────────────────────────
  const loadAssets = useCallback(async (kw?: string, cat?: string) => {
    setLoading(true);
    try {
      const res = await fetchMarketAssets({
        keyword: kw || undefined,
        category: cat || undefined,
        pageSize: 50,
      });
      setAssets(res.data || []);
      setTotal(res.total || res.data?.length || 0);
    } catch {
      setAssets([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadDashboard();
    loadAssets();
  }, [loadDashboard, loadAssets]);

  // ── Search ──────────────────────────────────────────────
  const handleSearch = useCallback(
    async (kw: string, cat: string) => {
      if (!kw.trim() && !cat) {
        loadAssets();
        return;
      }
      setLoading(true);
      try {
        const results = await searchMarketAssets(kw.trim(), cat || undefined);
        setAssets(results);
        setTotal(results.length);
      } catch {
        setAssets([]);
      } finally {
        setLoading(false);
      }
    },
    [loadAssets]
  );

  const onSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    handleSearch(keyword, category);
  };

  const onCategoryChange = (cat: string) => {
    setCategory(cat);
    if (keyword.trim()) {
      handleSearch(keyword, cat);
    } else if (cat) {
      loadAssets(undefined, cat);
    } else {
      loadAssets();
    }
  };

  // ── Asset detail ────────────────────────────────────────
  const openDetail = useCallback(async (asset: MarketplaceBrowserAsset) => {
    setDetailAsset(asset);
    setDetailLoading(true);
    try {
      const detail = await fetchMarketAssetDetail(asset.id);
      setDetailAsset(detail);
    } catch {
      // keep list version as fallback
    } finally {
      setDetailLoading(false);
    }
  }, []);

  const closeDetail = useCallback(() => {
    setDetailAsset(null);
  }, []);

  // ── Publish ─────────────────────────────────────────────
  const resetPublishForm = () => {
    setPublishName("");
    setPublishDesc("");
    setPublishCategory("数据集");
    setPublishTags("");
  };

  const handlePublish = async () => {
    if (!publishName.trim() || !publishDesc.trim()) return;
    setPublishing(true);
    try {
      const result = await publishMarketAsset({
        name: publishName.trim(),
        description: publishDesc.trim(),
        category: publishCategory,
        tags: publishTags
          .split(/[,，]/)
          .map((t) => t.trim())
          .filter(Boolean),
      });
      showToast(
        "success",
        tl(`资产「${result.name}」发布成功！`, `Asset "${result.name}" published!`)
      );
      setPublishOpen(false);
      resetPublishForm();
      loadAssets();
      loadDashboard();
    } catch (err: any) {
      showToast("error", tl(`发布失败：${err.message}`, `Publish failed: ${err.message}`));
    } finally {
      setPublishing(false);
    }
  };

  const handlePublishClose = () => {
    setPublishOpen(false);
    resetPublishForm();
  };

  // ── Access request ──────────────────────────────────────
  const handleAccessRequest = async () => {
    if (!accessAsset || !accessReason.trim()) return;
    setAccessSubmitting(true);
    try {
      const result = await requestAccess(accessAsset.id, accessReason.trim());
      showToast(
        "success",
        tl(
          `申请已提交！编号 ${result.requestId}，状态：${result.status === "PENDING" ? "待审批" : result.status}`,
          `Request submitted! ID: ${result.requestId}, Status: ${result.status}`
        )
      );
      setAccessAsset(null);
      setAccessReason("");
    } catch (err: any) {
      showToast("error", tl(`申请失败：${err.message}`, `Request failed: ${err.message}`));
    } finally {
      setAccessSubmitting(false);
    }
  };

  // ── Render ──────────────────────────────────────────────
  return (
    <div className={`flex-1 flex flex-col overflow-hidden ${styles.appBg}`}>
      {/* Header */}
      <MarketplaceHeader
        dashboard={dashboard}
        keyword={keyword}
        category={category}
        onKeywordChange={setKeyword}
        onCategoryChange={onCategoryChange}
        onSearchSubmit={onSearchSubmit}
        onPublishClick={() => setPublishOpen(true)}
      />

      {/* Card Grid */}
      <MarketplaceCardGrid
        assets={assets}
        total={total}
        loading={loading}
        onAssetClick={openDetail}
      />

      {/* Detail Panel */}
      {detailAsset && (
        <MarketplaceDetailPanel
          asset={detailAsset}
          loading={detailLoading}
          onClose={closeDetail}
          onRequestAccess={(asset) => {
            setAccessAsset(asset);
            setAccessReason("");
          }}
        />
      )}

      {/* Publish Modal */}
      <MarketplacePublishModal
        open={publishOpen}
        name={publishName}
        description={publishDesc}
        category={publishCategory}
        tags={publishTags}
        submitting={publishing}
        onNameChange={setPublishName}
        onDescriptionChange={setPublishDesc}
        onCategoryChange={setPublishCategory}
        onTagsChange={setPublishTags}
        onSubmit={handlePublish}
        onClose={handlePublishClose}
      />

      {/* Access Modal */}
      <MarketplaceAccessModal
        asset={accessAsset}
        reason={accessReason}
        submitting={accessSubmitting}
        onReasonChange={setAccessReason}
        onSubmit={handleAccessRequest}
        onClose={() => setAccessAsset(null)}
      />

      {/* Toast */}
      {toast && (
        <div
          className={`fixed top-6 right-6 z-[60] flex items-center gap-2.5 px-4 py-3 rounded-lg shadow-lg text-sm font-medium
            ${
              toast.type === "success"
                ? "bg-emerald-50 dark:bg-emerald-950 border border-emerald-200 dark:border-emerald-800 text-emerald-800 dark:text-emerald-200"
                : "bg-red-50 dark:bg-red-950 border border-red-200 dark:border-red-800 text-red-800 dark:text-red-200"
            }`}
        >
          {toast.type === "success" ? (
            <CheckCircle2 className="w-4 h-4 shrink-0" />
          ) : (
            <AlertCircle className="w-4 h-4 shrink-0" />
          )}
          <span>{toast.msg}</span>
          <button
            onClick={() => setToast(null)}
            className="ml-2 opacity-60 hover:opacity-100"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      )}
    </div>
  );
}
