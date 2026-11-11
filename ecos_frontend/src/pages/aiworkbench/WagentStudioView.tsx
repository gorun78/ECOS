/**
 * WagentStudioView — AI 工作台「W 智能体编排」向导（批 C，2026-11-11 WAgent 三块差距计划）。
 *
 * 定位：把已注册的 WAgent 工具契约装配成候选，并沿 L3 审批链发布为 PUBLISHED，
 * 产出的 PUBLISHED 候选由场景工作台 /available/wagents 通道可绑定（批 B）。
 *
 * 向导四步（详设-10 §4.2 关系流 / §5.2 #1 #17~#20）：
 *   ① 目标    POST /wagent/goals（GoalCreateRequest：name + goalType 八意图）
 *   ② 装配    GET /wagent/tools 检索 + POST /wagent/candidates 产出 GENERATED 候选
 *   ③ 校验    五段自检 POST /candidates/{id}/validate（generated/validating → validated）
 *             + 四眼评审 POST /candidates/{id}/review（APPROVE 必带凭证）
 *   ④ 发布    POST /approval-tokens 签发 → POST /candidates/{id}/publish → PUBLISHED
 *
 * 真值纪律（无假真理 / P0）：
 *   · 每步只呈服务端返回的状态与错误原文；403/409 一律如实横幅，禁本地合成成功；
 *   · 校验返回 200 ≠ 可评审：任一段 fail 时状态仍为 validating，UI 读 status 后再放行评审；
 *   · 明文审批凭证只出现在签发响应（服务端只存 SHA-256 摘要），本页仅内存持有、不进 URL。
 *
 * D-WC-1（2026-11-11）：原③只有评审，而评审仅接受 validated/review_pending、建候选恒 generated
 * ⇒ 新候选无法进入评审。本页对齐新增的 validate 通道：先校验，通过后才能评审。
 *
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  Bot,
  Target,
  Wrench,
  ClipboardCheck,
  Rocket,
  RefreshCw,
  AlertTriangle,
  Inbox,
  CheckCircle2,
} from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { logger } from "../../services/logger";
import {
  WAGENT_GOAL_INTENTS,
  createApprovalToken,
  createWagentCandidate,
  createWagentGoal,
  listWagentCandidates,
  publishWagentCandidate,
  reviewWagentCandidate,
  searchWagentTools,
  validateWagentCandidate,
  type WagentCandidateRow,
  type WagentPublishResult,
  type WagentToolRow,
} from "../../services/wagentClient";

type WizardStep = 1 | 2 | 3 | 4;

/** 可评审状态（服务端 CandidateStatus.canReview()：validated / review_pending）。 */
const REVIEWABLE = new Set(["validated", "review_pending"]);

/** 可发起五段校验的状态（服务端 CANDIDATE_VALIDATE 闸：generated/validating/validated）。 */
const VALIDATABLE = new Set(["generated", "validating", "validated"]);

const STEP_ICONS = [Target, Wrench, ClipboardCheck, Rocket] as const;

function errText(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

export default function WagentStudioView() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [step, setStep] = useState<WizardStep>(1);

  // ① 目标
  const [goalName, setGoalName] = useState("");
  const [goalIntent, setGoalIntent] = useState<string>(WAGENT_GOAL_INTENTS[0]);
  const [goalScope, setGoalScope] = useState("");
  const [goalId, setGoalId] = useState<string | null>(null);
  const [goalError, setGoalError] = useState<string | null>(null);
  const [goalBusy, setGoalBusy] = useState(false);

  // ② 装配
  const [tools, setTools] = useState<WagentToolRow[]>([]);
  const [toolsBusy, setToolsBusy] = useState(false);
  const [toolsError, setToolsError] = useState<string | null>(null);
  const [toolKeyword, setToolKeyword] = useState("");
  const [selectedTools, setSelectedTools] = useState<string[]>([]);
  const [targetObjectRef, setTargetObjectRef] = useState("");
  const [createdCandidate, setCreatedCandidate] = useState<WagentCandidateRow | null>(null);
  const [assembleError, setAssembleError] = useState<string | null>(null);
  const [assembleBusy, setAssembleBusy] = useState(false);

  // ③ 校验 / 评审 / 候选选择
  const [candidates, setCandidates] = useState<WagentCandidateRow[]>([]);
  const [candBusy, setCandBusy] = useState(false);
  const [candError, setCandError] = useState<string | null>(null);
  const [selectedCandidateId, setSelectedCandidateId] = useState<string | null>(null);
  const [validateBusy, setValidateBusy] = useState(false);
  const [validateError, setValidateError] = useState<string | null>(null);
  const [reviewComment, setReviewComment] = useState("");
  const [reviewBusy, setReviewBusy] = useState(false);
  const [reviewError, setReviewError] = useState<string | null>(null);

  // ④ 发布
  const [tokenRequestedBy, setTokenRequestedBy] = useState("");
  const [issuedToken, setIssuedToken] = useState<string | null>(null);
  const [tokenBusy, setTokenBusy] = useState(false);
  const [tokenError, setTokenError] = useState<string | null>(null);
  const [publishBusy, setPublishBusy] = useState(false);
  const [publishError, setPublishError] = useState<string | null>(null);
  const [publishResult, setPublishResult] = useState<WagentPublishResult | null>(null);

  // ── 工具检索（②）──
  const loadTools = useCallback((keyword?: string) => {
    setToolsBusy(true);
    setToolsError(null);
    searchWagentTools(keyword && keyword.trim() ? { keyword: keyword.trim() } : undefined)
      .then((rows) => setTools(rows))
      .catch((e: unknown) => {
        logger.error("wagent-studio-tools", e);
        setToolsError(errText(e));
        setTools([]);
      })
      .finally(() => setToolsBusy(false));
  }, []);

  useEffect(() => {
    void loadTools();
  }, [loadTools]);

  // ── 候选库（③）──
  const loadCandidates = useCallback(() => {
    setCandBusy(true);
    setCandError(null);
    listWagentCandidates()
      .then((page) => setCandidates(page.candidates ?? []))
      .catch((e: unknown) => {
        logger.error("wagent-studio-candidates", e);
        setCandError(errText(e));
        setCandidates([]);
      })
      .finally(() => setCandBusy(false));
  }, []);

  useEffect(() => {
    void loadCandidates();
  }, [loadCandidates]);

  const selectedCandidate = useMemo(
    () => candidates.find((c) => c.candidate_id === selectedCandidateId) ?? null,
    [candidates, selectedCandidateId],
  );

  /** 评审/发布后回读真实状态（禁本地推进状态机）。 */
  const refreshCandidate = useCallback((id: string) => {
    listWagentCandidates()
      .then((page) => {
        const list = page.candidates ?? [];
        setCandidates(list);
        const fresh = list.find((c) => c.candidate_id === id);
        if (fresh) setSelectedCandidateId(fresh.candidate_id);
      })
      .catch((e: unknown) => {
        logger.error("wagent-studio-refresh", e);
        setCandError(errText(e));
      });
  }, []);

  // ── ① 创建目标 ──
  const submitGoal = useCallback(() => {
    if (!goalName.trim()) return;
    setGoalBusy(true);
    setGoalError(null);
    setGoalId(null);
    createWagentGoal({
      name: goalName.trim(),
      goalType: goalIntent,
      scopeJson: goalScope.trim() ? JSON.stringify({ scope: goalScope.trim() }) : undefined,
    })
      .then((g) => {
        setGoalId(g.goal_id);
        setStep(2);
      })
      .catch((e: unknown) => {
        logger.error("wagent-studio-goal", e);
        setGoalError(errText(e));
      })
      .finally(() => setGoalBusy(false));
  }, [goalName, goalIntent, goalScope]);

  // ── ② 装配候选（初始状态恒 generated，服务端语义）──
  const assembleCandidate = useCallback(() => {
    setAssembleBusy(true);
    setAssembleError(null);
    setCreatedCandidate(null);
    createWagentCandidate({
      candidateType: "KNOWLEDGE_ITEM",
      targetEngine: "kb",
      payloadJson: JSON.stringify({
        goalId,
        intent: goalIntent,
        tools: selectedTools,
        goalName: goalName.trim(),
      }),
      targetObjectRefJson: targetObjectRef.trim() ? targetObjectRef.trim() : undefined,
      batchId: `wagent-studio-${Date.now()}`,
    })
      .then((c) => {
        setCreatedCandidate(c);
        void loadCandidates();
      })
      .catch((e: unknown) => {
        logger.error("wagent-studio-candidate", e);
        setAssembleError(errText(e));
      })
      .finally(() => setAssembleBusy(false));
  }, [goalId, goalIntent, goalName, selectedTools, targetObjectRef, loadCandidates]);

  // ── ③a 五段校验（generated/validating → validated；fail 停 validating）──
  const runValidation = useCallback(() => {
    if (!selectedCandidate) return;
    setValidateBusy(true);
    setValidateError(null);
    const id = selectedCandidate.candidate_id;
    validateWagentCandidate(id)
      .then((fresh) => {
        // 200 不等于通过：以服务端返回的 status 为准，禁本地判定
        setCandidates((prev) => prev.map((c) => (c.candidate_id === id ? fresh : c)));
      })
      .catch((e: unknown) => {
        logger.error("wagent-studio-validate", e);
        setValidateError(errText(e));
      })
      .finally(() => setValidateBusy(false));
  }, [selectedCandidate]);

  // ── ③b 评审通过（APPROVE 必带凭证；validated/review_pending 才可审）──
  const approveCandidate = useCallback(() => {
    if (!selectedCandidate) return;
    setReviewBusy(true);
    setReviewError(null);
    const id = selectedCandidate.candidate_id;
    const requester = tokenRequestedBy.trim() || "wagent-studio-generator";
    createApprovalToken({ scopeType: "CANDIDATE_PUBLISH", scopeId: id, requestedBy: requester })
      .then((issued) => {
        setIssuedToken(issued.token);
        return reviewWagentCandidate(id, {
          decision: "APPROVE",
          approvalToken: issued.token,
          commentText: reviewComment.trim() || undefined,
        });
      })
      .then(() => refreshCandidate(id))
      .catch((e: unknown) => {
        logger.error("wagent-studio-review", e);
        setReviewError(errText(e));
      })
      .finally(() => setReviewBusy(false));
  }, [selectedCandidate, tokenRequestedBy, reviewComment, refreshCandidate]);

  // ── ④a 签发发布凭证（admin；四眼 = 审批人 ≠ 申请人）──
  const issuePublishToken = useCallback(() => {
    if (!selectedCandidate) return;
    setTokenBusy(true);
    setTokenError(null);
    setIssuedToken(null);
    createApprovalToken({
      scopeType: "CANDIDATE_PUBLISH",
      scopeId: selectedCandidate.candidate_id,
      requestedBy: tokenRequestedBy.trim() || "wagent-studio-generator",
    })
      .then((issued) => setIssuedToken(issued.token))
      .catch((e: unknown) => {
        logger.error("wagent-studio-token", e);
        setTokenError(errText(e));
      })
      .finally(() => setTokenBusy(false));
  }, [selectedCandidate, tokenRequestedBy]);

  // ── ④b 发布（L3 真闭环；凭证一次性消费）──
  const publishCandidate = useCallback(() => {
    if (!selectedCandidate || !issuedToken) return;
    setPublishBusy(true);
    setPublishError(null);
    setPublishResult(null);
    const id = selectedCandidate.candidate_id;
    publishWagentCandidate(id, issuedToken)
      .then((r) => {
        setPublishResult(r);
        setIssuedToken(null);
        refreshCandidate(id);
      })
      .catch((e: unknown) => {
        logger.error("wagent-studio-publish", e);
        setPublishError(errText(e));
      })
      .finally(() => setPublishBusy(false));
  }, [selectedCandidate, issuedToken, refreshCandidate]);

  const stepLabels = [
    { icon: STEP_ICONS[0], label: t("aiworkbench.wagent.step.goal") },
    { icon: STEP_ICONS[1], label: t("aiworkbench.wagent.step.assemble") },
    { icon: STEP_ICONS[2], label: t("aiworkbench.wagent.step.review") },
    { icon: STEP_ICONS[3], label: t("aiworkbench.wagent.step.publish") },
  ];

  const canPublish = selectedCandidate?.status === "approved";
  const canReview = selectedCandidate ? REVIEWABLE.has(selectedCandidate.status) : false;
  const canValidate = selectedCandidate ? VALIDATABLE.has(selectedCandidate.status) : false;
  const inputCls = `${styles.inputBorder} ${styles.inputBg} ${styles.inputText} ${styles.placeholderText}`;
  const primaryBtn = `${styles.accentBg} ${styles.accentHover} ${styles.accentSolidText}`;
  const dangerBox = `${styles.dangerBorder} ${styles.dangerBg} ${styles.dangerText}`;
  const warnBox = `${styles.warningBorder} ${styles.warningBg} ${styles.warningText}`;

  return (
    <div className="flex-1 overflow-y-auto p-4 space-y-4" data-testid="wagent-studio">
      {/* 步骤指示 */}
      <div className={`rounded-lg border p-4 ${styles.cardBg} ${styles.cardBorder}`}>
        <div className="flex items-center gap-2 mb-3">
          <Bot className={`w-4 h-4 ${styles.accentText}`} />
          <h2 className={`text-sm font-bold ${styles.cardText}`}>{t("aiworkbench.wagent.title")}</h2>
          <span className={`text-[10px] ${styles.cardTextMuted}`}>{t("aiworkbench.wagent.subtitle")}</span>
        </div>
        <div className="flex items-center gap-1">
          {stepLabels.map((s, i) => {
            const Icon = s.icon;
            const active = step === i + 1;
            const done = step > i + 1;
            return (
              <React.Fragment key={i}>
                {i > 0 && <div className={`flex-1 h-0.5 ${done ? styles.accentBg : styles.cardBorder}`} />}
                <button
                  type="button"
                  onClick={() => setStep((i + 1) as WizardStep)}
                  className={`flex items-center gap-1 px-2 py-1 rounded text-[11px] cursor-pointer border ${
                    active ? `${primaryBtn} font-bold` : `${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`
                  }`}
                >
                  <Icon className="w-3 h-3" />
                  {s.label}
                </button>
              </React.Fragment>
            );
          })}
        </div>
      </div>

      {/* ① 目标 */}
      {step === 1 && (
        <div className={`rounded-lg border p-4 space-y-3 ${styles.cardBg} ${styles.cardBorder}`}>
          <div>
            <label className={`text-[10px] uppercase block mb-1 ${styles.cardTextMuted}`}>
              {t("aiworkbench.wagent.goal.name")}
            </label>
            <input
              value={goalName}
              onChange={(e) => setGoalName(e.target.value)}
              placeholder={t("aiworkbench.wagent.goal.name_placeholder")}
              className={`w-full p-2 rounded border text-xs ${inputCls}`}
            />
          </div>
          <div>
            <label className={`text-[10px] uppercase block mb-1 ${styles.cardTextMuted}`}>
              {t("aiworkbench.wagent.goal.intent")}
            </label>
            <select
              value={goalIntent}
              onChange={(e) => setGoalIntent(e.target.value)}
              className={`w-full p-2 rounded border text-xs ${styles.inputBorder} ${styles.inputBg} ${styles.inputText}`}
            >
              {WAGENT_GOAL_INTENTS.map((i) => (
                <option key={i} value={i}>
                  {i}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className={`text-[10px] uppercase block mb-1 ${styles.cardTextMuted}`}>
              {t("aiworkbench.wagent.goal.scope")}
            </label>
            <textarea
              value={goalScope}
              onChange={(e) => setGoalScope(e.target.value)}
              rows={2}
              placeholder={t("aiworkbench.wagent.goal.scope_placeholder")}
              className={`w-full p-2 rounded border text-xs ${inputCls}`}
            />
          </div>
          {goalError && (
            <div className={`flex items-start gap-2 p-2 rounded border text-xs ${dangerBox}`}>
              <AlertTriangle className="w-3.5 h-3.5 flex-shrink-0 mt-0.5" />
              <span>{goalError}</span>
            </div>
          )}
          <button
            type="button"
            disabled={!goalName.trim() || goalBusy}
            onClick={submitGoal}
            className={`px-4 py-1.5 rounded text-xs font-semibold cursor-pointer disabled:opacity-50 ${primaryBtn}`}
          >
            {goalBusy ? t("common.loading") : t("aiworkbench.wagent.goal.submit")}
          </button>
        </div>
      )}

      {/* ② 装配 */}
      {step === 2 && (
        <div className={`rounded-lg border p-4 space-y-3 ${styles.cardBg} ${styles.cardBorder}`}>
          <div className="flex items-center justify-between gap-2">
            <span className={`text-xs font-bold ${styles.cardText}`}>{t("aiworkbench.wagent.assemble.tools")}</span>
            <div className="flex items-center gap-1">
              <input
                value={toolKeyword}
                onChange={(e) => setToolKeyword(e.target.value)}
                placeholder={t("aiworkbench.wagent.assemble.keyword")}
                className={`px-2 py-1 rounded border text-[11px] w-40 ${inputCls}`}
              />
              <button
                type="button"
                onClick={() => loadTools(toolKeyword)}
                className={`inline-flex items-center gap-1 px-2 py-1 rounded border text-[11px] cursor-pointer ${styles.cardBorder} ${styles.cardText}`}
              >
                <RefreshCw className="w-3 h-3" />
                {t("common.status.retry")}
              </button>
            </div>
          </div>
          {toolsBusy && <p className={`text-xs ${styles.cardTextMuted}`}>{t("common.loading")}</p>}
          {toolsError && (
            <div className={`flex items-start gap-2 p-2 rounded border text-xs ${dangerBox}`}>
              <AlertTriangle className="w-3.5 h-3.5 flex-shrink-0 mt-0.5" />
              <span>{toolsError}</span>
            </div>
          )}
          {!toolsBusy && !toolsError && tools.length === 0 && (
            <div className={`rounded border p-4 flex flex-col items-center gap-1 ${styles.cardBorder}`}>
              <Inbox className="w-4 h-4 opacity-30" />
              <span className={`text-xs ${styles.cardTextMuted}`}>{t("aiworkbench.wagent.assemble.empty")}</span>
            </div>
          )}
          <div className="grid grid-cols-2 gap-2 max-h-52 overflow-y-auto">
            {tools.map((tool) => {
              const key = `${tool.name}@${tool.version}`;
              const selected = selectedTools.includes(key);
              return (
                <button
                  type="button"
                  key={key}
                  onClick={() =>
                    setSelectedTools((prev) => (selected ? prev.filter((k) => k !== key) : [...prev, key]))
                  }
                  className={`text-left p-2 rounded border text-xs cursor-pointer ${
                    selected ? `${styles.accentBorder} ${styles.badgeBg}` : `${styles.cardBorder} ${styles.cardBg}`
                  }`}
                >
                  <div className={`font-semibold ${styles.cardText}`}>{tool.name}</div>
                  <div className={`text-[10px] ${styles.cardTextMuted}`}>
                    {tool.engine} · {tool.category} · {tool.status}
                  </div>
                </button>
              );
            })}
          </div>
          <div>
            <label className={`text-[10px] uppercase block mb-1 ${styles.cardTextMuted}`}>
              {t("aiworkbench.wagent.assemble.target_ref")}
            </label>
            <textarea
              value={targetObjectRef}
              onChange={(e) => setTargetObjectRef(e.target.value)}
              rows={2}
              placeholder={t("aiworkbench.wagent.assemble.target_ref_placeholder")}
              className={`w-full p-2 rounded border text-[11px] font-mono ${inputCls}`}
            />
            <p className={`text-[10px] mt-1 ${styles.cardTextMuted}`}>
              {t("aiworkbench.wagent.assemble.target_ref_hint")}
            </p>
          </div>
          {assembleError && (
            <div className={`flex items-start gap-2 p-2 rounded border text-xs ${dangerBox}`}>
              <AlertTriangle className="w-3.5 h-3.5 flex-shrink-0 mt-0.5" />
              <span>{assembleError}</span>
            </div>
          )}
          {createdCandidate && (
            <div className={`p-2 rounded border text-xs space-y-0.5 ${styles.infoBorder} ${styles.infoBg} ${styles.infoText}`}>
              <div className="font-mono break-all">{createdCandidate.candidate_id}</div>
              <div>status={createdCandidate.status}</div>
              {createdCandidate.target_object_ref_json && (
                <div className="font-mono break-all">
                  {t("aiworkbench.wagent.assemble.target_ref_saved")}
                  {createdCandidate.target_object_ref_json}
                </div>
              )}
            </div>
          )}
          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={selectedTools.length === 0 || assembleBusy}
              onClick={assembleCandidate}
              className={`px-4 py-1.5 rounded text-xs font-semibold cursor-pointer disabled:opacity-50 ${primaryBtn}`}
            >
              {assembleBusy ? t("common.loading") : t("aiworkbench.wagent.assemble.submit")}
            </button>
            <button
              type="button"
              onClick={() => setStep(3)}
              className={`px-3 py-1.5 rounded border text-xs cursor-pointer ${styles.cardBorder} ${styles.cardText}`}
            >
              {t("aiworkbench.wagent.next")}
            </button>
          </div>
        </div>
      )}

      {/* ③ 评审：候选库选择 + 四眼评审 */}
      {step === 3 && (
        <div className={`rounded-lg border p-4 space-y-3 ${styles.cardBg} ${styles.cardBorder}`}>
          <div className="flex items-center justify-between">
            <span className={`text-xs font-bold ${styles.cardText}`}>{t("aiworkbench.wagent.review.pick")}</span>
            <button
              type="button"
              onClick={loadCandidates}
              className={`inline-flex items-center gap-1 px-2 py-1 rounded border text-[11px] cursor-pointer ${styles.cardBorder} ${styles.cardText}`}
            >
              <RefreshCw className="w-3 h-3" />
              {t("common.status.retry")}
            </button>
          </div>
          {candBusy && <p className={`text-xs ${styles.cardTextMuted}`}>{t("common.loading")}</p>}
          {candError && (
            <div className={`flex items-start gap-2 p-2 rounded border text-xs ${dangerBox}`}>
              <AlertTriangle className="w-3.5 h-3.5 flex-shrink-0 mt-0.5" />
              <span>{candError}</span>
            </div>
          )}
          {!candBusy && !candError && candidates.length === 0 && (
            <div className={`rounded border p-4 flex flex-col items-center gap-1 ${styles.cardBorder}`}>
              <Inbox className="w-4 h-4 opacity-30" />
              <span className={`text-xs ${styles.cardTextMuted}`}>{t("aiworkbench.wagent.review.empty")}</span>
            </div>
          )}
          <div className="space-y-1 max-h-48 overflow-y-auto" data-testid="wagent-candidate-list">
            {candidates.map((c) => {
              const active = selectedCandidateId === c.candidate_id;
              return (
                <button
                  type="button"
                  key={c.candidate_id}
                  onClick={() => setSelectedCandidateId(c.candidate_id)}
                  className={`w-full text-left p-2 rounded border text-[11px] cursor-pointer ${
                    active ? `${styles.accentBorder} ${styles.badgeBg}` : `${styles.cardBorder} ${styles.cardBg}`
                  }`}
                >
                  <div className={`font-mono break-all ${styles.cardText}`}>{c.candidate_id}</div>
                  <div className={styles.cardTextMuted}>
                    {c.candidate_type} · status={c.status}
                  </div>
                </button>
              );
            })}
          </div>
          {/* ③a 五段校验：generated → validating/validated（评审前必经） */}
          <div className={`p-2 rounded border text-[11px] space-y-2 ${warnBox}`}>
            <div>{t("aiworkbench.wagent.review.gap")}</div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                data-testid="wagent-validate"
                disabled={!canValidate || validateBusy}
                onClick={runValidation}
                className={`px-3 py-1 rounded border text-[11px] font-semibold cursor-pointer disabled:opacity-50 ${styles.cardBorder} ${styles.cardText}`}
              >
                {validateBusy ? t("common.loading") : t("aiworkbench.wagent.review.validate")}
              </button>
              {selectedCandidate && (
                <span className="font-mono">
                  {t("aiworkbench.wagent.review.five_segments")}:{" "}
                  {(["structure", "reference", "conflict", "impact", "regression"] as const)
                    .map((k) => `${k}=${selectedCandidate.validation?.[k] ?? "-"}`)
                    .join(" / ")}
                </span>
              )}
            </div>
            {validateError && (
              <div className={`flex items-start gap-2 p-2 rounded border ${dangerBox}`}>
                <AlertTriangle className="w-3.5 h-3.5 flex-shrink-0 mt-0.5" />
                <span>{validateError}</span>
              </div>
            )}
          </div>
          <div>
            <label className={`text-[10px] uppercase block mb-1 ${styles.cardTextMuted}`}>
              {t("aiworkbench.wagent.requested_by")}
            </label>
            <input
              value={tokenRequestedBy}
              onChange={(e) => setTokenRequestedBy(e.target.value)}
              placeholder={t("aiworkbench.wagent.requested_by_placeholder")}
              className={`w-full p-2 rounded border text-xs font-mono ${inputCls}`}
            />
            <p className={`text-[10px] mt-1 ${styles.cardTextMuted}`}>{t("aiworkbench.wagent.four_eyes")}</p>
          </div>
          <textarea
            value={reviewComment}
            onChange={(e) => setReviewComment(e.target.value)}
            rows={2}
            placeholder={t("aiworkbench.wagent.review.comment")}
            className={`w-full p-2 rounded border text-xs ${inputCls}`}
          />
          {reviewError && (
            <div className={`flex items-start gap-2 p-2 rounded border text-xs ${dangerBox}`}>
              <AlertTriangle className="w-3.5 h-3.5 flex-shrink-0 mt-0.5" />
              <span>{reviewError}</span>
            </div>
          )}
          <div className="flex items-center gap-2">
            <button
              type="button"
              disabled={!canReview || reviewBusy}
              onClick={approveCandidate}
              className={`px-4 py-1.5 rounded text-xs font-semibold cursor-pointer disabled:opacity-50 ${primaryBtn}`}
            >
              {reviewBusy ? t("common.loading") : t("aiworkbench.wagent.review.approve")}
            </button>
            <button
              type="button"
              onClick={() => setStep(4)}
              className={`px-3 py-1.5 rounded border text-xs cursor-pointer ${styles.cardBorder} ${styles.cardText}`}
            >
              {t("aiworkbench.wagent.next")}
            </button>
          </div>
          {selectedCandidate && !canReview && (
            <p className={`text-[11px] ${styles.cardTextMuted}`}>
              {t("aiworkbench.wagent.review.not_reviewable")} status={selectedCandidate.status}
            </p>
          )}
        </div>
      )}

      {/* ④ 发布 */}
      {step === 4 && (
        <div className={`rounded-lg border p-4 space-y-3 ${styles.cardBg} ${styles.cardBorder}`}>
          <div className={`text-xs ${styles.cardText}`}>
            <span className={styles.cardTextMuted}>candidate: </span>
            <span className="font-mono break-all">
              {selectedCandidate?.candidate_id ?? t("aiworkbench.wagent.publish.none")}
            </span>
            {selectedCandidate && (
              <span className={`ml-2 ${styles.cardTextMuted}`}>status={selectedCandidate.status}</span>
            )}
          </div>
          {!canPublish && (
            <div className={`p-2 rounded border text-[11px] ${warnBox}`}>
              {t("aiworkbench.wagent.publish.need_approved")}
            </div>
          )}
          <button
            type="button"
            disabled={!canPublish || tokenBusy}
            onClick={issuePublishToken}
            className={`px-4 py-1.5 rounded text-xs font-semibold cursor-pointer disabled:opacity-50 ${primaryBtn}`}
          >
            {tokenBusy ? t("common.loading") : t("aiworkbench.wagent.publish.issue_token")}
          </button>
          {tokenError && (
            <div className={`flex items-start gap-2 p-2 rounded border text-xs ${dangerBox}`}>
              <AlertTriangle className="w-3.5 h-3.5 flex-shrink-0 mt-0.5" />
              <span>{tokenError}</span>
            </div>
          )}
          {issuedToken && (
            <>
              <div
                className={`p-2 rounded border text-[11px] font-mono break-all ${styles.inputBorder} ${styles.inputBg} ${styles.inputText}`}
                data-testid="wagent-approval-token"
              >
                {issuedToken}
              </div>
              <button
                type="button"
                disabled={publishBusy}
                onClick={publishCandidate}
                className={`px-4 py-1.5 rounded text-xs font-semibold cursor-pointer disabled:opacity-50 ${primaryBtn}`}
              >
                {publishBusy ? t("common.loading") : t("aiworkbench.wagent.publish.confirm")}
              </button>
            </>
          )}
          {publishError && (
            <div className={`flex items-start gap-2 p-2 rounded border text-xs ${dangerBox}`}>
              <AlertTriangle className="w-3.5 h-3.5 flex-shrink-0 mt-0.5" />
              <span>{publishError}</span>
            </div>
          )}
          {publishResult && (
            <div
              className={`p-2 rounded border text-xs space-y-0.5 ${styles.successBorder} ${styles.successBg} ${styles.successText}`}
              data-testid="wagent-publish-result"
            >
              <div className="flex items-center gap-1 font-semibold">
                <CheckCircle2 className="w-3.5 h-3.5" />
                {t("aiworkbench.wagent.publish.ok")} status={publishResult.status}
              </div>
              <div className="font-mono break-all">gitRef: {publishResult.gitRef ?? "-"}</div>
              <div className="font-mono break-all">traceId: {publishResult.traceId}</div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
