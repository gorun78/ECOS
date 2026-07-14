// @ts-nocheck
/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 * @ts-nocheck — React 19 class component types need @types/react upgrade
 */

// @ts-nocheck — React 19 class component types need @types/react upgrade
import React, { Component, ErrorInfo, ReactNode } from "react";
import { AlertTriangle, RefreshCw, Home } from "lucide-react";
import { useTheme } from "../ThemeContext";
import { useLanguage } from "../LanguageContext";

export interface ErrorBoundaryProps {
  children: ReactNode;
  /** Optional fallback UI renderer */
  fallback?: (error: Error, reset: () => void) => ReactNode;
  /** Callback when an error is caught */
  onError?: (error: Error, errorInfo: ErrorInfo) => void;
}

interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
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
    console.error("[ErrorBoundary]", error, errorInfo);
    this.props.onError?.(error, errorInfo);
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null });
  };

  render() {
    if (!this.state.hasError) {
      return this.props.children;
    }

    if (this.props.fallback) {
      return this.props.fallback(this.state.error!, this.handleReset);
    }

    return (
      <DefaultFallback error={this.state.error!} onReset={this.handleReset} />
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
  const themeHook = { styles: {} as any }; // Placeholder — will use ThemeWrapper
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
  let t: (key: string) => string = (k) => k;
  let locale = "en";

  try {
    const theme = useTheme();
    styles = theme.styles;
  } catch {
    // Fallback if ThemeContext is not available
    styles = {
      cardBg: "bg-white",
      cardBorder: "border-[#E2E8F0]",
      cardText: "text-slate-800",
      cardTextMuted: "text-slate-500",
      accentBg: "bg-indigo-600",
      accentHover: "hover:bg-indigo-700",
      accentText: "text-indigo-600",
      appBg: "bg-slate-50",
      inputBg: "bg-white",
    };
  }

  try {
    const lang = useLanguage();
    t = lang.t;
    locale = lang.locale;
  } catch {
    // LanguageContext not available
    t = (k) => k;
  }

  return (
    <div
      className={`flex flex-col items-center justify-center min-h-[300px] p-8 select-none ${styles.cardBg} rounded-xl border ${styles.cardBorder}`}
    >
      <div className="flex items-center justify-center w-16 h-16 rounded-full bg-red-50 dark:bg-red-950/30 mb-5">
        <AlertTriangle className="w-8 h-8 text-red-500" />
      </div>

      <h2 className={`text-lg font-bold mb-1.5 ${styles.cardText}`}>
        {locale === "zh" ? "页面渲染出错" : "Something went wrong"}
      </h2>
      <p className={`text-xs max-w-md text-center leading-relaxed mb-4 ${styles.cardTextMuted}`}>
        {locale === "zh"
          ? "应用程序遇到了意外错误。请尝试刷新页面，如果问题持续存在，请联系管理员。"
          : "The application encountered an unexpected error. Try refreshing the page, or contact your administrator if the issue persists."}
      </p>

      {/* Error details (collapsible) */}
      <details className="w-full max-w-lg mb-5">
        <summary
          className={`text-[10px] font-mono cursor-pointer ${styles.cardTextMuted} hover:${styles.cardText} transition`}
        >
          {locale === "zh" ? "查看错误详情" : "Error details"}
        </summary>
        <pre
          className={`mt-2 p-3 rounded-lg text-[10px] font-mono leading-relaxed overflow-auto max-h-32 whitespace-pre-wrap break-all ${
            styles.inputBg
          } border ${styles.cardBorder} text-red-600 dark:text-red-400`}
        >
          {error.message}
          {error.stack && `\n\n${error.stack}`}
        </pre>
      </details>

      <div className="flex items-center gap-3">
        <button
          onClick={onReset}
          className={`flex items-center gap-1.5 px-4 py-2 text-xs font-bold rounded-lg cursor-pointer transition ${styles.accentBg} text-white hover:opacity-90 shadow-xs`}
        >
          <RefreshCw className="w-3.5 h-3.5" />
          {locale === "zh" ? "重试" : "Try Again"}
        </button>
        <button
          onClick={() => window.location.reload()}
          className={`flex items-center gap-1.5 px-4 py-2 text-xs font-semibold rounded-lg border ${styles.cardBorder} ${styles.cardTextMuted} hover:bg-black/5 dark:hover:bg-white/5 cursor-pointer transition`}
        >
          <Home className="w-3.5 h-3.5" />
          {locale === "zh" ? "刷新页面" : "Refresh Page"}
        </button>
      </div>
    </div>
  );
}

/**
 * ErrorBoundary — wraps children to catch render errors and display
 * a friendly error fallback with retry capability.
 *
 * Usage:
 *   <ErrorBoundary>
 *     <YourComponent />
 *   </ErrorBoundary>
 */
export default function ErrorBoundary(props: ErrorBoundaryProps) {
  return <ErrorBoundaryClass {...props} />;
}
