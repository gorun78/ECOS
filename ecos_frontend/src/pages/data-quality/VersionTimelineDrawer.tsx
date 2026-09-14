/**
 * PMO-48-B T9 — 版本时间线抽屉（右侧）
 *
 * 列表行点「历史」图标打开。调 GET /rules/{id}/versions 倒序渲染。
 * 每条展示版本 v1/v2/v3 + changedBy + changedAt + changeNote，可展开看快照 JSON 美化。
 */

import React, { useEffect, useState } from "react";
import { ChevronDown, ChevronRight, History, Loader2, X } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import {
  DqRuleVO,
  DqRuleVersionVO,
  fetchDqGovernanceVersions,
} from "./api";

interface VersionTimelineDrawerProps {
  visible: boolean;
  /** 目标规则（null 时不渲染） */
  rule: DqRuleVO | null;
  onClose: () => void;
}

/** 快照 JSON 美化 */
function prettifySnapshot(raw: string): string {
  if (!raw) return "";
  try {
    return JSON.stringify(JSON.parse(raw), null, 2);
  } catch {
    // 后端未给 JSON 返回原文
    return raw;
  }
}

export default function VersionTimelineDrawer({
  visible,
  rule,
  onClose,
}: VersionTimelineDrawerProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [versions, setVersions] = useState<DqRuleVersionVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});

  useEffect(() => {
    if (!visible || !rule) return;
    setExpanded({});
    setVersions([]);
    setLoading(true);
    let cancelled = false;
    (async () => {
      const v = await fetchDqGovernanceVersions(rule.id);
      if (cancelled) return;
      // 倒序渲染：新 → 旧
      setVersions(Array.isArray(v) ? [...v].reverse() : []);
      setLoading(false);
    })();
    return () => {
      cancelled = true;
    };
  }, [visible, rule?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  if (!visible || !rule) return null;

  const toggle = (key: string) =>
    setExpanded((prev) => ({ ...prev, [key]: !prev[key] }));

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <div className="absolute inset-0 bg-black/30" onClick={onClose} />
      <aside
        className={`relative z-50 w-[440px] max-w-[90vw] h-full ${styles.cardBg} border-l ${styles.cardBorder} flex flex-col shadow-2xl`}
      >
        {/* 头部 */}
        <div className={`flex items-center justify-between px-5 py-4 border-b ${styles.cardBorder} shrink-0`}>
          <div className="flex items-center gap-2 min-w-0">
            <History className={`w-4 h-4 ${styles.accentText} shrink-0`} />
            <div className="min-w-0">
              <div className={`font-bold text-sm ${styles.cardText} truncate`}>
                {t("dw.dqRule.statusMachine.versionTimeline")}
              </div>
              <div className={`text-[10px] font-mono ${styles.cardTextMuted} truncate`}>
                {rule.ruleName} · {rule.ruleCode}
              </div>
            </div>
          </div>
          <button
            onClick={onClose}
            className={`p-1.5 rounded hover:bg-black/5 dark:hover:bg-white/10 ${styles.cardTextMuted} transition cursor-pointer`}
            aria-label="close"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* 内容 */}
        <div className="flex-1 overflow-y-auto p-4">
          {loading ? (
            <div className={`flex flex-col items-center py-16 gap-2 ${styles.cardTextMuted}`}>
              <Loader2 className={`w-5 h-5 animate-spin ${styles.accentText}`} />
              <span className="text-xs">{t("dw.dqRule.loading")}</span>
            </div>
          ) : versions.length === 0 ? (
            <div className={`flex flex-col items-center py-16 ${styles.cardTextMuted}`}>
              <History className={`w-10 h-10 opacity-30`} />
              <span className="mt-3 text-sm font-semibold">
                {t("dw.dqRule.statusMachine.noVersions")}
              </span>
            </div>
          ) : (
            <ol className="relative space-y-3 before:absolute before:inset-y-1 before:left-[12px] before:w-px before:bg-current before:opacity-20">
              {versions.map((v, i) => {
                const key = `${v.ruleId}-v${v.version}`;
                const isOpen = !!expanded[key];
                return (
                  <li key={key} className="relative pl-8">
                    <span
                      className={`absolute left-[7px] top-2 w-2.5 h-2.5 rounded-full border-2 ${styles.accentBorder} ${
                        i === 0 ? `${styles.accentBg}` : `${styles.appBg}`
                      }`}
                      aria-hidden
                    />
                    <div className={`p-3 rounded-lg border ${styles.cardBorder} ${styles.appBg}`}>
                      <button
                        onClick={() => toggle(key)}
                        className="w-full flex items-center gap-2 cursor-pointer text-left"
                        aria-expanded={isOpen}
                      >
                        {isOpen ? (
                          <ChevronDown className={`w-3.5 h-3.5 ${styles.cardTextMuted} shrink-0`} />
                        ) : (
                          <ChevronRight className={`w-3.5 h-3.5 ${styles.cardTextMuted} shrink-0`} />
                        )}
                        <span className={`text-xs font-bold font-mono ${styles.cardText}`}>
                          v{v.version}
                        </span>
                        <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
                          {v.changedBy || "-"}
                        </span>
                        <span className={`ml-auto text-[10px] font-mono ${styles.cardTextMuted}`}>
                          {v.changedAt || "-"}
                        </span>
                      </button>
                      {v.changeNote ? (
                        <div className={`mt-2 pl-5 text-[11px] ${styles.cardText} whitespace-pre-wrap break-words`}>
                          <span className={`font-bold ${styles.cardTextMuted} font-mono uppercase text-[10px] mr-1`}>
                            {t("dw.dqRule.statusMachine.changeNote")}:
                          </span>
                          {v.changeNote}
                        </div>
                      ) : null}
                      {isOpen && (
                        <pre
                          className={`mt-2 p-3 rounded-lg ${styles.appBg} border ${styles.cardBorder} text-[10px] font-mono ${styles.cardTextMuted} overflow-x-auto max-h-64`}
                        >
                          <span className={`text-[9px] uppercase ${styles.cardTextMuted} opacity-60 mr-2 font-bold`}>
                            {t("dw.dqRule.statusMachine.snapshot")}:
                          </span>
                          {prettifySnapshot(v.snapshotJson)}
                        </pre>
                      )}
                    </div>
                  </li>
                );
              })}
            </ol>
          )}
        </div>
      </aside>
    </div>
  );
}
