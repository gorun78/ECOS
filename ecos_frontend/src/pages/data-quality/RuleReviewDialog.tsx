/**
 * PMO-48-B T9 — 规则审核对话框
 *
 * 展示版本时间线（Approve 场景）+ 驳回原因必填输入（Reject 场景）。
 * 成功后 cb.onConfirmed() 由调用方重新拉取列表。
 */

import React, { useEffect, useState } from "react";
import { Loader2, History, X } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import {
  DqRuleVO,
  DqRuleVersionVO,
  dqRuleAction,
  fetchDqGovernanceVersions,
} from "./api";

interface RuleReviewDialogProps {
  visible: boolean;
  /** 审核动作：approve / reject（其它值不渲染） */
  action: "approve" | "reject";
  /** 目标规则（用于调 versions 取最新 v{n}） */
  rule: DqRuleVO | null;
  /** 当前操作用户名（写入 approver/rejector 字段） */
  operator: string;
  onClose: () => void;
  onConfirmed: () => void;
}

export default function RuleReviewDialog({
  visible,
  action,
  rule,
  operator,
  onClose,
  onConfirmed,
}: RuleReviewDialogProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [versions, setVersions] = useState<DqRuleVersionVO[]>([]);
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  // 打开时拉版本历史 + 重置表单
  useEffect(() => {
    if (!visible || !rule) return;
    setRejectReason("");
    setError("");
    setVersions([]);
    setVersionsLoading(true);
    let cancelled = false;
    (async () => {
      const v = await fetchDqGovernanceVersions(rule.id);
      if (cancelled) return;
      setVersions(Array.isArray(v) ? v : []);
      setVersionsLoading(false);
    })();
    return () => {
      cancelled = true;
    };
  }, [visible, rule?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!visible || !rule) return null;

  const latestVersion = versions.length > 0 ? versions[0].version : (rule.version ?? 1);
  const isReject = action === "reject";
  const canConfirm =
    !submitting && (!isReject || rejectReason.trim().length > 0);

  const onConfirm = async () => {
    if (!canConfirm) return;
    setSubmitting(true);
    setError("");
    const res = await dqRuleAction(
      rule.id,
      action,
      isReject
        ? { rejector: operator, reason: rejectReason.trim() }
        : { approver: operator }
    );
    setSubmitting(false);
    if (res.success) {
      onConfirmed();
      onClose();
    } else {
      setError(res.error ?? t("dw.dqRule.statusMachine.actionFailed"));
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center">
      <div className="absolute inset-0 bg-black/40" onClick={onClose} />
      <div
        className={`relative z-50 w-full max-w-2xl mx-4 rounded-xl shadow-2xl ${styles.cardBg} border ${styles.cardBorder} flex flex-col max-h-[85vh]`}
      >
        {/* 头部 */}
        <div className={`flex items-center justify-between px-5 py-4 border-b ${styles.cardBorder}`}>
          <div className="flex items-center gap-2">
            <History
              className={`w-4 h-4 ${isReject ? styles.dangerText : styles.accentText}`}
            />
            <span className={`font-bold text-sm ${styles.cardText}`}>
              {isReject
                ? t("dw.dqRule.statusMachine.reject")
                : t("dw.dqRule.statusMachine.approve")}
              {" · "}
              {rule.ruleName}
            </span>
            <span
              className={`font-mono text-[10px] ${styles.cardTextMuted} px-1.5 py-0.5 rounded-full ${styles.badgeBg} ${styles.badgeText}`}
            >
              v{latestVersion}
            </span>
          </div>
          <button
            onClick={onClose}
            className={`p-1 rounded hover:bg-black/5 dark:hover:bg-white/10 ${styles.cardTextMuted} cursor-pointer transition`}
            aria-label="close"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* 版本时间线 */}
        <div className="flex-1 overflow-y-auto px-5 py-4 space-y-3 min-h-0">
          <div className={`text-[11px] font-bold font-mono uppercase ${styles.cardTextMuted}`}>
            {t("dw.dqRule.statusMachine.versionTimeline")}
          </div>
          {versionsLoading ? (
            <div className={`flex items-center gap-2 py-6 justify-center ${styles.cardTextMuted}`}>
              <Loader2 className={`w-4 h-4 animate-spin ${styles.accentText}`} />
              <span className="text-xs">{t("dw.dqRule.loading")}</span>
            </div>
          ) : versions.length === 0 ? (
            <div className={`py-6 text-center text-xs ${styles.cardTextMuted}`}>
              {t("dw.dqRule.statusMachine.noVersions")}
            </div>
          ) : (
            <ol className="space-y-2">
              {versions.map((v, i) => (
                <li
                  key={`${v.ruleId}-v${v.version}`}
                  className={`p-3 rounded-lg border ${styles.cardBorder} ${styles.appBg} transition-colors ${
                    i === 0 ? `border-l-2 ${isReject ? styles.dangerBorder : styles.accentBorder}` : ""
                  }`}
                >
                  <div className="flex items-center gap-2 text-xs">
                    <span className={`font-bold font-mono ${styles.cardText}`}>
                      v{v.version}
                    </span>
                    <span className={`font-mono text-[10px] ${styles.cardTextMuted}`}>
                      {v.changedBy || "-"} · {v.changedAt || "-"}
                    </span>
                  </div>
                  {v.changeNote ? (
                    <div className={`mt-1.5 text-[11px] ${styles.cardText} whitespace-pre-wrap break-words`}>
                      {v.changeNote}
                    </div>
                  ) : null}
                </li>
              ))}
            </ol>
          )}

          {/* 驳回原因（仅 reject 显示，必填） */}
          {isReject && (
            <div className="space-y-1.5 pt-2">
              <label className={`text-[11px] font-bold font-mono uppercase ${styles.cardTextMuted}`}>
                {t("dw.dqRule.statusMachine.rejectReason")} *
              </label>
              <textarea
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                rows={3}
                placeholder={t("dw.dqRule.statusMachine.rejectReason")}
                className={`w-full px-3 py-2 text-xs border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} rounded-lg focus:outline-hidden focus:ring-1 focus:ring-red-500/50 resize-none`}
              />
            </div>
          )}

          {error ? (
            <div className={`text-xs ${styles.dangerText} mt-2 px-2`}>{error}</div>
          ) : null}
        </div>

        {/* 底部按钮 */}
        <div className={`flex items-center justify-end gap-2 px-5 py-3 border-t ${styles.cardBorder}`}>
          <button
            onClick={onClose}
            disabled={submitting}
            className={`px-4 py-1.5 rounded-lg text-xs font-semibold border ${styles.cardBorder} ${styles.cardText} hover:bg-black/5 dark:hover:bg-white/5 transition cursor-pointer disabled:opacity-40`}
          >
            {t("dw.dqRule.cancel")}
          </button>
          <button
            onClick={() => void onConfirm()}
            disabled={!canConfirm}
            className={`px-4 py-1.5 rounded-lg text-xs font-bold ${
              isReject
                ? `bg-red-600 hover:bg-red-700 text-white`
                : `${styles.accentBg} ${styles.accentHover} ${styles.cardText}`
            } transition cursor-pointer disabled:opacity-40`}
          >
            {submitting ? (
              <Loader2 className="w-3.5 h-3.5 inline animate-spin" />
            ) : (
              isReject
                ? t("dw.dqRule.statusMachine.reject")
                : t("dw.dqRule.statusMachine.approve")
            )}
          </button>
        </div>
      </div>
    </div>
  );
}
