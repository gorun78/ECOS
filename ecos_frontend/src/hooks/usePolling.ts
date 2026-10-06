/**
 * usePolling — 页面级轮询收口 hook (替代裸 setInterval)
 *
 * 语义
 * - `enabled=false` 或 `document.hidden` 时不发起 poll（避免后台 Tab 空转）
 * - `unmount` 时自动 clearInterval
 * - 已被 in-flight 的 poll 不会叠加（poll 内 self-guard，防止慢请求叠加）
 * - 支持 `onError?: (err) => void` 而不影响下一轮
 */
import { useEffect, useRef } from "react";

interface UsePollingOptions {
  /** 每次 tick 执行的 async 函数 */
  poll: () => Promise<unknown> | unknown;
  /** 轮询间隔（毫秒）。区间支持 0 = 单次执行、不再轮询 */
  intervalMs: number;
  /** 立即触发首次 poll（默认 true，保留原有 App.tsx 语义） */
  immediate?: boolean;
  /** 是否忽略 document.hidden 状态（默认 true = 隐藏时不 poll） */
  pauseOnHidden?: boolean;
  /** enabled=false 时完全不 poll（unmount 的另一等效替代） */
  enabled?: boolean;
  /** 单次 poll 失败时可选的钩子（不影响下一轮） */
  onError?: (err: unknown) => void;
}

export function usePolling({
  poll,
  intervalMs,
  immediate = true,
  pauseOnHidden = true,
  enabled = true,
  onError,
}: UsePollingOptions): void {
  const pollRef = useRef(poll);
  const onErrorRef = useRef(onError);
  pollRef.current = poll;
  onErrorRef.current = onError;

  useEffect(() => {
    if (!enabled) return;
    if (intervalMs <= 0 && !immediate) return;

    let inFlight = false;
    const runOnce = () => {
      if (inFlight) return;
      inFlight = true;
      Promise.resolve(pollRef.current())
        .catch((err) => onErrorRef.current?.(err))
        .finally(() => {
          inFlight = false;
        });
    };

    if (immediate) runOnce();

    if (intervalMs <= 0) return;

    let timer: ReturnType<typeof setInterval> | null = setInterval(() => {
      if (pauseOnHidden && typeof document !== "undefined" && document.hidden) return;
      runOnce();
    }, intervalMs);

    let visibilityHandler: (() => void) | null = null;
    if (pauseOnHidden && typeof document !== "undefined") {
      // 回到前台时补一次 poll，减少用户可见的 stale 窗口
      visibilityHandler = () => {
        if (!document.hidden) runOnce();
      };
      document.addEventListener("visibilitychange", visibilityHandler);
    }

    return () => {
      if (timer) clearInterval(timer);
      if (visibilityHandler) document.removeEventListener("visibilitychange", visibilityHandler);
    };
  }, [enabled, intervalMs, immediate, pauseOnHidden]);
}

export default usePolling;
