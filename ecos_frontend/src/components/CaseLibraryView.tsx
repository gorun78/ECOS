/**
 * P2-5 CaseLibrary — 自学习案例库：列表+搜索+详情
 * Connected to: /cases/search, /cases, /cases/{id}, /cases/record
 */
import React, { useState, useEffect, useCallback } from "react";
import { Search, BookOpen, Plus, RefreshCw, Tag, Clock, AlertCircle, CheckCircle2, XCircle } from "lucide-react";
import { useLanguage } from "../pages/../components/LanguageContext";
import { useTheme } from "../pages/../components/ThemeContext";

const API_BASE = "/cases";

interface CaseItem {
  id: number;
  title: string;
  scenario: string;
  tags: string[];
  feedback: string;
  source: string;
  created_at: string;
  relevance?: number;
  decision?: string;
  result?: string;
}

// ── Main Component ─────────────────────────────
export default function CaseLibraryView() {
  const { locale } = useLanguage();
  const { styles } = useTheme();

  const [cases, setCases] = useState<CaseItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [query, setQuery] = useState("");
  const [detail, setDetail] = useState<CaseItem | null>(null);
  const [showNew, setShowNew] = useState(false);
  const [newForm, setNewForm] = useState({ title: "", scenario: "", tags: "", source: "manual" });

  const fetchCases = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const resp = await fetch(`${API_BASE}`);
      const data = await resp.json();
      setCases(data?.data || []);
    } catch (e: any) {
      setError("加载失败: " + (e?.message || "Unknown"));
    } finally {
      setLoading(false);
    }
  }, []);

  const doSearch = useCallback(async () => {
    if (!query.trim()) { fetchCases(); return; }
    setLoading(true);
    try {
      const resp = await fetch(`${API_BASE}/search?q=${encodeURIComponent(query)}&k=10`);
      const data = await resp.json();
      setCases(data?.data?.results || []);
    } catch (e: any) {
      setError("搜索失败: " + (e?.message || "Unknown"));
    } finally {
      setLoading(false);
    }
  }, [query, fetchCases]);

  const viewDetail = useCallback(async (id: number) => {
    try {
      const resp = await fetch(`${API_BASE}/${id}`);
      const data = await resp.json();
      setDetail(data?.data || null);
    } catch {
      setDetail(null);
    }
  }, []);

  const recordCase = useCallback(async () => {
    try {
      const tags = newForm.tags.split(/[,，]/).map(t => t.trim()).filter(Boolean);
      const resp = await fetch(`${API_BASE}/record`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...newForm, tags }),
      });
      await resp.json();
      setShowNew(false);
      setNewForm({ title: "", scenario: "", tags: "", source: "manual" });
      fetchCases();
    } catch (e: any) {
      setError("记录失败: " + (e?.message || "Unknown"));
    }
  }, [newForm, fetchCases]);

  useEffect(() => { fetchCases(); }, [fetchCases]);

  const feedbackIcon = (fb: string) => {
    if (fb === "positive") return <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />;
    if (fb === "negative") return <XCircle className="w-3.5 h-3.5 text-red-400" />;
    return <Clock className="w-3.5 h-3.5 text-amber-400" />;
  };

  return (
    <div className="flex-1 flex flex-col min-h-0">
      {/* Toolbar */}
      <div className="flex items-center gap-2 mb-3 shrink-0 flex-wrap">
        <div className="flex items-center gap-1.5 flex-1 min-w-[200px]">
          <Search className="w-4 h-4 opacity-50 shrink-0" />
          <input
            className={`flex-1 px-3 py-1.5 rounded border text-xs outline-none ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
            placeholder={locale === "zh" ? "搜索案例..." : "Search cases..."}
            value={query}
            onChange={e => setQuery(e.target.value)}
            onKeyDown={e => e.key === "Enter" && doSearch()}
          />
        </div>
        <button onClick={doSearch}
          className={`px-3 py-1.5 rounded text-xs font-medium transition-all ${styles.accentBg} ${styles.accentHover} text-white`}>
          {locale === "zh" ? "搜索" : "Search"}
        </button>
        <button onClick={() => setShowNew(true)}
          className={`flex items-center gap-1 px-3 py-1.5 rounded text-xs font-medium border transition-all ${styles.cardBorder} ${styles.cardBg} hover:opacity-80`}>
          <Plus className="w-3.5 h-3.5" />
          {locale === "zh" ? "新建" : "New"}
        </button>
        <button onClick={fetchCases} disabled={loading}
          className={`p-1.5 rounded border transition-all ${styles.cardBorder} hover:opacity-80 disabled:opacity-30`}>
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
        </button>
      </div>

      {error && (
        <div className="mb-3 px-3 py-2 rounded text-xs bg-red-500/10 border border-red-500/30 text-red-400 flex items-center gap-2 shrink-0">
          <AlertCircle className="w-3.5 h-3.5" />{error}
        </div>
      )}

      {/* New Case Modal */}
      {showNew && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className={`w-full max-w-md mx-4 rounded-lg p-5 ${styles.cardBg} border ${styles.cardBorder}`}>
            <h3 className="text-sm font-semibold mb-3">{locale === "zh" ? "新建案例" : "New Case"}</h3>
            <input className={`w-full px-3 py-1.5 rounded border text-xs mb-2 outline-none ${styles.inputBg} ${styles.inputBorder}`}
              placeholder={locale === "zh" ? "标题" : "Title"} value={newForm.title}
              onChange={e => setNewForm({ ...newForm, title: e.target.value })} />
            <textarea className={`w-full px-3 py-1.5 rounded border text-xs mb-2 outline-none resize-none ${styles.inputBg} ${styles.inputBorder}`}
              rows={3} placeholder={locale === "zh" ? "情景描述" : "Scenario"}
              value={newForm.scenario} onChange={e => setNewForm({ ...newForm, scenario: e.target.value })} />
            <input className={`w-full px-3 py-1.5 rounded border text-xs mb-3 outline-none ${styles.inputBg} ${styles.inputBorder}`}
              placeholder={locale === "zh" ? "标签 (逗号分隔)" : "Tags (comma separated)"}
              value={newForm.tags} onChange={e => setNewForm({ ...newForm, tags: e.target.value })} />
            <div className="flex gap-2">
              <button onClick={recordCase}
                className={`flex-1 px-4 py-1.5 rounded text-xs font-medium ${styles.accentBg} ${styles.accentHover} text-white`}>
                {locale === "zh" ? "保存" : "Save"}
              </button>
              <button onClick={() => setShowNew(false)}
                className={`flex-1 px-4 py-1.5 rounded border text-xs ${styles.cardBorder}`}>
                {locale === "zh" ? "取消" : "Cancel"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Detail Modal */}
      {detail && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className={`w-full max-w-lg mx-4 rounded-lg p-5 max-h-[80vh] overflow-auto ${styles.cardBg} border ${styles.cardBorder}`}>
            <h3 className="text-sm font-semibold mb-2">{detail.title}</h3>
            <p className="text-xs opacity-70 mb-2">{detail.scenario}</p>
            <div className="flex flex-wrap gap-1 mb-2">
              {(detail.tags || []).map((t, i) => (
                <span key={i} className="text-[10px] px-1.5 py-0.5 rounded bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                  {t}
                </span>
              ))}
            </div>
            <div className="grid grid-cols-2 gap-2 text-[10px] opacity-70 mb-2">
              <span>{locale === "zh" ? "来源" : "Source"}: {detail.source}</span>
              <span className="flex items-center gap-1">{locale === "zh" ? "反馈" : "Feedback"}: {feedbackIcon(detail.feedback)} {detail.feedback}</span>
              <span>{locale === "zh" ? "时间" : "Time"}: {detail.created_at?.slice(0, 16)}</span>
              {detail.relevance !== undefined && <span>Relevance: {detail.relevance}</span>}
            </div>
            {detail.decision && (
              <details className="mb-1">
                <summary className="text-xs font-medium cursor-pointer opacity-80">{locale === "zh" ? "决策" : "Decision"}</summary>
                <pre className="text-[10px] mt-1 p-2 rounded bg-black/20 overflow-auto max-h-32">{detail.decision}</pre>
              </details>
            )}
            {detail.result && (
              <details>
                <summary className="text-xs font-medium cursor-pointer opacity-80">{locale === "zh" ? "结果" : "Result"}</summary>
                <pre className="text-[10px] mt-1 p-2 rounded bg-black/20 overflow-auto max-h-32">{detail.result}</pre>
              </details>
            )}
            <button onClick={() => setDetail(null)}
              className={`mt-3 w-full px-4 py-1.5 rounded border text-xs ${styles.cardBorder}`}>
              {locale === "zh" ? "关闭" : "Close"}
            </button>
          </div>
        </div>
      )}

      {/* Case List */}
      <div className="flex-1 overflow-auto">
        {cases.length === 0 && !loading ? (
          <div className="flex items-center justify-center h-full">
            <div className="text-center opacity-50">
              <BookOpen className="w-10 h-10 mx-auto mb-2" />
              <p className="text-sm">{locale === "zh" ? "暂无案例" : "No cases yet"}</p>
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
            {cases.map((c) => (
              <div key={c.id}
                onClick={() => viewDetail(c.id)}
                className={`p-3 rounded-lg border cursor-pointer transition-all hover:border-indigo-500/50 ${styles.cardBorder} ${styles.cardBg}`}>
                <div className="flex items-start gap-2">
                  {feedbackIcon(c.feedback)}
                  <div className="flex-1 min-w-0">
                    <h4 className="text-xs font-semibold truncate">
                      {c.relevance !== undefined && <span className="text-[10px] text-amber-400 mr-1">★{c.relevance}</span>}
                      {c.title}
                    </h4>
                    <p className="text-[10px] opacity-60 mt-0.5 line-clamp-2">{c.scenario}</p>
                    <div className="flex flex-wrap gap-1 mt-1.5">
                      {(c.tags || []).slice(0, 3).map((t, i) => (
                        <span key={i} className="text-[9px] px-1 py-0.5 rounded bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                          {t}
                        </span>
                      ))}
                      {c.tags?.length > 3 && <span className="text-[9px] opacity-50">+{c.tags.length - 3}</span>}
                    </div>
                  </div>
                </div>
                <div className="flex justify-between mt-1.5 text-[9px] opacity-40">
                  <span>{c.source}</span>
                  <span>{c.created_at?.slice(0, 10)}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
