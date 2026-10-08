/**
 * DetectTab — 场景可用 Mind 探测（E1 detect）— design-05 B.4
 *
 * <p>数据源：`GET /api/v1/business/scenarios/{id}/cognition/detect`，调用
 * {@link detectScenarioMinds}（经 `services/cognitiveClient` 单源，auth 经
 * `services/auth.getAuthToken()`）。空列表 → "该场景未绑定 Mind" 空态（不是错误，
 * PRD-05 REQ-COG-01 §1.1）。错误矩阵按 {@link CognitiveApiError.code} 渲染。</p>
 *
 * <p>每 Mind chip：`name` + `capabilityMask[]`（chips）+ `closedLoopBounds[]`
 * （PRE/POST chips）+ `isActive` 标记（成功色圆点）。禁自绘 SVG；图标仅 lucide。</p>
 *
 * @license Apache-2.0
 */
import React from "react";
import { Microscope, ShieldX, Ban } from "lucide-react";
import { useTheme } from "../../../components/ThemeContext";
import { useLanguage } from "../../../components/LanguageContext";
import type { MindLoadState } from "./CognitiveRightPanel";
import { CognitiveSkeleton, CognitiveErrorState, cognitiveErrorTitleKey } from "./CognitionStateViews";
export interface DetectTabProps {
  scenarioId: string;
  mindId?: string;
  minds: MindLoadState;
  onRetryMinds: () => void;
}

export default function DetectTab({ scenarioId, minds, onRetryMinds }: DetectTabProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const renderError = () => {
    if (!minds.error) return null;
    // design-05 B.5 / F05-16 要点 2：501 + stub=true 是"能力未实现"占位态，不是故障态，
    // 必须与红色错误 banner 视觉可分，且不渲染任何假数据。
    if (minds.error.stub) {
      return (
        <div
          role="status"
          data-testid="cog-stub-placeholder"
          className={`flex items-start gap-2 p-4 rounded-md border ${styles.infoBg} ${styles.infoBorder} ${styles.infoText} text-[11px]`}
        >
          <Ban size={13} className="mt-0.5 shrink-0" />
          <div>
            <span className="font-bold">{t("scenario.cognition.stub")}</span>
            {minds.error.message && (
              <p className={`mt-1 font-mono text-[10px] ${styles.muted} break-all`}>
                {minds.error.message}
              </p>
            )}
          </div>
        </div>
      );
    }
    // 故障/未启用/schema 未就绪等错误码一律经共享故障态渲染（红色 danger + traceId 复制 + 重试），
    // 与「空」态（muted ShieldX）视觉严格可分（B.5）。
    return (
      <CognitiveErrorState
        error={minds.error}
        titleKey={cognitiveErrorTitleKey(minds.error)}
        onRetry={onRetryMinds}
      />
    );
  };

  const renderEmpty = () => (
    <div className={`p-4 flex items-center gap-2 ${styles.muted} text-[11px]`} role="status">
      <ShieldX size={13} />
      <span>{t("scenario.cognition.scenarioNoBind")}</span>
    </div>
  );

  const renderRow = (row: {
    mindId: string;
    name: string;
    capabilityMask?: string[];
    closedLoopBounds?: Array<"PRE" | "POST">;
    isActive?: boolean;
  }) => {
    const caps = Array.isArray(row.capabilityMask) ? row.capabilityMask : [];
    const bounds = Array.isArray(row.closedLoopBounds) ? row.closedLoopBounds : [];
    return (
      <div
        key={row.mindId}
        role="listitem"
        aria-label={row.name}
        className={`flex flex-col gap-2 p-3 ${styles.inputBg} border ${styles.inputBorder} rounded-md`}
      >
        <div className="flex items-center gap-2">
          <span
            className={`h-1.5 w-1.5 rounded-full ${row.isActive ? styles.successText : styles.muted}`}
            aria-hidden
          />
          <span className={`text-[11px] font-bold ${styles.cardText}`}>{row.name}</span>
          {row.isActive && (
            <span className={`text-[9px] font-mono px-1.5 py-0.5 rounded ${styles.successBg} ${styles.successText}`}>
              {t("scenario.cognition.active")}
            </span>
          )}
          <span className={`ml-auto text-[9px] font-mono ${styles.muted}`}>{row.mindId}</span>
        </div>
        {caps.length > 0 && (
          <div className="flex flex-wrap items-center gap-1.5">
            <span className={`text-[9px] font-bold uppercase tracking-wider ${styles.muted}`}>
              {t("scenario.cognition.capMask")}
            </span>
            {caps.map((c) => (
              <span
                key={c}
                className={`text-[10px] px-1.5 py-0.5 rounded ${styles.badgeBg} ${styles.cardText}`}
              >
                {c}
              </span>
            ))}
          </div>
        )}
        {bounds.length > 0 && (
          <div className="flex flex-wrap items-center gap-1.5">
            <span className={`text-[9px] font-bold uppercase tracking-wider ${styles.muted}`}>
              {t("scenario.cognition.loopBounds")}
            </span>
            {bounds.map((b) => (
              <span
                key={b}
                className={`text-[10px] font-mono px-1.5 py-0.5 rounded ${styles.badgeBg} ${styles.cardTextMuted}`}
              >
                {b}
              </span>
            ))}
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="space-y-3 p-3">
      <div className="flex items-center gap-2">
        <Microscope size={13} className={styles.muted} />
        <span className={`text-[10px] font-bold uppercase tracking-wider ${styles.muted}`}>
          {t("scenario.cognition.detect")} · {scenarioId}
        </span>
      </div>
      {minds.loading && <CognitiveSkeleton rows={3} />}
      {renderError()}
      {!minds.loading && !minds.error && minds.rows.length === 0 && renderEmpty()}
      {!minds.loading && !minds.error && minds.rows.length > 0 && (
        <div className="space-y-2" role="list" aria-label={t("scenario.cognition.detect")}>{minds.rows.map(renderRow)}</div>
      )}
    </div>
  );
}
