/**
 * RunProgress — 运行进度时间线（详设-08 §2.2「流式进度」+ §2.3「运行中」行 + §3.4 承流控制流）。
 *
 * 通道契约：
 * - `progressChannel`（"sse" | "poll"）由**服务端决定**并随 run 回执下发（§3.4），前端不自行选择；
 * - poll 通道必须显示「降级：非实时（轮询 N 秒）」，不得声称流式（§2.2/W192 C174）；
 * - SSE 断线计数 `sseDisconnectCount` 达到 3 时自动按轮询形态渲染并回调 `onFallbackToPoll`
 *   （§4.4 断线 3 次自动切轮询），计数由持有 EventSource 的调用方上报。
 *
 * 取消语义（§5.2 / 卷 10 F10-21）：
 * - 取消 = Kill Switch，端点 `POST /api/v1/wagent/kill-switch`（经 BFF/gateway，不可逆）；
 * - 本组件默认经 `services/httpClient#apiFetch` 收敛层发起该 POST（组件内不裸写 fetch），
 *   并由公共收敛版 ConfirmDialog 做不可逆二次确认；传入 `onCancel` 时改由调用方发请求
 *   （调用方同样只准走 httpClient/api 收敛层）。
 *
 * 真值纪律：步骤与状态只从 props 传入（引擎端点消费结果），组件不合成进度。
 * @license Apache-2.0
 */

import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Ban, Check, Clock, AlertTriangle, Loader2, X } from "lucide-react";
import { useTheme } from "../../ThemeContext";
import { useLanguage } from "../../LanguageContext";
import Button from "../Button";
import ConfirmDialog from "./ConfirmDialog";
import { apiFetch } from "../../../services/httpClient";

/** 进度通道：服务端决定，"sse" 实时流 / "poll" 轮询兜底 */
export type ProgressChannel = "sse" | "poll";

/** 单个运行步骤（Run 状态机投影，来自引擎端点） */
export interface RunStep {
  id: string;
  /** 步骤名（业务语言，引擎返回） */
  label: string;
  /** 步骤状态；waiting_user 对应 require_approval 等待点 */
  state: "pending" | "running" | "done" | "failed" | "waiting_user";
}

/** Kill Switch 端点路径（httpClient 会补 API_BASE="/api"，最终 /api/v1/wagent/kill-switch） */
export const KILL_SWITCH_PATH = "/v1/wagent/kill-switch";

/** 断线自动切轮询阈值（§4.4） */
export const SSE_FALLBACK_THRESHOLD = 3;

export interface RunProgressProps {
  runId: string;
  /** 服务端下发的进度通道声明 */
  progressChannel: ProgressChannel;
  /** 轮询间隔秒数（poll 通道展示用；缺省按 §3.4 默认 3s 展示注记） */
  pollIntervalSeconds?: number;
  /** 步骤时间线（引擎状态机投影；空数组时不渲染时间线，不补假步骤） */
  steps?: RunStep[];
  /** SSE 断线累计次数（由持有连接的调用方上报）；≥3 自动按轮询形态渲染 */
  sseDisconnectCount?: number;
  /** 断线 3 次自动切轮询时触发一次（调用方据此重排 react-query refetchInterval） */
  onFallbackToPoll?: (runId: string) => void;
  /** 覆写取消行为（调用方经自己的收敛层发 kill-switch 请求）；缺省走内置 apiFetch */
  onCancel?: (runId: string) => Promise<unknown>;
}

export default function RunProgress({
  runId,
  progressChannel,
  pollIntervalSeconds,
  steps = [],
  sseDisconnectCount = 0,
  onFallbackToPoll,
  onCancel,
}: RunProgressProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [confirmOpen, setConfirmOpen] = useState(false);
  const [cancelError, setCancelError] = useState(false);
  const fallbackFiredRef = useRef(false);

  // 通道归一：显式 poll，或 sse 断线达阈值 → 按轮询形态渲染（不得声称流式）
  const degradedToPoll = progressChannel === "poll" || (progressChannel === "sse" && sseDisconnectCount >= SSE_FALLBACK_THRESHOLD);
  const seconds = pollIntervalSeconds ?? 3;

  // 断线 3 次自动切轮询回调，仅触发一次
  useEffect(() => {
    if (
      progressChannel === "sse" &&
      sseDisconnectCount >= SSE_FALLBACK_THRESHOLD &&
      !fallbackFiredRef.current
    ) {
      fallbackFiredRef.current = true;
      onFallbackToPoll?.(runId);
    }
  }, [progressChannel, sseDisconnectCount, onFallbackToPoll, runId]);

  /** 内置取消：POST kill-switch（收敛层 httpClient，禁止裸 fetch） */
  const postKillSwitch = useCallback(async () => {
    await apiFetch(KILL_SWITCH_PATH, {
      method: "POST",
      body: JSON.stringify({ runId }),
    });
  }, [runId]);

  const handleConfirmedCancel = useCallback(() => {
    setConfirmOpen(false);
    setCancelError(false);
    const action = onCancel ? onCancel(runId) : postKillSwitch();
    void Promise.resolve(action).catch(() => setCancelError(true));
  }, [onCancel, runId, postKillSwitch]);

  const stateMeta = useMemo<Record<RunStep["state"], { text: string; tone: string }>>(
    () => ({
      pending: { text: t("common.run.state_pending"), tone: styles.cardTextMuted },
      running: { text: t("common.run.state_running"), tone: styles.accentText },
      done: { text: t("common.run.state_done"), tone: styles.successText },
      failed: { text: t("common.run.state_failed"), tone: styles.dangerText },
      waiting_user: { text: t("common.run.state_waiting"), tone: styles.warningText },
    }),
    [t, styles]
  );

  /** 步骤状态 → lucide 图标（颜色 + 图标 + 文字三重标识，不依赖纯色） */
  const stateIcons: Record<RunStep["state"], React.ReactNode> = {
    pending: <Clock className="w-3.5 h-3.5" aria-hidden="true" />,
    running: <Loader2 className="w-3.5 h-3.5 animate-spin" aria-hidden="true" />,
    done: <Check className="w-3.5 h-3.5" aria-hidden="true" />,
    failed: <X className="w-3.5 h-3.5" aria-hidden="true" />,
    waiting_user: <AlertTriangle className="w-3.5 h-3.5" aria-hidden="true" />,
  };

  return (
    <section
      className={`rounded-xl border p-4 space-y-4 ${styles.cardBg} ${styles.cardBorder}`}
      aria-label={t("common.run.title")}
    >
      <header className="flex items-center justify-between gap-2">
        <h3 className={`text-sm font-semibold ${styles.cardText}`}>{t("common.run.title")}</h3>
        <Button variant="danger" size="sm" onClick={() => setConfirmOpen(true)}>
          <Ban className="w-3.5 h-3.5 mr-1" aria-hidden="true" />
          {t("common.run.cancel")}
        </Button>
      </header>

      {degradedToPoll && (
        <p className={`text-xs rounded-lg border px-3 py-2 ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`} role="status">
          {t("common.status.poll_fallback", { seconds })}
        </p>
      )}

      {steps.length > 0 ? (
        <ol className="space-y-2">
          {steps.map((step) => (
            <li key={step.id} className="flex items-center gap-2 text-xs">
              <span className={stateMeta[step.state].tone}>{stateIcons[step.state]}</span>
              <span className={styles.cardText}>{step.label}</span>
              <span className={`ml-auto ${stateMeta[step.state].tone}`}>{stateMeta[step.state].text}</span>
            </li>
          ))}
        </ol>
      ) : (
        <p className={`text-xs ${styles.cardTextMuted}`}>{t("common.structured.no_data")}</p>
      )}

      {cancelError && (
        <p className={`text-xs ${styles.dangerText}`} role="alert">
          {t("common.run.cancel_failed")}
        </p>
      )}

      <ConfirmDialog
        open={confirmOpen}
        title={t("common.run.kill_title")}
        impactScope={t("common.run.kill_impact")}
        confirmLabel={t("common.run.kill_confirm")}
        onConfirm={handleConfirmedCancel}
        onCancel={() => setConfirmOpen(false)}
      />
    </section>
  );
}
