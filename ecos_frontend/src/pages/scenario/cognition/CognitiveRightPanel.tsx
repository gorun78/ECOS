/**
 * CognitiveRightPanel — 场景工作台右栏：认知四端点 Tab 壳（design-05 B.3 / F05-17）
 *
 * <p>Props: `{scenarioId: string}`。四个子 Tab 独立文件（Detect / OperationEval /
 * Hypotheses / Beliefs），共享 `<select>` 的 Mind 下拉（来自 E1 detect 结果：
 * 该场景可用 Mind，空列表 → 禁用下拉 + 空态 `t('scenario.cognition.scenarioNoBind')`）。</p>
 *
 * <p>错误 7 态矩阵（design-05 B.5）各 Tab 独立持有，本文件只维护 shell（Tab 切换 +
 * Mind 选择 + 状态共享）。Mind 选择为空 = "全部 Mind"（E3/E4 不传 `?mind=`）。</p>
 *
 * <p>主题全部走 `useTheme()` token；i18n 全走 `useLanguage()`；图标仅 lucide-react。</p>
 *
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { BrainCircuit, Loader2, ShieldAlert, X } from "lucide-react";
import { useTheme } from "../../../components/ThemeContext";
import { useLanguage } from "../../../components/LanguageContext";
import { useMobileSidebar } from "../../../hooks/useMobileSidebar";
import {
  detectScenarioMinds,
  isCognitiveApiError,
  CognitiveApiError,
  debounce,
  COGNITION_POLL_INTERVAL_MS,
  COGNITION_DEBOUNCE_MS,
  type CognMindRow,
} from "../../../services/cognitiveClient";
import DetectTab from "./DetectTab";
import OperationEvalTab from "./OperationEvalTab";
import HypothesesTab from "./HypothesesTab";
import BeliefsTab from "./BeliefsTab";

export interface CognitiveRightPanelProps {
  scenarioId: string;
}

type TabKey = "detect" | "operationEval" | "hypotheses" | "beliefs";

interface TabMeta {
  key: TabKey;
  labelKey: string;
}

const TABS: TabMeta[] = [
  { key: "detect", labelKey: "scenario.cognition.detect" },
  { key: "operationEval", labelKey: "scenario.cognition.operationEval" },
  { key: "hypotheses", labelKey: "scenario.cognition.hypotheses" },
  { key: "beliefs", labelKey: "scenario.cognition.beliefs" },
];

export interface MindLoadState {
  loading: boolean;
  rows: CognMindRow[];
  error: CognitiveApiError | null;
}

const INITIAL_MINDS: MindLoadState = { loading: false, rows: [], error: null };

export default function CognitiveRightPanel({ scenarioId }: CognitiveRightPanelProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [active, setActive] = useState<TabKey>("detect");
  const [minds, setMinds] = useState<MindLoadState>(INITIAL_MINDS);
  const [selectedMindId, setSelectedMindId] = useState<string>("");

  const loadMinds = useCallback(async () => {
    if (!scenarioId) return;
    setMinds((p) => ({ ...p, loading: true, error: null }));
    try {
      const rows = await detectScenarioMinds(scenarioId);
      setMinds({ loading: false, rows, error: null });
    } catch (e) {
      setMinds({
        loading: false,
        rows: [],
        error: isCognitiveApiError(e)
          ? e
          : new CognitiveApiError({
              code: "NETWORK",
              status: 0,
              message: e instanceof Error ? e.message : String(e),
            }),
      });
    }
  }, [scenarioId]);

  const loadMindsRef = useRef(loadMinds);
  loadMindsRef.current = loadMinds;
  // 重复触发防抖（快速切换场景合并为末次，契约 100–300ms）
  const debouncedLoadMinds = useMemo(
    () => debounce(() => { void loadMindsRef.current(); }, COGNITION_DEBOUNCE_MS),
    [],
  );

  useEffect(() => {
    setMinds(INITIAL_MINDS);
    setSelectedMindId("");
    debouncedLoadMinds();
    // 30s 后台静默轮询 Mind 可用性（design-05 B.6 / X-52）
    const poll = setInterval(() => { void loadMindsRef.current(); }, COGNITION_POLL_INTERVAL_MS);
    return () => {
      clearInterval(poll);
      debouncedLoadMinds.cancel();
    };
  }, [scenarioId, debouncedLoadMinds]);

  // B.8 移动端：右栏在 <768px 折叠为抽屉（复用 useMobileSidebar 单例语义）
  const { isMobile, sidebarOpen, toggleSidebar, closeSidebar } = useMobileSidebar();

  // B.7 键盘可达 Tab（role=tablist + 方向键/Home/End，WAI-ARIA Tabs 模式）
  const tabRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const onTabListKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    const current = TABS.findIndex((tb) => tb.key === active);
    let next = -1;
    if (e.key === "ArrowLeft") next = (current - 1 + TABS.length) % TABS.length;
    else if (e.key === "ArrowRight") next = (current + 1) % TABS.length;
    else if (e.key === "Home") next = 0;
    else if (e.key === "End") next = TABS.length - 1;
    if (next < 0) return;
    e.preventDefault();
    setActive(TABS[next].key);
    tabRefs.current[next]?.focus();
  };

  const hasError = !!minds.error;
  const disabled = !!minds.loading || hasError || minds.rows.length === 0;
  // design-05 B.5 状态矩阵：引擎未启用 / schema 未就绪（503）→ 右栏整体置灰 + banner
  const engineDown =
    !!minds.error && (minds.error.code === "ENGINE_DISABLED" || minds.error.code === "SCHEMA_NOT_READY");

  const renderTabData = () => {
    if (!scenarioId) {
      return (
        <div className={`p-4 ${styles.muted} text-xs`} role="status">
          {t("scenario.cognition.empty")}
        </div>
      );
    }
    const mindId = selectedMindId || undefined;
    const onRetryMinds = (): void => {
      void loadMinds();
    };
    switch (active) {
      case "detect":
        return <DetectTab scenarioId={scenarioId} mindId={mindId} minds={minds} onRetryMinds={onRetryMinds} />;
      case "operationEval":
        return <OperationEvalTab scenarioId={scenarioId} mindId={mindId} />;
      case "hypotheses":
        return <HypothesesTab scenarioId={scenarioId} mindId={mindId} />;
      case "beliefs":
        return <BeliefsTab scenarioId={scenarioId} mindId={mindId} />;
      default:
        return null;
    }
  };

  const panelBody = (
    <div className={`space-y-3 ${isMobile ? "p-2" : "p-3"}`} data-mobile={isMobile || undefined}>
      {/* Header */}
      <div className={`flex items-center gap-2 ${styles.cardBg} border ${styles.cardBorder} rounded-md px-3 py-2`}>
        <span className={`p-1 rounded ${styles.accentBg} ${styles.accentText}`}>
          <BrainCircuit size={13} />
        </span>
        <span className="text-xs font-bold">{t("scenario.cognition.panel")}</span>
        {minds.loading && (
          <Loader2 size={12} className={`ml-auto animate-spin ${styles.muted}`} />
        )}
      </div>

      {/* Mind 选择（E1 探测驱动） */}
      <div className={`flex items-center gap-2 ${styles.cardBg} border ${styles.cardBorder} rounded-md px-3 py-2`}>
        <label
          htmlFor="ecos-cognition-mind"
          className={`text-[10px] font-bold uppercase tracking-wider ${styles.muted}`}
        >
          {t("scenario.cognition.selectMind")}
        </label>
        <select
          id="ecos-cognition-mind"
          aria-label={t("scenario.cognition.selectMind")}
          value={selectedMindId}
          disabled={disabled}
          onChange={(e) => setSelectedMindId(e.target.value)}
          className={`ml-auto px-2 py-1 text-[11px] rounded ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} outline-none cursor-pointer disabled:opacity-50 max-w-[55%]`}
        >
          <option value="">{t("scenario.cognition.allMinds")}</option>
          {minds.rows.map((m) => (
            <option key={m.mindId} value={m.mindId}>
              {m.name}
              {m.isActive ? ` · ${t("scenario.cognition.active")}` : ""}
            </option>
          ))}
        </select>
      </div>

      {/* Tab bar（B.7：role=tablist + 方向键键盘可达） */}
      <div
        role="tablist"
        aria-label={t("scenario.cognition.panel")}
        onKeyDown={onTabListKeyDown}
        className={`flex flex-wrap gap-1 border-b ${styles.cardBorder}`}
      >
        {TABS.map((tab, i) => {
          const isActive = tab.key === active;
          return (
            <button
              key={tab.key}
              type="button"
              role="tab"
              id={`cog-tab-${tab.key}`}
              aria-selected={isActive}
              aria-controls="cog-tabpanel"
              tabIndex={isActive ? 0 : -1}
              ref={(el) => {
                tabRefs.current[i] = el;
              }}
              onClick={() => setActive(tab.key)}
              aria-label={t(tab.labelKey)}
              className={`px-3 py-1.5 text-[11px] font-bold rounded-t-md border-b-2 transition-colors cursor-pointer ${
                isActive
                  ? `${styles.accentBorder} ${styles.accentText}`
                  : `border-transparent ${styles.muted} hover:opacity-80`
              }`}
            >
              {t(tab.labelKey)}
            </button>
          );
        })}
      </div>

      {/* 503 未启用/schema 未就绪 → 右栏置灰 banner（B.5，联动 PRD-08 WS-03） */}
      {engineDown && minds.error && (
        <div
          role="status"
          aria-live="polite"
          data-testid="cog-engine-down-banner"
          className={`flex items-start gap-2 p-3 rounded-md text-[11px] border ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}
        >
          <ShieldAlert size={13} className="mt-0.5 shrink-0" />
          <span>
            {minds.error.code === "SCHEMA_NOT_READY"
              ? t("scenario.cognition.schemaNotReady")
              : t("scenario.cognition.notEnabled")}
          </span>
        </div>
      )}

      {/* Tab content */}
      <div
        role="tabpanel"
        id="cog-tabpanel"
        aria-labelledby={`cog-tab-${active}`}
        className={`${styles.cardBg} border ${styles.cardBorder} rounded-b-md transition-opacity ${
          engineDown ? "opacity-40 pointer-events-none select-none" : ""
        }`}
        aria-disabled={engineDown || undefined}
      >
        {renderTabData()}
      </div>
    </div>
  );

  // B.8：<768px 折叠为触发卡片 + 右侧抽屉（role=dialog + 遮罩 + 关闭按钮）
  if (isMobile) {
    return (
      <div data-testid="cognitive-right-panel" className={`h-full w-full ${styles.appBg} font-sans`}>
        <div className="p-2">
          <button
            type="button"
            onClick={toggleSidebar}
            aria-expanded={sidebarOpen}
            aria-haspopup="dialog"
            aria-label={t("scenario.cognition.openPanel")}
            className={`flex w-full items-center gap-2 ${styles.cardBg} border ${styles.cardBorder} rounded-md px-3 py-2 cursor-pointer`}
          >
            <span className={`p-1 rounded ${styles.accentBg} ${styles.accentText}`}>
              <BrainCircuit size={13} />
            </span>
            <span className="text-xs font-bold">{t("scenario.cognition.panel")}</span>
            {minds.loading && (
              <Loader2 size={12} className={`ml-auto animate-spin ${styles.muted}`} />
            )}
          </button>
        </div>
        {sidebarOpen && (
          <div
            className="fixed inset-0 z-50 flex justify-end"
            role="dialog"
            aria-modal="true"
            aria-label={t("scenario.cognition.panel")}
          >
            <button
              type="button"
              onClick={closeSidebar}
              aria-label={t("scenario.cognition.closePanel")}
              className={`absolute inset-0 ${styles.overlayBg} cursor-default`}
            />
            <aside
              className={`relative h-full w-[85%] max-w-sm overflow-auto ${styles.appBg} ${styles.appText} font-sans`}
            >
              <div className="flex items-center justify-between px-3 pt-3">
                <span className={`text-xs font-bold uppercase tracking-wider ${styles.muted}`}>
                  {t("scenario.cognition.panel")}
                </span>
                <button
                  type="button"
                  onClick={closeSidebar}
                  aria-label={t("scenario.cognition.closePanel")}
                  className={`rounded border p-1 cursor-pointer ${styles.cardBg} ${styles.cardBorder} ${styles.cardTextMuted}`}
                >
                  <X size={13} />
                </button>
              </div>
              {panelBody}
            </aside>
          </div>
        )}
      </div>
    );
  }

  return (
    <div
      data-testid="cognitive-right-panel"
      className={`h-full w-full overflow-auto ${styles.appBg} ${styles.appText} font-sans`}
    >
      {panelBody}
    </div>
  );
}
