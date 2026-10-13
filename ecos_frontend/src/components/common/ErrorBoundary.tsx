/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * W196 收口（报告 §4.1 P2 / §4.8 P1 / §五 L3）：
 *  1. 全文件 `@ts-nocheck` 移除（全仓唯一 ts-nocheck 底座件）——React 19 类组件
 *     类型现可正常推导，无需豁免；
 *  2. chunk-load 失败（懒加载页 import 失败 = dev 模块缓存陈旧 / prod 发布窗口
 *     期 chunk 404）自动「整页 reload 一次」兜底：sessionStorage 时间戳防死循环，
 *     30s 窗口内不重复自动刷新；
 *  3. 「重试」对 chunk 类错误直接走整页刷新——旧实现仅 setState 复位边界，而
 *     失败的 dynamic import 在存活模块图里永远失败 → 重试按钮不可恢复（L3 实锤）；
 *  4. 裸 console.error 收口到 services/logger（前端开发规范铁律 10）。
 */
import React, { Component, ErrorInfo, ReactNode } from "react";
import { AlertTriangle, RefreshCw, Home } from "lucide-react";
import { useTheme } from "../ThemeContext";
import { useLanguage } from "../LanguageContext";
import { logger } from "../../services/logger";

export interface ErrorBoundaryProps {
  children: ReactNode;
  /** Optional fallback UI renderer */
  fallback?: (error: Error, reset: () => void) => ReactNode;
  /** Callback when an error caught */
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

/**
 * chunk-load 失败识别：Vite dev 报 "Failed to fetch dynamically imported
 * module"，webpack 报 "Loading chunk N failed"/ChunkLoadError，浏览器引擎
 * 措辞（Safari "Importing a module script failed"）一并覆盖。
 */
export function isChunkLoadError(error: unknown): boolean {
  if (!error) return false;
  const e = error as { message?: string; name?: string };
  const text = `${e.name ?? ""} ${e.message ?? ""}`;
  return /dynamically imported module|importing a module script failed|error loading dynamically imported module|loading chunk .*failed|ChunkLoadError/i.test(
    text,
  );
}

/** 整页 reload 一次防死循环：30s 窗口内已自动刷新过则不再刷（转入手动兜底）。 */
const CHUNK_RELOAD_GUARD_KEY = "ecos-chunk-reload-at";
const CHUNK_RELOAD_WINDOW_MS = 30_000;

function autoReloadOnceForChunkError(): boolean {
  try {
    const last = Number(sessionStorage.getItem(CHUNK_RELOAD_GUARD_KEY) || "0");
    if (Date.now() - last < CHUNK_RELOAD_WINDOW_MS) return false; // 刚刷过仍失败 → 不再循环
    sessionStorage.setItem(CHUNK_RELOAD_GUARD_KEY, String(Date.now()));
    window.location.reload();
    return true;
  } catch {
    // sessionStorage 不可用（隐私模式）：宁可不自动刷，交给手动「重新加载」按钮
    return false;
  }
}

class ErrorBoundaryClass extends Component<
  ErrorBoundaryProps,
  ErrorBoundaryState
> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    logger.error("ErrorBoundary", error, errorInfo);
    this.props.onError?.(error, errorInfo);
    // L3 兜底：chunk 拉取失败复位边界无意义（模块图里 import 永久失败），整页刷一次。
    if (isChunkLoadError(error)) {
      autoReloadOnceForChunkError();
    }
  }

  handleReset = () => {
    // 陈旧 chunk 场景下 setState 不可能恢复（报告 §4.1：重试不清陈旧 chunk）→
    // 直接整页刷新（带防死循环窗口）。普通渲染错误才走边界内复位。
    if (isChunkLoadError(this.state.error)) {
      window.location.reload();
      return;
    }
    this.setState({ hasError: false, error: null });
  };

  render(): ReactNode {
    if (!this.state.hasError) {
      return this.props.children;
    }

    if (this.props.fallback) {
      return this.props.fallback(this.state.error as Error, this.handleReset);
    }

    return (
      <DefaultFallback error={this.state.error as Error} onReset={this.handleReset} />
    );
  }
}

function DefaultFallback({
  error,
  onReset,
}: {
  error: Error;
  onReset: () => void;
}) {
  return <FallbackInner error={error} onReset={onReset} />;
}

function FallbackInner({
  error,
  onReset,
}: {
  error: Error;
  onReset: () => void;
}) {
  let styles: Record<string, string> = {};
  let t: (key: string, vars?: Record<string, string | number>) => string = (k) => k;

  try {
    const theme = useTheme();
    styles = theme.styles as unknown as Record<string, string>;
  } catch {
    // ThemeContext unavailable (boundary mounted outside providers): neutral
    // scrim classes only — no palette-specific literals (W196 owner-file rule).
    styles = {
      cardBg: "bg-black/40",
      cardBorder: "border-white/20",
      cardText: "text-white",
      cardTextMuted: "text-white/70",
      accentBg: "bg-white/20",
      accentHover: "hover:bg-white/30",
      accentText: "text-white",
      appBg: "bg-black/40",
      inputBg: "bg-black/30",
      dangerBg: "bg-black/40",
      dangerText: "text-white",
    };
  }

  try {
    const lang = useLanguage();
    t = lang.t;
  } catch {
    // LanguageContext not available
    t = (k) => k;
  }

  const chunkFailure = isChunkLoadError(error);

  return (
    <div
      className={`flex flex-col items-center justify-center min-h-[300px] p-8 select-text ${styles.cardBg} rounded-xl border ${styles.cardBorder}`}
    >
      <div className={`flex items-center justify-center w-16 h-16 rounded-full ${styles.dangerBg} mb-5`}>
        <AlertTriangle className={`w-8 h-8 ${styles.dangerText}`} />
      </div>

      <h2 className={`text-lg font-bold mb-1.5 ${styles.cardText}`}>
        {t("common.error.title")}
      </h2>
      <p className={`text-xs max-w-md text-center leading-relaxed mb-4 ${styles.cardTextMuted}`}>
        {/* chunk 拉取失败（dev 模块缓存陈旧 / 发布窗口期旧页面引用已下线 chunk）
            给专门口径而非通用错误文案 —— 报告 §五 L3。 */}
        {chunkFailure ? t("common.error.chunk") : t("common.error.desc")}
      </p>

      {/* Error details (collapsible) — 根容器 select-none 已收窄（报告 §4.1 P1），
          此块显式 select-text，traceId/错误详情必须可复制。 */}
      <details className="w-full max-w-lg mb-5">
        <summary
          className={`text-[10px] font-mono cursor-pointer ${styles.cardTextMuted} transition`}
        >
          {t("common.error.details")}
        </summary>
        <pre
          className={`mt-2 p-3 rounded-lg text-[10px] font-mono leading-relaxed overflow-auto max-h-32 whitespace-pre-wrap break-all select-text ${
            styles.inputBg
          } border ${styles.cardBorder} ${styles.dangerText}`}
        >
          {error.message}
          {error.stack ? `\n\n${error.stack}` : ""}
        </pre>
      </details>

      <div className="flex items-center gap-3">
        <button type="button"
          onClick={onReset}
          className={`flex items-center gap-1.5 px-4 py-2 text-xs font-bold rounded-lg cursor-pointer transition ${styles.accentBg} text-white hover:opacity-90 shadow-xs`}
        >
          <RefreshCw className="w-3.5 h-3.5" />
          {t("common.error.retry")}
        </button>
        <button type="button"
          onClick={() => window.location.reload()}
          className={`flex items-center gap-1.5 px-4 py-2 text-xs font-semibold rounded-lg border ${styles.cardBorder} ${styles.cardTextMuted} hover:bg-black/5 dark:hover:bg-white/5 cursor-pointer transition`}
        >
          <Home className="w-3.5 h-3.5" />
          {t("common.error.reload")}
        </button>
      </div>
    </div>
  );
}

/**
 * ErrorBoundary — wraps children to catch render errors and display
 * a friendly error fallback with retry capability.
 *
 * W214/C196 route-level usage: App.tsx mounts it at the <Outlet/> layer with
 * `key={currentView}`, so the boundary remounts per content route root —
 * a page crash degrades only that route, siblings survive.
 *
 * L3 兜底：chunk-load 失败自动整页 reload 一次（30s 防死循环窗口），手动
 * 「重试」对 chunk 类错误同样走整页刷新。
 *
 * Usage:
 *   <ErrorBoundary>
 *     <YourComponent />
 *   </ErrorBoundary>
 */
export default function ErrorBoundary(props: ErrorBoundaryProps) {
  return <ErrorBoundaryClass {...props} />;
}
