/**
 * AttentionPanel — 「需要你关注」（原型 v2 右下面板）。
 *
 * 原型的"待处理事项"没有对应聚合端点，这里用两条真实数据拼成：
 *  ① 场景运行条件预检的失败项（`POST /scenarios/{id}/pre-validate`，`pass=false` 的行）
 *  ② 认知 Mind 的待复核提案入口（Mind 治理列表，点进去做复核）
 *
 * 注意 `PreValidateCheck.detail` 是后端中文硬编码的技术明细，
 * 只作原始明细展示（title 属性），业务文案一律走 i18n 按 `name` 映射；
 * 未知 name 直接显示原文，不猜语义。
 *
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useState } from "react";
import { AlertTriangle, Brain } from "lucide-react";
import { Link } from "react-router-dom";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { NoAccessError } from "../../services/httpClient";
import { preValidateScenario, type PreValidateCheck } from "../../services/decisionWorkspaceApi";
import logger from "../../services/logger";
import type { AsyncState } from "./decisionTypes";

interface AttentionItem {
  id: string;
  kind: "precheck" | "mind";
  /** i18n 键（precheck 已知项）或原文（未知项/后端给的 name） */
  labelKey?: string;
  rawLabel?: string;
  detail?: string;
  href?: string;
}

const PRECHECK_LABEL_KEYS: Record<string, string> = {
  mind: "platform.decision.attention.precheck.mind",
  sandbox_coverage: "platform.decision.attention.precheck.sandbox",
  bindings_reachability: "platform.decision.attention.precheck.bindings",
};

export interface AttentionPanelProps {
  /** 当前选中场景（无场景时只显示 Mind 入口） */
  scenarioId: string | null;
}

export default function AttentionPanel({ scenarioId }: AttentionPanelProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [state, setState] = useState<AsyncState<AttentionItem[]>>({ kind: "loading" });

  const load = useCallback(async () => {
    setState({ kind: "loading" });
    const items: AttentionItem[] = [];
    let denied = false;
    let failed = false;
    let firstError = "";

    if (scenarioId) {
      try {
        const checks = await preValidateScenario(scenarioId);
        for (const c of collectFailures(checks)) {
          items.push({
            id: `precheck-${c.name}`,
            kind: "precheck",
            labelKey: PRECHECK_LABEL_KEYS[c.name],
            rawLabel: PRECHECK_LABEL_KEYS[c.name] ? undefined : c.name,
            detail: c.detail,
            href: `/workbench/${scenarioId}`,
          });
        }
      } catch (e) {
        if (e instanceof NoAccessError) denied = true;
        else {
          failed = true;
          firstError = e instanceof Error ? e.message : String(e);
          logger.error("decision-attention-precheck", e);
        }
      }
    }

    // Mind 治理入口：始终保留（无提案时用户仍需要进治理列表）
    items.push({
      id: "mind-governance",
      kind: "mind",
      labelKey: "platform.decision.attention.mindEntry",
      href: "/decision/mind",
    });

    if (denied) {
      setState({ kind: "denied" });
      return;
    }
    // 预检失败不阻断 Mind 入口的展示，但要把失败如实带出
    if (failed && items.length === 1) {
      setState({ kind: "error", message: firstError });
      return;
    }
    setState({ kind: "ready", value: items });
  }, [scenarioId]);

  useEffect(() => {
    void load();
  }, [load]);

  const body = (() => {
    if (state.kind === "loading" || state.kind === "idle") {
      return <p className={`py-5 text-center text-xs ${styles.cardTextMuted}`}>{t("platform.decision.task.loading")}</p>;
    }
    if (state.kind === "denied") {
      return <p className={`py-5 text-center text-xs ${styles.warningText}`}>{t("platform.decision.task.denied")}</p>;
    }
    if (state.kind === "error") {
      return (
        <p className={`py-3 text-[11px] break-words ${styles.dangerText}`}>
          {t("platform.decision.loadFailed")}{state.message}
        </p>
      );
    }
    if (state.value.length === 0) {
      return <p className={`py-5 text-center text-xs ${styles.cardTextMuted}`}>{t("platform.decision.attention.empty")}</p>;
    }
    return (
      <ul>
        {state.value.map((item) => (
          <li key={item.id} className={`py-2.5 border-b last:border-b-0 ${styles.cardBorder}`}>
            <div className="flex items-start gap-2.5">
              <span
                className={`mt-0.5 inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full ${
                  item.kind === "precheck"
                    ? `${styles.warningBg} ${styles.warningText}`
                    : `${styles.badgeBg} ${styles.badgeText}`
                }`}
              >
                {item.kind === "precheck" ? <AlertTriangle size={12} /> : <Brain size={12} />}
              </span>
              <div className="min-w-0 flex-1">
                <strong className={`block text-xs break-words ${styles.cardText}`}>
                  {item.labelKey ? t(item.labelKey) : item.rawLabel}
                </strong>
                {item.detail && (
                  <small className={`block mt-0.5 text-[11px] break-words ${styles.cardTextMuted}`} title={item.detail}>
                    {item.detail}
                  </small>
                )}
              </div>
              {item.href && (
                <Link to={item.href} className={`shrink-0 text-[11px] font-bold ${styles.accentText}`}>
                  {t("platform.decision.attention.open")}
                </Link>
              )}
            </div>
          </li>
        ))}
      </ul>
    );
  })();

  return (
    <section className={`rounded-xl border p-4 ${styles.cardBg} ${styles.cardBorder}`}>
      <div className="flex items-center justify-between gap-2 mb-1">
        <h2 className={`text-xs font-bold ${styles.cardText}`}>{t("platform.decision.attention.title")}</h2>
        <button type="button" onClick={load} className={`text-[11px] cursor-pointer ${styles.accentText}`}>
          {t("platform.decision.continue.refresh")}
        </button>
      </div>
      {body}
    </section>
  );
}

function collectFailures(checks: PreValidateCheck[]): PreValidateCheck[] {
  return Array.isArray(checks) ? checks.filter((c) => c && c.pass === false) : [];
}
