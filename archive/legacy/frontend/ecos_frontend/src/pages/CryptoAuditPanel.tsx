/**
 * CryptoAuditPanel — 密码审计面板
 * ECOS Phase 1 P1-2.4: Cryptographic audit logs + chain verification
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback } from "react";
import { Shield, CheckCircle, XCircle, Search, RefreshCw, Plus, AlertTriangle } from "lucide-react";
import { fetchCryptAuditLogs, fetchCryptAuditVerify, postCryptAuditRecord } from "../api";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";

interface CryptAuditLog {
  id: number;
  eventType: string;
  resource: string;
  action: string;
  operatorId: string;
  payload?: string;
  hash: string;
  previousHash: string;
  timestamp: string;
  verified: boolean;
}

interface VerifyResult {
  intact: boolean;
  totalBlocks: number;
  tamperedBlocks: number[];
  message: string;
}

export default function CryptoAuditPanel() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();

  const [activeTab, setActiveTab] = useState<"logs" | "verify">("logs");

  // ── Tab 1: Audit Logs ──
  const [logs, setLogs] = useState<CryptAuditLog[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(20);
  const [keyword, setKeyword] = useState("");
  const [searchInput, setSearchInput] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // ── Tab 2: Chain Verification ──
  const [verifyResult, setVerifyResult] = useState<VerifyResult | null>(null);
  const [verifying, setVerifying] = useState(false);
  const [verifyError, setVerifyError] = useState<string | null>(null);

  // ── Test record ──
  const [posting, setPosting] = useState(false);
  const [postMsg, setPostMsg] = useState<string | null>(null);

  // Load logs
  const loadLogs = useCallback(async (p: number, kw: string) => {
    setLoading(true);
    setError(null);
    try {
      const res = await fetchCryptAuditLogs(kw || undefined, p, pageSize);
      setLogs(res.data || []);
      setTotal(res.total || 0);
    } catch (e: any) {
      setError(e.message || "加载审计日志失败");
      setLogs([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [pageSize]);

  useEffect(() => {
    loadLogs(page, keyword);
  }, [page, keyword, loadLogs]);

  const handleSearch = () => {
    setKeyword(searchInput);
    setPage(1);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") handleSearch();
  };

  // ── Verify chain ──
  const handleVerify = async () => {
    setVerifying(true);
    setVerifyError(null);
    setVerifyResult(null);
    try {
      const res = await fetchCryptAuditVerify();
      setVerifyResult(res);
    } catch (e: any) {
      setVerifyError(e.message || "链验证请求失败");
    } finally {
      setVerifying(false);
    }
  };

  // ── Post test record ──
  const handlePostTest = async () => {
    setPosting(true);
    setPostMsg(null);
    try {
      await postCryptAuditRecord({
        eventType: "CRYPTO_TEST",
        resource: "test-chain",
        action: "VERIFY_INTEGRITY",
        operatorId: "demo-operator",
        payload: JSON.stringify({ test: true, ts: Date.now() }),
      });
      setPostMsg(locale === "zh" ? "测试记录已提交成功！" : "Test record posted successfully!");
      // Reload logs
      loadLogs(page, keyword);
    } catch (e: any) {
      setPostMsg(locale === "zh" ? `提交失败: ${e.message}` : `Failed: ${e.message}`);
    } finally {
      setPosting(false);
    }
  };

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  const getEventTypeBadge = (eventType: string) => {
    const colors: Record<string, string> = {
      "CRYPTO_VERIFY": "bg-blue-500/10 text-blue-400 border-blue-500/30",
      "CRYPTO_RECORD": "bg-emerald-500/10 text-emerald-400 border-emerald-500/30",
      "CRYPTO_AUDIT": "bg-violet-500/10 text-violet-400 border-violet-500/30",
      "CRYPTO_TEST": "bg-amber-500/10 text-amber-400 border-amber-500/30",
    };
    const c = colors[eventType] || "bg-gray-500/10 text-gray-400 border-gray-500/30";
    return (
      <span className={`text-[10px] font-mono px-2 py-0.5 rounded border ${c}`}>
        {eventType}
      </span>
    );
  };

  return (
    <div className={`flex-1 overflow-y-auto ${styles.appBg} p-5 ${styles.appText} flex flex-col h-full font-sans`}>
      {/* Header */}
      <div className="flex justify-between items-center mb-5 shrink-0">
        <div>
          <h1 className="text-xl font-bold tracking-tight flex items-center gap-2">
            <Shield className="text-indigo-500 w-5 h-5 shrink-0" />
            {locale === "zh" ? "密码审计" : "Crypto Audit"}
          </h1>
          <p className={`text-xs mt-0.5 ${styles.cardTextMuted}`}>
            {locale === "zh"
              ? "基于哈希链的密码学审计日志与完整性验证"
              : "Hash-chain based cryptographic audit logs & integrity verification"}
          </p>
        </div>

        {/* Post test record button */}
        <button
          onClick={handlePostTest}
          disabled={posting}
          className={`flex items-center gap-1.5 px-3 py-1.5 rounded text-xs font-medium transition-all duration-150
            ${styles.accentBg} ${styles.accentHover} text-white disabled:opacity-50 disabled:cursor-not-allowed`}
        >
          <Plus className="w-3.5 h-3.5" />
          {posting
            ? (locale === "zh" ? "提交中..." : "Posting...")
            : (locale === "zh" ? "记录测试" : "Test Record")}
        </button>
      </div>

      {/* Post message */}
      {postMsg && (
        <div className={`mb-4 px-3 py-2 rounded text-xs flex items-center gap-2 ${
          postMsg.includes("成功") || postMsg.includes("success")
            ? "bg-emerald-500/10 border border-emerald-500/30 text-emerald-400"
            : "bg-red-500/10 border border-red-500/30 text-red-400"
        }`}>
          {postMsg.includes("成功") || postMsg.includes("success")
            ? <CheckCircle className="w-4 h-4 shrink-0" />
            : <AlertTriangle className="w-4 h-4 shrink-0" />}
          {postMsg}
        </div>
      )}

      {/* Tabs */}
      <div className={`flex gap-0 border-b mb-5 shrink-0 ${styles.sidebarBorder}`}>
        <button
          onClick={() => setActiveTab("logs")}
          className={`px-4 py-2 text-sm font-medium transition-all duration-150 border-b-2 -mb-[1px] ${
            activeTab === "logs"
              ? "border-indigo-500 text-indigo-400"
              : `border-transparent ${styles.cardTextMuted} hover:text-gray-300`
          }`}
        >
          {locale === "zh" ? "审计日志" : "Audit Logs"}
        </button>
        <button
          onClick={() => setActiveTab("verify")}
          className={`px-4 py-2 text-sm font-medium transition-all duration-150 border-b-2 -mb-[1px] ${
            activeTab === "verify"
              ? "border-indigo-500 text-indigo-400"
              : `border-transparent ${styles.cardTextMuted} hover:text-gray-300`
          }`}
        >
          {locale === "zh" ? "链验证" : "Chain Verification"}
        </button>
      </div>

      {/* ── Tab 1: Audit Logs ── */}
      {activeTab === "logs" && (
        <div className="flex-1 flex flex-col min-h-0">
          {/* Search bar */}
          <div className="flex items-center gap-2 mb-4 shrink-0">
            <div className={`flex items-center gap-2 flex-1 max-w-md rounded border px-3 py-1.5 ${styles.cardBorder} ${styles.cardBg}`}>
              <Search className="w-3.5 h-3.5 opacity-50 shrink-0" />
              <input
                type="text"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={locale === "zh" ? "搜索事件类型、操作、操作人..." : "Search eventType, action, operator..."}
                className={`flex-1 bg-transparent text-xs outline-none placeholder:opacity-40 ${styles.cardText}`}
              />
              {searchInput && (
                <button onClick={() => { setSearchInput(""); setKeyword(""); setPage(1); }} className="opacity-50 hover:opacity-100">
                  <XCircle className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
            <button
              onClick={handleSearch}
              className={`px-3 py-1.5 rounded text-xs font-medium ${styles.accentBg} ${styles.accentHover} text-white`}
            >
              {locale === "zh" ? "搜索" : "Search"}
            </button>
            <button
              onClick={() => loadLogs(page, keyword)}
              disabled={loading}
              className={`p-1.5 rounded border ${styles.cardBorder} ${styles.cardBg} hover:opacity-80 disabled:opacity-40`}
              title={locale === "zh" ? "刷新" : "Refresh"}
            >
              <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
            </button>
          </div>

          {/* Error */}
          {error && (
            <div className="mb-4 px-3 py-2 rounded text-xs bg-red-500/10 border border-red-500/30 text-red-400 flex items-center gap-2">
              <AlertTriangle className="w-4 h-4 shrink-0" />
              {error}
            </div>
          )}

          {/* Table */}
          <div className={`flex-1 overflow-auto border rounded ${styles.cardBorder} ${styles.cardBg}`}>
            <table className="w-full text-xs">
              <thead>
                <tr className={`border-b ${styles.cardBorder} text-left`}>
                  <th className="px-3 py-2.5 font-mono text-[10px] uppercase tracking-wider opacity-60 w-16">ID</th>
                  <th className="px-3 py-2.5 font-mono text-[10px] uppercase tracking-wider opacity-60">Event Type</th>
                  <th className="px-3 py-2.5 font-mono text-[10px] uppercase tracking-wider opacity-60">Action</th>
                  <th className="px-3 py-2.5 font-mono text-[10px] uppercase tracking-wider opacity-60">Operator</th>
                  <th className="px-3 py-2.5 font-mono text-[10px] uppercase tracking-wider opacity-60">Timestamp</th>
                  <th className="px-3 py-2.5 font-mono text-[10px] uppercase tracking-wider opacity-60 w-20 text-center">Verified</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-800/50">
                {loading && logs.length === 0 ? (
                  Array.from({ length: 5 }).map((_, i) => (
                    <tr key={i} className="animate-pulse">
                      {Array.from({ length: 6 }).map((_, j) => (
                        <td key={j} className="px-3 py-3"><div className="h-3 bg-gray-700/30 rounded w-3/4" /></td>
                      ))}
                    </tr>
                  ))
                ) : logs.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-3 py-12 text-center opacity-50">
                      {locale === "zh" ? "暂无审计日志" : "No audit logs found"}
                    </td>
                  </tr>
                ) : (
                  logs.map((log) => (
                    <tr key={log.id} className="hover:bg-white/[0.02] transition-colors">
                      <td className="px-3 py-2.5 font-mono text-[11px] opacity-60">{log.id}</td>
                      <td className="px-3 py-2.5">{getEventTypeBadge(log.eventType)}</td>
                      <td className="px-3 py-2.5 font-medium">{log.action}</td>
                      <td className="px-3 py-2.5 font-mono text-[11px] opacity-80">{log.operatorId}</td>
                      <td className="px-3 py-2.5 font-mono text-[11px] opacity-60">{log.timestamp}</td>
                      <td className="px-3 py-2.5 text-center">
                        {log.verified ? (
                          <span className="inline-flex items-center gap-1 text-emerald-400" title="Verified">
                            <CheckCircle className="w-3.5 h-3.5" />
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1 text-red-400" title="Not Verified">
                            <XCircle className="w-3.5 h-3.5" />
                          </span>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {total > 0 && (
            <div className="flex items-center justify-between mt-3 shrink-0 text-xs">
              <span className="opacity-50">
                {locale === "zh"
                  ? `共 ${total} 条记录，第 ${page}/${totalPages} 页`
                  : `Total ${total} records, page ${page}/${totalPages}`}
              </span>
              <div className="flex items-center gap-1">
                <button
                  onClick={() => setPage((p) => Math.max(1, p - 1))}
                  disabled={page <= 1}
                  className={`px-2.5 py-1 rounded border text-xs font-mono transition-all disabled:opacity-30 disabled:cursor-not-allowed
                    ${styles.cardBorder} ${styles.cardBg} hover:opacity-80`}
                >
                  ←
                </button>
                {Array.from({ length: Math.min(5, totalPages) }).map((_, i) => {
                  let pageNum: number;
                  if (totalPages <= 5) {
                    pageNum = i + 1;
                  } else if (page <= 3) {
                    pageNum = i + 1;
                  } else if (page >= totalPages - 2) {
                    pageNum = totalPages - 4 + i;
                  } else {
                    pageNum = page - 2 + i;
                  }
                  return (
                    <button
                      key={pageNum}
                      onClick={() => setPage(pageNum)}
                      className={`px-2.5 py-1 rounded text-xs font-mono transition-all
                        ${pageNum === page
                          ? `${styles.accentBg} text-white`
                          : `${styles.cardBorder} border ${styles.cardBg} hover:opacity-80`
                        }`}
                    >
                      {pageNum}
                    </button>
                  );
                })}
                <button
                  onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                  disabled={page >= totalPages}
                  className={`px-2.5 py-1 rounded border text-xs font-mono transition-all disabled:opacity-30 disabled:cursor-not-allowed
                    ${styles.cardBorder} ${styles.cardBg} hover:opacity-80`}
                >
                  →
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ── Tab 2: Chain Verification ── */}
      {activeTab === "verify" && (
        <div className="flex-1 flex flex-col items-center justify-center min-h-0">
          <div className={`max-w-md w-full border rounded-lg p-6 ${styles.cardBorder} ${styles.cardBg}`}>
            <h3 className="text-sm font-bold mb-2 flex items-center gap-2">
              <Shield className="w-4 h-4 text-indigo-500" />
              {locale === "zh" ? "哈希链完整性验证" : "Hash Chain Integrity Verification"}
            </h3>
            <p className={`text-xs mb-5 ${styles.cardTextMuted}`}>
              {locale === "zh"
                ? "验证整个密码审计链的哈希完整性，检测是否存在被篡改的记录。"
                : "Verify the hash integrity of the entire crypto audit chain and detect any tampered records."}
            </p>

            <button
              onClick={handleVerify}
              disabled={verifying}
              className={`w-full flex items-center justify-center gap-2 px-4 py-2.5 rounded text-sm font-medium transition-all duration-150
                ${styles.accentBg} ${styles.accentHover} text-white disabled:opacity-50 disabled:cursor-not-allowed`}
            >
              <RefreshCw className={`w-4 h-4 ${verifying ? "animate-spin" : ""}`} />
              {verifying
                ? (locale === "zh" ? "验证中..." : "Verifying...")
                : (locale === "zh" ? "开始验证" : "Start Verification")}
            </button>

            {/* Verify Error */}
            {verifyError && (
              <div className="mt-4 px-3 py-2 rounded text-xs bg-red-500/10 border border-red-500/30 text-red-400 flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0" />
                {verifyError}
              </div>
            )}

            {/* Verify Result */}
            {verifyResult && (
              <div className={`mt-4 border rounded p-4 ${
                verifyResult.intact
                  ? "border-emerald-500/30 bg-emerald-500/5"
                  : "border-red-500/30 bg-red-500/5"
              }`}>
                {/* Status */}
                <div className="flex items-center gap-2 mb-3">
                  {verifyResult.intact ? (
                    <>
                      <CheckCircle className="w-5 h-5 text-emerald-400" />
                      <span className="text-sm font-bold text-emerald-400">
                        {locale === "zh" ? "链完整" : "Chain Intact"}
                      </span>
                    </>
                  ) : (
                    <>
                      <XCircle className="w-5 h-5 text-red-400" />
                      <span className="text-sm font-bold text-red-400">
                        {locale === "zh" ? "链被篡改" : "Chain Tampered"}
                      </span>
                    </>
                  )}
                </div>

                {/* Summary */}
                <div className={`text-xs space-y-1.5 ${styles.cardTextMuted}`}>
                  <div className="flex justify-between">
                    <span>{locale === "zh" ? "总区块数" : "Total Blocks"}:</span>
                    <span className="font-mono font-medium">{verifyResult.totalBlocks}</span>
                  </div>
                  {!verifyResult.intact && verifyResult.tamperedBlocks && verifyResult.tamperedBlocks.length > 0 && (
                    <div>
                      <span className="text-red-400">
                        {locale === "zh" ? "被篡改区块" : "Tampered Blocks"}:
                      </span>
                      <div className="flex flex-wrap gap-1 mt-1">
                        {verifyResult.tamperedBlocks.map((bid) => (
                          <span key={bid} className="px-2 py-0.5 rounded text-[10px] font-mono bg-red-500/15 text-red-400 border border-red-500/30">
                            #{bid}
                          </span>
                        ))}
                      </div>
                    </div>
                  )}
                  {verifyResult.message && (
                    <div className={`mt-2 pt-2 border-t ${styles.cardBorder} text-[11px]`}>
                      {verifyResult.message}
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
