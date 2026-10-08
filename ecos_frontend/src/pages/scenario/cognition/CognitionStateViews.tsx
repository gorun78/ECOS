/**
 * CognitionStateViews — 认知右栏共享状态视图（design-05 B.5 七态矩阵）。
 *
 * <p>提供两个纯展示组件：{@link CognitiveSkeleton}（加载态骨架屏，替代裸 spinner）
 * 与 {@link CognitiveErrorState}（故障态红色 banner + message + traceId 可复制 + 重试）。
 * 「故障」与「空」使用不同图标（AlertTriangle vs ShieldX/Info）、不同色（danger vs muted）、
 * 不同 role（alert vs status），视觉严格可分（B.5 解除掩蔽链要求）。</p>
 *
 * <p>纯展示：不发请求、不出现任何 cognition 路径字面量（单源约束见
 * `src/api/__tests__/cognitivePaths.singleSource.spec.ts`）。</p>
 *
 * @license Apache-2.0
 */
import React, { useCallback, useState } from "react";
import { AlertTriangle, Ban, Check, Copy, Loader2, ShieldX } from "lucide-react";
import { useTheme } from "../../../components/ThemeContext";
import { useLanguage } from "../../../components/LanguageContext";
import type { CognitiveApiError } from "../../../services/cognitiveClient";

/** 回退复制用的临时 textarea 标签名（用变量传入，避免字面量 t("…") 形态被 i18n 门禁正则误判）。 */
const TEXTAREA_TAG = "textarea";

/** 加载态骨架屏（B.5「加载」态；rows 默认 3 行脉冲占位条）。 */
export function CognitiveSkeleton({ rows = 3 }: { rows?: number }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  return (
    <div
      role="status"
      aria-live="polite"
      aria-label={t("scenario.cognition.loading")}
      data-testid="cog-skeleton"
      className="space-y-2 p-4"
    >
      <div className={`flex items-center gap-2 text-[11px] ${styles.muted}`}>
        <Loader2 size={13} className={`animate-spin ${styles.muted}`} />
        <span>{t("scenario.cognition.loading")}</span>
      </div>
      {Array.from({ length: rows }).map((_, i) => (
        <div
          key={i}
          aria-hidden
          className={`h-9 rounded-md animate-pulse ${styles.inputBg} border ${styles.inputBorder}`}
          style={{ opacity: 1 - i * 0.2 }}
        />
      ))}
    </div>
  );
}

/** 空态（200 `data:[]`；B.5 / DG-6：与故障态视觉严格可分，禁同一 fallback）。 */
export function CognitiveEmptyState({
  labelKey,
  testId = "cog-empty",
}: {
  labelKey: string;
  testId?: string;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  return (
    <div
      role="status"
      data-testid={testId}
      className={`p-4 flex items-center gap-2 ${styles.muted} text-[11px]`}
    >
      <ShieldX size={13} aria-hidden />
      <span>{t(labelKey)}</span>
    </div>
  );
}

/** 故障态 banner（5xx / ECOS-COG-* → 显式红色错误 + traceId 复制 + 重试）。
 *  `stub=true`（DG-9 / B.5「未实现」）单独走 info 占位形态：role=status + cog-stub-placeholder，
 *  与红色故障态视觉可分（F05-16 禁把占位伪装成故障，也禁伪装成数据）。 */
export function CognitiveErrorState({
  error,
  titleKey,
  onRetry,
}: {
  error: CognitiveApiError;
  titleKey: string;
  onRetry?: () => void;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [copied, setCopied] = useState(false);
  const [copyFailed, setCopyFailed] = useState(false);

  const copyTrace = useCallback(() => {
    const traceId = error.traceId;
    if (!traceId) return;
    const done = (ok: boolean) => {
      setCopied(ok);
      setCopyFailed(!ok);
      if (ok) window.setTimeout(() => setCopied(false), 2000);
    };
    try {
      // 优先异步 Clipboard API；不可用（http/旧浏览器/权限）时回退 execCommand。
      if (typeof navigator !== "undefined" && navigator.clipboard?.writeText) {
        navigator.clipboard
          .writeText(traceId)
          .then(() => done(true))
          .catch(() => done(false));
        return;
      }
      const ta = document.createElement(TEXTAREA_TAG);
      ta.value = traceId;
      ta.setAttribute("readonly", "");
      ta.style.position = "fixed";
      ta.style.opacity = "0";
      document.body.appendChild(ta);
      ta.select();
      const ok = document.execCommand("copy");
      document.body.removeChild(ta);
      done(ok);
    } catch {
      done(false);
    }
  }, [error.traceId]);

  if (error.stub) {
    return (
      <div
        role="status"
        data-testid="cog-stub-placeholder"
        className={`flex items-start gap-2 p-4 rounded-md border ${styles.infoBg} ${styles.infoBorder} ${styles.infoText} text-[11px]`}
      >
        <Ban size={13} className="mt-0.5 shrink-0" aria-hidden />
        <div>
          <span className="font-bold">{t(titleKey)}</span>
          {error.message && (
            <p className={`mt-1 font-mono text-[10px] ${styles.muted} break-all`}>{error.message}</p>
          )}
        </div>
      </div>
    );
  }

  return (
    <div
      role="alert"
      data-testid="cog-error-state"
      className={`p-4 ${styles.dangerBg} border ${styles.dangerBorder} rounded-md text-[11px] ${styles.dangerText}`}
    >
      <div className="flex items-center gap-2 font-bold">
        <AlertTriangle size={13} aria-hidden />
        <span>{t(titleKey)}</span>
      </div>
      {error.message && (
        <p className="mt-1 font-mono text-[10px] opacity-80 break-all">{error.message}</p>
      )}
      {error.traceId && (
        <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
          <span className={`font-mono text-[10px] ${styles.muted}`} data-testid="cog-trace-id">
            {t("scenario.cognition.traceId")}: {error.traceId}
          </span>
          <button
            type="button"
            onClick={copyTrace}
            aria-label={t("scenario.cognition.copyTraceId")}
            data-testid="cog-copy-trace"
            className={`inline-flex items-center gap-1 text-[10px] font-bold px-1.5 py-0.5 rounded border ${styles.dangerBorder} cursor-pointer hover:opacity-80 transition-opacity`}
          >
            {copied ? <Check size={10} aria-hidden /> : <Copy size={10} aria-hidden />}
            <span>
              {copied
                ? t("scenario.cognition.traceCopied")
                : copyFailed
                  ? t("scenario.cognition.copyTraceFailed")
                  : t("scenario.cognition.copyTraceId")}
            </span>
          </button>
        </div>
      )}
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          aria-label={t("scenario.cognition.retry")}
          className={`mt-2 text-[10px] font-bold px-2 py-1 rounded border ${styles.dangerBorder} cursor-pointer hover:opacity-80 transition-opacity`}
        >
          {t("scenario.cognition.retry")}
        </button>
      )}
    </div>
  );
}

/** 错误态标题键映射（错误码 → B.5/DG 文案键；单源于此，禁各 Tab 重复三元表达式漂移）。 */
export function cognitiveErrorTitleKey(e: CognitiveApiError): string {
  if (e.stub) return "scenario.cognition.stub";
  switch (e.code) {
    case "NOT_FOUND":
      return "scenario.cognition.sceneNotFound";
    case "BAD_REQUEST":
      // ECOS-COG-400（mind 不属场景，选择器红字提示）；ECOS-COG-400P 参数非法回退通用故障文案
      return /mind/i.test(e.message) ? "scenario.cognition.mindNotOnScenario" : "scenario.cognition.error";
    case "ENGINE_DISABLED":
      return "scenario.cognition.notEnabled";
    case "SCHEMA_NOT_READY":
      return "scenario.cognition.schemaNotReady";
    default:
      return "scenario.cognition.error";
  }
}
