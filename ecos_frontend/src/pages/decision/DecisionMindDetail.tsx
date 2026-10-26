/**
 * DecisionMindDetail — R4 Mind 治理详情（#/decision/mind/:mindId，P0-6 / B3-c）。
 *
 * 三区：A 六段投影 + 三轴（V292 未迁移走未迁移横幅，禁假值）；
 *        B 候选更新队列（PROPOSED/ACCEPTED/REJECTED 三态 + 受控终态置入）；
 *        C 提交候选（受控人工通道，Agent 提交通道的等价字面；恒落 PROPOSED）。
 *
 * 数据面单源：services/cognitive/mindApi.ts。终态写路径唯一 = finalizeMindProposal
 * （ACCEPTED/REJECTED，复核原因必填，留痕四要件由后端承载）。
 *
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ArrowLeft, Brain, Check, ChevronDown, ChevronUp, RefreshCw, Send, X } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import {
  fetchMindDetail,
  fetchMindProposals,
  finalizeMindProposal,
  submitMindProposal,
  type MindDetail,
  type MindUpdateRow,
} from "../../services/cognitive/mindApi";
import logger from "../../services/logger";

interface LoadState {
  loading: boolean;
  error: string | null;
}

/** 词表枚 → i18n；未命中保留原值前置固定词（让服务端漂移显形，禁吞掉）。 */
function i18nEnum(t: (k: string) => string, key: string, raw: string | null | undefined): string {
  if (!raw) return "—";
  const k = `platform.mind.status.${raw}`;
  const mapped = t(k);
  return mapped === k ? `${t("platform.mind.unmappedPrefix")}${raw}` : mapped;
}

function i18nTargetType(t: (k: string) => string, raw: string | null | undefined): string {
  if (!raw) return "—";
  const k = `platform.mind.targetType.${raw}`;
  const mapped = t(k);
  return mapped === k ? raw : mapped;
}

/** 三轴 status 徽章配色档：批准/采纳/接受 → 绿；候选/待 → 黄；驳回/作废 → 红。 */
function toneOf(raw: string | null | undefined): "ok" | "warn" | "bad" | "muted" {
  if (!raw) return "muted";
  if (["APPROVED", "ADOPTED", "ACCEPTED", "VALID"].includes(raw)) return "ok";
  if (["PROPOSED", "PENDING", "PENDING_REVIEW", "DRAFT"].includes(raw)) return "warn";
  if (["REJECTED", "INVALIDATED", "WITHDRAWN"].includes(raw)) return "bad";
  return "muted";
}

export default function DecisionMindDetail() {
  const { mindId = "" } = useParams<{ mindId: string }>();
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [detail, setDetail] = useState<MindDetail | null>(null);
  const [detailState, setDetailState] = useState<LoadState>({ loading: true, error: null });

  const [proposals, setProposals] = useState<MindUpdateRow[]>([]);
  const [propState, setPropState] = useState<LoadState>({ loading: true, error: null });
  /** 置终态成功回执（行内提示，防假绿：只展示服务端回行的 status）。 */
  const [finalizeMsg, setFinalizeMsg] = useState<string | null>(null);
  /** 行内 busy 态（updateId 集合）。 */
  const [busyIds, setBusyIds] = useState<Set<string>>(new Set());

  const loadDetail = useCallback(async () => {
    setDetailState({ loading: true, error: null });
    try {
      const d = await fetchMindDetail(mindId);
      setDetail(d);
      setDetailState({ loading: false, error: null });
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      logger.error("decision-mind-detail", e);
      setDetailState({ loading: false, error: msg });
    }
  }, [mindId]);

  const loadProposals = useCallback(async () => {
    setPropState({ loading: true, error: null });
    try {
      const list = await fetchMindProposals(mindId, 50);
      setProposals(list);
      setPropState({ loading: false, error: null });
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      logger.error("decision-mind-proposals", e);
      setPropState({ loading: false, error: msg });
    }
  }, [mindId]);

  useEffect(() => {
    void loadDetail();
    void loadProposals();
  }, [loadDetail, loadProposals]);

  const retryAll = (): void => {
    void loadDetail();
    void loadProposals();
  };

  const doFinalize = async (row: MindUpdateRow, status: "ACCEPTED" | "REJECTED"): Promise<void> => {
    const reason = window.prompt(t("platform.mind.gate.reasonPlaceholder"), "") ?? "";
    if (reason.trim().length === 0) return; // 复核原因必填（留痕四要件），空 = 不发请求
    setBusyIds((p) => new Set(p).add(row.id));
    try {
      const updated = await finalizeMindProposal(row.id, { status, reviewReason: reason.trim() });
      setProposals((p) => p.map((x) => (x.id === row.id ? { ...x, ...updated } : x)));
      setFinalizeMsg(`${row.id} → ${updated.status ?? status}`);
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      logger.error("decision-mind-finalize", e);
      setFinalizeMsg(`ERROR ${msg}`);
    } finally {
      setBusyIds((p) => {
        const n = new Set(p);
        n.delete(row.id);
        return n;
      });
    }
  };

  const axisBadge = (raw: string | null | undefined) => {
    const tone = toneOf(raw);
    const map = {
      ok: `${styles.successBg} ${styles.successText}`,
      warn: `${styles.warningBg} ${styles.warningText}`,
      bad: `${styles.dangerBg} ${styles.dangerText}`,
      muted: `${styles.cardTextMuted}`,
    } as const;
    return (
      <span className={`inline-flex items-center rounded border px-1.5 py-0.5 text-[10px] font-mono font-bold ${map[tone]} ${styles.cardBorder}`}>
        {i18nEnum(t, "platform.mind.status", raw)}
      </span>
    );
  };

  return (
    <div className={`flex-1 flex flex-col overflow-y-auto ${styles.appBg} ${styles.appText}`}>
      {/* 页头 */}
      <div className={`px-4 py-4 border-b ${styles.cardBorder} ${styles.cardBg} shrink-0 flex items-center justify-between`}>
        <div className="flex items-center gap-2">
          <Link
            to="/decision/mind"
            className={`p-1.5 rounded-md border ${styles.cardBorder} ${styles.cardTextMuted} hover:opacity-80`}
            title={t("platform.mind.back")}
          >
            <ArrowLeft size={14} />
          </Link>
          <span className={`p-1.5 rounded-md flex items-center justify-center ${styles.accentBg} ${styles.cardText}`}>
            <Brain size={16} />
          </span>
          <div>
            <h1 className={`text-lg font-bold tracking-tight ${styles.appText}`}>
              {t("platform.mind.detail")}
              <span className="font-mono text-xs ml-2 opacity-70">{mindId}</span>
            </h1>
            <p className={`text-[11px] ${styles.cardTextMuted} -mt-0.5`}>{t("platform.mind.subtitle")}</p>
          </div>
        </div>
        <button
          type="button"
          onClick={retryAll}
          className={`p-1.5 rounded-md border ${styles.cardBorder} ${styles.cardTextMuted} hover:opacity-80 cursor-pointer`}
          title={t("platform.mind.retry")}
        >
          <RefreshCw size={14} />
        </button>
      </div>

      {/* A · 六段投影 + 三轴 */}
      <section className="p-4">
        <div className={`rounded-lg border ${styles.cardBg} ${styles.cardBorder} p-4`}>
          {detailState.loading ? (
            <div className="flex items-center justify-center py-8"><Spinner /></div>
          ) : detailState.error ? (
            <ErrorBox detail={detailState.error} onRetry={retryAll} />
          ) : !detail ? (
            <ErrorBox detail={t("platform.mind.empty")} onRetry={retryAll} />
          ) : (
            <>
              {!detail.r4ColumnsProvisioned && (
                <div className={`mb-3 rounded-md px-3 py-2 border text-[11px] ${styles.warningBg} ${styles.warningText} ${styles.cardBorder}`}>
                  {t("platform.mind.unmigrated")}
                </div>
              )}
              <div className="grid gap-3 md:grid-cols-2">
                <Field label={t("platform.mind.col.name")} value={detail.name ?? "—"} styles={styles} />
                <Field label={t("platform.mind.col.key")} value={detail.mindKey ?? "—"} mono styles={styles} />
                <Field
                  label={t("platform.mind.axis.validity")}
                  node={axisBadge(detail.validityStatus)}
                  styles={styles}
                />
                <Field
                  label={t("platform.mind.axis.review")}
                  node={axisBadge(detail.reviewStatus)}
                  styles={styles}
                />
                <Field
                  label={t("platform.mind.axis.adoption")}
                  node={axisBadge(detail.adoptionStatus)}
                  styles={styles}
                />
                <Field label={t("platform.mind.detail.supersededBy")} value={detail.supersededBy ?? "—"} mono styles={styles} />
              </div>
              {detail.description && (
                <p className={`mt-3 text-xs ${styles.cardTextMuted} break-words`}>{detail.description}</p>
              )}
              <div className={`mt-3 text-xs ${styles.cardText}`}>
                <span className={`font-semibold ${styles.cardTextMuted}`}>{t("platform.mind.detail.changeReason")}: </span>
                {detail.changeReason ?? t("platform.mind.detail.versionUnset")}
              </div>
            </>
          )}
        </div>
      </section>

      {/* B · 候选更新队列 */}
      <section className="px-4 pb-4">
        <div className={`rounded-lg border ${styles.cardBg} ${styles.cardBorder} p-4`}>
          <h2 className={`text-xs font-bold uppercase ${styles.cardTextMuted} mb-1`}>
            {t("platform.mind.gate.title")}
          </h2>
          <p className={`text-[11px] ${styles.cardTextMuted} mb-3`}>{t("platform.mind.gate.desc")}</p>

          {propState.loading ? (
            <div className="flex items-center justify-center py-8"><Spinner /></div>
          ) : propState.error ? (
            <ErrorBox detail={propState.error} onRetry={retryAll} />
          ) : proposals.length === 0 ? (
            <p className={`py-6 text-center text-xs ${styles.cardTextMuted}`}>{t("platform.mind.gate.empty")}</p>
          ) : (
            <div className="space-y-2">
              {finalizeMsg && (
                <div
                  className={`rounded-md px-3 py-2 text-[11px] border ${
                    finalizeMsg.startsWith("ERROR")
                      ? `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`
                      : `${styles.successBg} ${styles.successText} ${styles.cardBorder}`
                  }`}
                >
                  {finalizeMsg.startsWith("ERROR")
                    ? t("platform.mind.gate.finalizeFailed") + finalizeMsg.slice(5)
                    : t("platform.mind.gate.done") + " " + finalizeMsg}
                </div>
              )}
              {proposals.map((p) => (
                <ProposalCard
                  key={p.id}
                  row={p}
                  busy={busyIds.has(p.id)}
                  onFinalize={(s) => void doFinalize(p, s)}
                  styles={styles}
                  t={t}
                />
              ))}
            </div>
          )}
        </div>
      </section>

      <SubmitSection mindId={mindId} onSubmitted={() => void loadProposals()} />
    </div>
  );
}

function ProposalCard({
  row,
  busy,
  onFinalize,
  styles,
  t,
}: {
  row: MindUpdateRow;
  busy: boolean;
  onFinalize: (s: "ACCEPTED" | "REJECTED") => void;
  styles: Record<string, string>;
  t: (k: string) => string;
}) {
  const [open, setOpen] = useState(false);
  const terminal = row.status === "ACCEPTED" || row.status === "REJECTED";
  const tone = toneOf(row.status);
  const toneCls = {
    ok: `${styles.successBg} ${styles.successText}`,
    warn: `${styles.warningBg} ${styles.warningText}`,
    bad: `${styles.dangerBg} ${styles.dangerText}`,
    muted: `${styles.cardTextMuted}`,
  }[tone];
  return (
    <div className={`rounded-md border ${styles.cardBorder} ${styles.appBg}`}>
      <button
        type="button"
        className="w-full flex items-center gap-2 px-3 py-2 text-left cursor-pointer"
        onClick={() => setOpen((v) => !v)}
      >
        <span className={`inline-flex items-center rounded border px-1.5 py-0.5 text-[10px] font-mono font-bold ${toneCls} ${styles.cardBorder}`}>
          {i18nEnum(t, "platform.mind.status", row.status)}
        </span>
        <span className={`text-[11px] font-semibold ${styles.cardText}`}>
          {i18nTargetType(t, row.targetType)}
          {row.targetId ? ` · ${row.targetId}` : ""}
        </span>
        <span className={`ml-auto text-[10px] font-mono ${styles.cardTextMuted}`}>{row.agentRunId ?? ""}</span>
        {open ? <ChevronUp size={12} className={styles.cardTextMuted} /> : <ChevronDown size={12} className={styles.cardTextMuted} />}
      </button>
      {open && (
        <div className={`px-3 pb-3 border-t ${styles.cardBorder} space-y-1 text-[11px]`}>
          <div className={`pt-2 ${styles.cardText}`}>
            <span className={`font-semibold ${styles.cardTextMuted}`}>{t("platform.mind.gate.submitter")}: </span>
            <span className="font-mono">{row.agentRunId ?? "—"}</span>
            <span className={`ml-4 font-semibold ${styles.cardTextMuted}`}>{t("platform.mind.gate.reason")}: </span>
            {row.reviewReason ?? "—"}
            {row.reviewedBy && <span className={`ml-1 ${styles.cardTextMuted}`}>（{row.reviewedBy}）</span>}
          </div>
          <pre className={`rounded border p-2 text-[10px] overflow-x-auto break-all ${styles.cardBorder} ${styles.cardTextMuted}`}>
            {row.changeJson ?? "—"}
          </pre>
          {!terminal && (
            <div className="flex gap-2 pt-1">
              <button
                type="button"
                disabled={busy}
                onClick={() => onFinalize("ACCEPTED")}
                className={`inline-flex items-center gap-1 rounded border px-2 py-1 text-[11px] cursor-pointer disabled:opacity-50 ${styles.successBg} ${styles.successText} ${styles.cardBorder}`}
              >
                <Check size={12} /> {t("platform.mind.gate.accept")}
              </button>
              <button
                type="button"
                disabled={busy}
                onClick={() => onFinalize("REJECTED")}
                className={`inline-flex items-center gap-1 rounded border px-2 py-1 text-[11px] cursor-pointer disabled:opacity-50 ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}
              >
                <X size={12} /> {t("platform.mind.gate.reject")}
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

/** 候选作用对象词表与后端 MindUpdateVO.TARGET_TYPES 逐字对齐（BELIEF/HYPOTHESIS/CONTEXT/EXPLANATION）。 */
const TARGET_TYPES = ["BELIEF", "HYPOTHESIS", "CONTEXT", "EXPLANATION"] as const;

function SubmitSection({ mindId, onSubmitted }: { mindId: string; onSubmitted: () => void }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [agentRunId, setAgentRunId] = useState("");
  const [targetType, setTargetType] = useState<string>(TARGET_TYPES[0]);
  const [targetId, setTargetId] = useState("");
  const [changeJson, setChangeJson] = useState("");
  const [evidenceRefsJson, setEvidenceRefsJson] = useState("");
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<{ ok: boolean; msg: string } | null>(null);

  const canSubmit = agentRunId.trim().length > 0 && changeJson.trim().length > 0 && !busy;

  const submit = async (): Promise<void> => {
    if (!canSubmit) return;
    setBusy(true);
    setResult(null);
    try {
      const row = await submitMindProposal(mindId, {
        agentRunId: agentRunId.trim(),
        targetType,
        targetId: targetId.trim() || null,
        changeJson: changeJson.trim(),
        evidenceRefsJson: evidenceRefsJson.trim() || undefined,
      });
      setResult({ ok: true, msg: `${t("platform.mind.submit.done")} (${row.status ?? "PROPOSED"})` });
      setAgentRunId("");
      setTargetId("");
      setChangeJson("");
      setEvidenceRefsJson("");
      onSubmitted();
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      logger.error("decision-mind-submit", e);
      setResult({ ok: false, msg: `${t("platform.mind.submit.failed")}${msg}` });
    } finally {
      setBusy(false);
    }
  };

  const inputCls = `w-full rounded border px-2 py-1 text-xs ${styles.inputBorder} ${styles.inputText} ${styles.appBg}`;
  return (
    <section className="px-4 pb-6">
      <div className={`rounded-lg border ${styles.cardBg} ${styles.cardBorder} p-4`}>
        <h2 className={`text-xs font-bold uppercase ${styles.cardTextMuted} mb-1`}>{t("platform.mind.submit.title")}</h2>
        <div className="grid gap-2 md:grid-cols-2 mt-2">
          <label className="text-[11px]">
            <span className={styles.cardTextMuted}>{t("platform.mind.submit.agentRunId")}</span>
            <input value={agentRunId} onChange={(e) => setAgentRunId(e.target.value)} className={`${inputCls} mt-0.5`} />
          </label>
          <label className="text-[11px]">
            <span className={styles.cardTextMuted}>{t("platform.mind.submit.targetType")}</span>
            <select value={targetType} onChange={(e) => setTargetType(e.target.value)} className={`${inputCls} mt-0.5`}>
              {TARGET_TYPES.map((v) => (
                <option key={v} value={v}>{i18nTargetType(t, v)}</option>
              ))}
            </select>
          </label>
          <label className="text-[11px]">
            <span className={styles.cardTextMuted}>{t("platform.mind.submit.targetId")}</span>
            <input value={targetId} onChange={(e) => setTargetId(e.target.value)} className={`${inputCls} mt-0.5`} />
          </label>
          <label className="text-[11px]">
            <span className={styles.cardTextMuted}>{t("platform.mind.submit.evidenceRefs")}</span>
            <input value={evidenceRefsJson} onChange={(e) => setEvidenceRefsJson(e.target.value)} className={`${inputCls} mt-0.5`} />
          </label>
          <label className={`text-[11px] md:col-span-2`}>
            <span className={styles.cardTextMuted}>{t("platform.mind.submit.changeJson")}</span>
            <textarea
              value={changeJson}
              onChange={(e) => setChangeJson(e.target.value)}
              rows={4}
              className={`${inputCls} mt-0.5 font-mono`}
            />
          </label>
        </div>
        {result && (
          <div
            className={`mt-2 rounded-md px-3 py-2 text-[11px] border ${
              result.ok
                ? `${styles.successBg} ${styles.successText} ${styles.cardBorder}`
                : `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`
            }`}
          >
            {result.msg}
          </div>
        )}
        <button
          type="button"
          disabled={!canSubmit}
          onClick={() => void submit()}
          className={`mt-3 inline-flex items-center gap-1.5 rounded border px-3 py-1.5 text-xs font-bold cursor-pointer disabled:opacity-50 ${styles.accentBg} ${styles.accentText} ${styles.accentBorder}`}
        >
          <Send size={13} /> {t("platform.mind.submit.submit")}
        </button>
      </div>
    </section>
  );
}

function Field({
  label,
  value,
  node,
  mono = false,
  styles,
}: {
  label: string;
  value?: string;
  node?: React.ReactNode;
  mono?: boolean;
  styles: Record<string, string>;
}) {
  return (
    <div>
      <div className={`text-[10px] font-semibold uppercase tracking-wide ${styles.cardTextMuted}`}>{label}</div>
      <div className={`mt-0.5 text-xs ${mono ? "font-mono" : ""} ${styles.cardText} break-words`}>{node ?? value ?? "—"}</div>
    </div>
  );
}

function Spinner() {
  return (
    <span className="h-5 w-5 inline-block animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />
  );
}

function ErrorBox({ detail, onRetry }: { detail: string; onRetry: () => void }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  return (
    <div className={`rounded-md px-3 py-4 border text-[11px] break-words ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder} text-center`}>
      <span>{detail}</span>
      <button
        type="button"
        onClick={onRetry}
        className={`ml-2 rounded border px-2 py-0.5 cursor-pointer ${styles.inputBorder} ${styles.inputText}`}
      >
        {t("platform.mind.retry")}
      </button>
    </div>
  );
}
