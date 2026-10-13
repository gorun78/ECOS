/**
 * ECOS 项目看板 — 嵌入 kanban-web standalone
 *
 * 2026-10-13 前端检视修复（F 组）：
 * 1. iframe sandbox 风险评估：`allow-same-origin allow-scripts` 组合下，同源内容
 *    可自行移除 sandbox 限制（等效无沙箱）。经实测评估**不能去掉** allow-same-origin：
 *    看板 bundle（public/kanban/assets/index-*.js）依赖 localStorage ×8 与 fetch ×2
 *    （卡片持久化 + kanban-data.json 读取），去掉后应用直接报错不可用。
 *    内容为本仓自研第一方构建产物（非用户可控 HTML），残余风险记录于此；
 *    彻底隔离需将看板部署到独立 origin（部署侧议题，不在本文件 scope）。
 * 2. 补加载失败回退：onLoad 探测（重定向/404 时 iframe 会 load 成浏览器错误页，
 *    通过 contentWindow 可达性 + location 路径判定）+ 超时兜底 + 重试重建。
 */
import React, { useCallback, useEffect, useRef, useState } from "react";
import { LayoutDashboard, AlertCircle, Loader2, RefreshCw } from "lucide-react";
import { useLanguage } from "../components/LanguageContext";
import { useTheme } from "../components/ThemeContext";

const KANBAN_SRC = "/kanban/ecos-kanban.html";
const LOAD_TIMEOUT_MS = 15_000;

export default function KanbanBoard() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const frameRef = useRef<HTMLIFrameElement | null>(null);
  const [frameKey, setFrameKey] = useState(0);
  const [status, setStatus] = useState<"loading" | "ok" | "failed">("loading");

  // 超时兜底：iframe 既不 load 也不报错（网络挂起）时判失败
  useEffect(() => {
    if (status !== "loading") return;
    const timer = setTimeout(() => setStatus(s => (s === "loading" ? "failed" : s)), LOAD_TIMEOUT_MS);
    return () => clearTimeout(timer);
  }, [status, frameKey]);

  const handleLoad = useCallback(() => {
    let ok = true;
    try {
      // 同源可读：校验实际落点仍是看板页（404/重定向会 load 成错误页）
      const win = frameRef.current?.contentWindow;
      const doc = frameRef.current?.contentDocument;
      if (!doc || !doc.body || doc.body.childElementCount === 0) {
        ok = false;
      } else if (win && !/ecos-kanban\.html$/.test(win.location.pathname)) {
        ok = false;
      }
    } catch {
      // 读不到 contentWindow = 未落到同源错误页；sandbox 不禁止同源脚本枚举，
      // 第一方页面正常路径不会走到这里 — 保守判定为成功，避免误伤。
      ok = true;
    }
    setStatus(ok ? "ok" : "failed");
  }, []);

  const retry = useCallback(() => {
    setStatus("loading");
    setFrameKey(k => k + 1);
  }, []);

  return (
    <div className="h-full flex flex-col">
      {/* Title bar */}
      <div className={`flex items-center gap-2 px-6 py-4 border-b ${styles.cardBorder}`}>
        <LayoutDashboard size={20} className={styles.accentText} />
        <h1 className={`text-lg font-bold ${styles.appText}`}>{t("kanban.title")}</h1>
        <span className={`text-xs ml-2 ${styles.cardTextMuted}`}>{t("kanban.subtitle")}</span>
      </div>
      {/* Kanban iframe + 加载态/失败回退 */}
      <div className="relative flex-1 min-h-0">
        <iframe
          key={frameKey}
          ref={frameRef}
          src={KANBAN_SRC}
          className="h-full w-full border-0"
          title={t("kanban.title")}
          sandbox="allow-scripts allow-same-origin"
          onLoad={handleLoad}
        />
        {status === "loading" && (
          <div className={`absolute inset-0 flex items-center justify-center ${styles.appBg}`}>
            <div className="text-center space-y-3">
              <Loader2 className={`w-8 h-8 ${styles.accentText} animate-spin mx-auto`} />
              <p className={`text-sm ${styles.cardTextMuted}`}>{t("kanban.loading")}</p>
            </div>
          </div>
        )}
        {status === "failed" && (
          <div role="alert" className={`absolute inset-0 flex items-center justify-center ${styles.appBg}`}>
            <div className="text-center max-w-sm">
              <AlertCircle className={`w-10 h-10 ${styles.dangerText} mx-auto mb-3`} />
              <p className={`text-sm font-semibold ${styles.dangerText} mb-1`}>{t("kanban.loadFailed")}</p>
              <button
                type="button"
                onClick={retry}
                className={`inline-flex items-center gap-1.5 px-4 py-2 ${styles.accentBg} text-white text-xs font-semibold rounded-lg transition hover:opacity-90`}
              >
                <RefreshCw className="w-3.5 h-3.5" />
                {t("kanban.retry")}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
