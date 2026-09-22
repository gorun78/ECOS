// 抽取审核 Tab（PMO-55 批次 D）
// 从 /api/v1/knowledge/extract/candidates/{fileId} 读取待审核候选，或从 candidates 列表选文件后批量展示
// 复用已有 ExtractionReviewPanel 的核心展示，本 Tab 作为独立审核入口
import { useCallback, useEffect, useState } from "react";
import {
  AlertTriangle, FileText, ListChecks, Loader2, RefreshCw, Search, Inbox,
} from "lucide-react";
import { useTheme } from "../../../components/ThemeContext";
import { useLanguage } from "../../../components/LanguageContext";
import { knowledgeApi } from "../services/knowledgeApi";

// ----------------------------
// 类型
// ----------------------------

interface CandidateFile {
  fileId: string;
  fileName: string;
  status: string;
  candidateCount: number;
  checksum?: string;
  createdAt?: string;
  error?: string;
}

export default function ExtractionReviewTab() {
  const { styles } = useTheme();
  const { t, locale } = useLanguage();
  const [loading, setLoading] = useState(false);
  const [files, setFiles] = useState<CandidateFile[]>([]);
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<CandidateFile | null>(null);
  const [candidates, setCandidates] = useState<Record<string, unknown>[]>([]);
  const [candidatesLoading, setCandidatesLoading] = useState(false);
  const [notFoundBanner, setNotFoundBanner] = useState<string | null>(null);

  const loadFiles = useCallback(async () => {
    setLoading(true);
    setNotFoundBanner(null);
    try {
      const list = await knowledgeApi.fetchExtractCandidateFiles();
      setFiles(list);
    } catch (e) {
      const msg = String(e);
      if (msg.includes("404") || msg.toLowerCase().includes("not found")) {
        setNotFoundBanner(
          `${t("knowledge.review.file_list_not_found")} (${msg}). ${t("knowledge.review.backend_stub_hint")}`
        );
      } else {
        setNotFoundBanner(`${t("knowledge.review.file_list_error")} ${msg}`);
      }
    } finally {
      setLoading(false);
    }
  }, [t]);

  useEffect(() => { void loadFiles(); }, [loadFiles]);

  const loadCandidates = useCallback(async (fileId: string) => {
    setCandidatesLoading(true);
    setCandidates([]);
    try {
      const res = await knowledgeApi.fetchExtractCandidates(fileId);
      setCandidates(res?.candidates || []);
    } catch (e) {
      setNotFoundBanner(`${t("knowledge.review.candidates_error")} ${String(e)}`);
    } finally {
      setCandidatesLoading(false);
    }
  }, [t]);

  useEffect(() => {
    if (selected?.fileId) void loadCandidates(selected.fileId);
  }, [selected, loadCandidates]);

  const filteredFiles = files.filter(f =>
    !search || f.fileName.toLowerCase().includes(search.toLowerCase()) || f.fileId.includes(search)
  );

  return (
    <div className="h-full overflow-y-auto">
      <div className="space-y-4">
        {/* 标题 */}
        <div>
          <h2 className={`text-xl font-bold ${styles.cardText}`}>{t("knowledge.review.title")}</h2>
          <p className={`text-xs mt-1 ${styles.cardTextMuted}`}>{t("knowledge.review.subtitle")}</p>
        </div>

        {notFoundBanner && (
          <div className="flex items-start gap-2 rounded-md border px-3 py-2 text-xs border-yellow-300 bg-yellow-100 text-yellow-900">
            <AlertTriangle className="w-3.5 h-3.5 flex-shrink-0 mt-0.5" />
            <span>{notFoundBanner}</span>
          </div>
        )}

        <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
          {/* 左：候选文件列表 */}
          <div className={`border rounded-lg ${styles.cardBg} ${styles.cardBorder} flex flex-col`}>
            <div className="flex items-center justify-between px-4 py-2.5 border-b" style={{ borderColor: styles.cardBorder }}>
              <div className="flex items-center gap-2 text-sm font-medium">
                <ListChecks className="w-4 h-4" />
                {t("knowledge.review.files_list")}
              </div>
              <div className="flex items-center gap-2">
                <div className="relative">
                  <Search className="w-3.5 h-3.5 absolute left-2 top-1/2 -translate-y-1/2 opacity-50" />
                  <input
                    value={search}
                    onChange={e => setSearch(e.target.value)}
                    placeholder={t("knowledge.review.search_file")}
                    className={`text-xs pl-7 pr-2 py-1 rounded-md border ${styles.inputBg} ${styles.inputBorder} focus:outline-none`}
                  />
                </div>
                <button
                  onClick={() => void loadFiles()}
                  className="p-1 rounded border hover:opacity-70"
                  style={{ borderColor: styles.cardBorder }}
                  title={t("knowledge.common.refresh")}
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
            <div className="flex-1 overflow-y-auto max-h-[480px]">
              {loading && <div className="p-4 flex items-center gap-2 text-xs opacity-60"><Loader2 className="w-4 h-4 animate-spin" />{t("knowledge.gbt.loading")}</div>}
              {!loading && filteredFiles.length === 0 && (
                <div className="p-8 text-center opacity-50">
                  <Inbox className="w-6 h-6 mx-auto mb-2 opacity-50" />
                  <div className="text-xs">{t("knowledge.review.files_empty")}</div>
                </div>
              )}
              {!loading && filteredFiles.map(f => (
                <button
                  key={f.fileId}
                  onClick={() => setSelected(f)}
                  className={`w-full text-left px-4 py-2.5 border-b hover:opacity-80 transition-opacity ${
                    selected?.fileId === f.fileId ? `${styles.accentBg} text-white` : ""
                  }`}
                  style={{ borderColor: styles.cardBorder }}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs flex items-center gap-1.5 truncate">
                      <FileText className="w-3.5 h-3.5 flex-shrink-0 opacity-60" />
                      {f.fileName}
                    </span>
                    <span className="text-[10px] font-mono px-1.5 py-0.5 rounded opacity-80 flex-shrink-0">
                      {f.status}
                    </span>
                  </div>
                  <div className="text-[10px] font-mono mt-0.5 opacity-60">
                    {f.candidateCount} {t("knowledge.review.candidate_unit")}
                    {f.createdAt ? ` · ${new Date(f.createdAt).toLocaleString()}` : ""}
                  </div>
                </button>
              ))}
            </div>
          </div>

          {/* 右：候选详情 */}
          <div className={`border rounded-lg ${styles.cardBg} ${styles.cardBorder} flex flex-col`}>
            <div className="px-4 py-2.5 border-b flex items-center justify-between" style={{ borderColor: styles.cardBorder }}>
              <div className="text-sm font-medium flex items-center gap-2">
                <FileText className="w-4 h-4" />
                {t("knowledge.review.candidates_detail")}
              </div>
              {selected && (
                <div className="text-[11px] font-mono opacity-60">
                  {selected.fileName} · {selected.candidateCount} items
                </div>
              )}
            </div>
            <div className="flex-1 overflow-y-auto max-h-[480px] p-4">
              {!selected && (
                <div className="h-full flex items-center justify-center text-xs opacity-50">
                  {t("knowledge.review.select_file_hint")}
                </div>
              )}
              {selected && candidatesLoading && (
                <div className="flex items-center justify-center gap-2 text-xs opacity-60 py-8">
                  <Loader2 className="w-4 h-4 animate-spin" />
                  {t("knowledge.review.loading_candidates")}
                </div>
              )}
              {selected && !candidatesLoading && candidates.length === 0 && (
                <div className="h-full flex items-center justify-center text-xs opacity-50">
                  {t("knowledge.review.candidates_empty")}
                </div>
              )}
              {selected && !candidatesLoading && candidates.length > 0 && (
                <div className="space-y-2">
                  {candidates.map((c, i) => (
                    <div key={i} className="border rounded-md p-2.5 text-xs" style={{ borderColor: styles.cardBorder }}>
                      <div className="flex items-center justify-between mb-1">
                        <span className="font-medium">
                          {String(c.type ?? c.entityType ?? c.kind ?? c.candidateId ?? `#${i + 1}`)}
                        </span>
                        <span className={`px-1.5 py-0.5 rounded text-[10px] font-mono ${
                          c.rejected ? "bg-red-100 text-red-700" : "bg-green-100 text-green-700"
                        }`}>
                          {c.rejected ? "REJECTED" : "PENDING"}
                        </span>
                      </div>
                      <div className="opacity-70 text-[11px] break-words">
                        {String(c.description ?? c.label ?? c.value ?? JSON.stringify(c).slice(0, 200))}
                      </div>
                      {c.confidence !== undefined && (
                        <div className="text-[10px] font-mono mt-1 opacity-60">
                          confidence: {Number(c.confidence).toFixed(3)}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>

        <div className="text-[11px] font-mono opacity-50 flex items-center gap-2">
          <span>{t("knowledge.review.backend_stub_hint")}</span>
          <span className="opacity-50">
            GET /api/v1/knowledge/extract/files, GET /api/v1/knowledge/extract/candidates/{'{fileId}'}
          </span>
        </div>
      </div>
    </div>
  );
}
