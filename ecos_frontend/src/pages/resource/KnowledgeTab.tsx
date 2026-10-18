/**
 * KnowledgeTab — 资源中心「知识资产」页签（2026-10-14 菜单重构）。
 *
 * 降级裁决（登记）：KnowledgeView 自带完整深链/6-page UI 且与工作台上下文强耦合，
 * 直接内嵌会渲染失效控件（假真理红线）；本 Tab = 降级说明横幅 + 真实术语表清单
 * （kbFetchGlossaryTerms 真值，四态：loading/error/empty/data）+ 知识工作台入口
 * CTA。完整知识域仍在 #/knowledge_view（菜单「工作区·知识工作台」）。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */
import React, { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { AlertTriangle, ArrowRight, Library, RefreshCw } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { kbFetchGlossaryTerms } from "../knowledge/services/wiki";
import logger from "../../services/logger";

interface GlossaryTermLite {
  id: number | string;
  name: string;
  code?: string | null;
  domain?: string | null;
  status: string;
}

interface ListState {
  loading: boolean;
  error: string | null;
}

export default function KnowledgeTab() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const navigate = useNavigate();

  const [terms, setTerms] = useState<GlossaryTermLite[]>([]);
  const [state, setState] = useState<ListState>({ loading: true, error: null });
  const [disclaimerOpen, setDisclaimerOpen] = useState(true);

  const load = useCallback(async () => {
    setState((p) => ({ ...p, loading: true, error: null }));
    try {
      const arr = await kbFetchGlossaryTerms(undefined, 200);
      setTerms((arr ?? []).map((x) => ({
        id: x.id,
        name: x.name,
        code: x.code ?? undefined,
        domain: x.domain ?? undefined,
        status: x.status,
      })));
      setState({ loading: false, error: null });
    } catch (e) {
      logger.error("resource-knowledge-tab", e);
      setTerms([]);
      setState({ loading: false, error: e instanceof Error ? e.message : String(e) });
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="flex-1 min-h-0 flex flex-col gap-3 p-4 overflow-y-auto">
      {/* 降级注记（可折叠，C-9） */}
      <div className={`rounded-md border px-3 py-2 text-[11px] ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
        <button
          type="button"
          onClick={() => setDisclaimerOpen((v) => !v)}
          className="flex items-center gap-1.5 font-bold cursor-pointer"
        >
          <AlertTriangle size={13} /> {t("platform.resource.knowledge.degraded")}
        </button>
        {disclaimerOpen && (
          <div className="mt-2 flex items-center justify-between gap-2 flex-wrap">
            <span className={`${styles.cardTextMuted}`}>{t("platform.resource.knowledge.degraded")}</span>
            <button
              type="button"
              onClick={() => { navigate("/knowledge_view"); setDisclaimerOpen(false); }}
              className={`inline-flex items-center gap-1 rounded border px-2.5 py-1 cursor-pointer shrink-0 ${styles.accentText} ${styles.accentBorder}`}
            >
              <Library size={12} />
              {t("platform.resource.openKnowledgeWorkbench")}
              <ArrowRight size={12} />
            </button>
          </div>
        )}
      </div>

      {/* 真实术语表清单（/api/v1/kb/glossary/terms，limit=200 服务端截断口径） */}
      <div className={`rounded-lg border ${styles.cardBg} ${styles.cardBorder}`}>
        <div className={`flex items-center justify-between px-3 py-2 border-b ${styles.cardBorder}`}>
          <span className={`text-xs font-bold uppercase ${styles.cardTextMuted}`}>
            {t("platform.resource.tab.knowledge")}
            <span className={`ml-2 font-mono ${styles.cardTextMuted}`}>
              {state.loading ? "…" : `${terms.length}`}
            </span>
          </span>
          <button
            type="button"
            onClick={() => void load()}
            className={`p-1 rounded border cursor-pointer ${styles.cardBorder} ${styles.cardTextMuted} hover:opacity-80`}
            title={t("platform.decision.retry")}
          >
            <RefreshCw size={12} />
          </button>
        </div>

        <div className="p-3">
          {state.loading ? (
            <div className="py-8 text-center text-[11px]">
              <Spinner />
            </div>
          ) : state.error ? (
            <div className={`rounded-md px-3 py-4 text-[11px] text-center break-words ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
              {t("platform.decision.loadFailed")}{state.error}
              <button
                type="button"
                onClick={() => void load()}
                className={`ml-2 rounded border px-2 py-0.5 cursor-pointer align-middle ${styles.inputBorder} ${styles.inputText}`}
              >
                {t("platform.decision.retry")}
              </button>
            </div>
          ) : terms.length === 0 ? (
            <p className={`py-8 text-center text-[11px] ${styles.cardTextMuted}`}>
              {t("platform.resource.cognitive.empty")}
            </p>
          ) : (
            <ul className="grid gap-1.5 grid-cols-1 md:grid-cols-2 xl:grid-cols-3">
              {terms.map((term) => (
                <li key={String(term.id)} className={`rounded border px-2.5 py-1.5 min-w-0 ${styles.cardBorder}`}>
                  <div className="flex items-center justify-between gap-2">
                    <span className={`text-[11px] font-bold truncate ${styles.cardText}`}>{term.name}</span>
                    <span className={`shrink-0 text-[10px] font-mono ${styles.cardTextMuted}`}>{term.status}</span>
                  </div>
                  <div className={`flex items-center gap-2 text-[10px] mt-0.5 ${styles.cardTextMuted}`}>
                    {term.code && <span className="font-mono">{term.code}</span>}
                    {term.domain && <span className="truncate">{term.domain}</span>}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}

function Spinner() {
  return <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />;
}
