/**
 * DecisionMindList — R4 Mind 治理列表（#/decision/mind，差距分析 P0-6 /
 * 详设 §8.6.1 前端面 / 本批 B3-c）。
 *
 * 数据面单源：services/cognitive/mindApi.ts（callJson 唯一通道，路径从
 * COGNITIVE_BASE 派生，全仓无第二处字面量）。
 *
 * 真值纪律：
 *  - r4ColumnsProvisioned=false（V292 未落库）⇒ 顶部横幅明示「未迁移」，
 *    三轴列全空展示，禁折叠成 DRAFT/PENDING_REVIEW 假值；
 *  - 单行失败不影响整表（chained error 走 fail-closed 横幅 + 重试）；
 *  - 禁本地合成 isActive / 三轴 等字段。
 *
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ArrowRight, Brain, RefreshCw } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { fetchMindList, type MindRow } from "../../services/cognitive/mindApi";
import logger from "../../services/logger";

interface LoadState {
  loading: boolean;
  error: string | null;
}

/** 三轴状态单源映射：命中词表枚 → i18n；未命中/未知 → 前缀 + 原值（禁吞掉，让漂移显形）。 */
function axisLabel(t: (k: string) => string, axisNs: "validity" | "review" | "adoption", raw: string | null | undefined): string {
  void axisNs;
  if (!raw) return "—";
  const mapped = t(`platform.mind.status.${raw}`);
  return mapped === `platform.mind.status.${raw}` ? `${t("platform.mind.unmappedPrefix")}${raw}` : mapped;
}

export default function DecisionMindList() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [rows, setRows] = useState<MindRow[]>([]);
  const [provisioned, setProvisioned] = useState<boolean>(true);
  const [state, setState] = useState<LoadState>({ loading: true, error: null });

  const load = useCallback(async () => {
    setState({ loading: true, error: null });
    try {
      const res = await fetchMindList();
      setRows(res.items);
      setProvisioned(res.r4ColumnsProvisioned);
      setState({ loading: false, error: null });
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      logger.error("decision-mind-list", e);
      setState({ loading: false, error: msg });
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const retry = (): void => { void load(); };

  return (
    <div className={`flex-1 flex flex-col overflow-y-auto ${styles.appBg} ${styles.appText}`}>
      {/* 页头 */}
      <div className={`px-4 py-4 border-b ${styles.cardBorder} ${styles.cardBg} shrink-0 flex items-center justify-between`}>
        <div className="flex items-center gap-2">
          <span className={`p-1.5 rounded-md flex items-center justify-center ${styles.accentBg} ${styles.cardText}`}>
            <Brain size={16} />
          </span>
          <div>
            <h1 className={`text-lg font-bold tracking-tight ${styles.appText}`}>{t("platform.mind.title")}</h1>
            <p className={`text-[11px] ${styles.cardTextMuted} -mt-0.5`}>{t("platform.mind.subtitle")}</p>
          </div>
        </div>
        <button
          type="button"
          onClick={retry}
          className={`p-1.5 rounded-md border ${styles.cardBorder} ${styles.cardTextMuted} hover:opacity-80 cursor-pointer`}
          title={t("platform.mind.retry")}
        >
          <RefreshCw size={14} />
        </button>
      </div>

      {/* 未迁移横幅 */}
      {!state.loading && !state.error && !provisioned && (
        <div className={`mx-4 mt-4 rounded-md px-3 py-2 border text-[11px] break-words ${styles.warningBg} ${styles.warningText} ${styles.cardBorder}`}>
          {t("platform.mind.unmigrated")}
        </div>
      )}

      <section className="p-4">
        <p className={`text-[11px] ${styles.cardTextMuted} mb-2`}>{t("platform.mind.preview.desc")}</p>

        {state.loading ? (
          <div className="flex items-center justify-center py-10">
            <Spinner />
          </div>
        ) : state.error ? (
          <ErrorBox detail={state.error} onRetry={retry} />
        ) : rows.length === 0 ? (
          <div className={`py-10 text-center text-xs ${styles.cardTextMuted} border rounded-lg ${styles.cardBorder}`}>
            {t("platform.mind.empty")}
          </div>
        ) : (
          <div className={`rounded-lg border overflow-hidden ${styles.cardBg} ${styles.cardBorder}`}>
            <table className="w-full text-xs">
              <thead>
                <tr className={`${styles.cardBorder} border-b`}>
                  <Th label={t("platform.mind.col.name")} className={styles.cardBorder} />
                  <Th label={t("platform.mind.col.key")} className={styles.cardBorder} />
                  <Th label={t("platform.mind.col.active")} className={styles.cardBorder} />
                  <Th label={t("platform.mind.axis.validity")} className={styles.cardBorder} />
                  <Th label={t("platform.mind.axis.review")} className={styles.cardBorder} />
                  <Th label={t("platform.mind.axis.adoption")} className={styles.cardBorder} />
                  <Th label="" className={styles.cardBorder} />
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.mindId} className={`border-b last:border-b-0 ${styles.cardBorder}`}>
                    <Td className={styles.cardBorder}>
                      <Link
                        to={`/decision/mind/${encodeURIComponent(r.mindId)}`}
                        className={`font-bold inline-flex items-center gap-1 ${styles.accentText}`}
                      >
                        {r.name || r.mindKey || r.mindId}
                        <ArrowRight size={11} />
                      </Link>
                    </Td>
                    <Td className={styles.cardBorder}>
                      <span className="font-mono text-[11px]">{r.mindKey ?? "—"}</span>
                    </Td>
                    <Td className={styles.cardBorder}>
                      {r.isActive === undefined ? "—" : r.isActive ? t("platform.mind.yes") : t("platform.mind.no")}
                    </Td>
                    <Td className={styles.cardBorder}>{provisioned ? axisLabel(t, "validity", r.validityStatus) : "—"}</Td>
                    <Td className={styles.cardBorder}>{provisioned ? axisLabel(t, "review", r.reviewStatus) : "—"}</Td>
                    <Td className={styles.cardBorder}>{provisioned ? axisLabel(t, "adoption", r.adoptionStatus) : "—"}</Td>
                    <Td right className={styles.cardBorder}>
                      <span className="text-[10px] text-right inline-flex items-center gap-1">
                        <ArrowRight size={11} />
                      </span>
                    </Td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}

function Th({ label, className }: { label: string; className?: string }) {
  const { styles } = useTheme();
  return (
    <th className={`px-3 py-2 text-left font-semibold text-[10px] uppercase tracking-wide ${styles.cardTextMuted} ${className ?? ""}`}>
      {label}
    </th>
  );
}

function Td({ children, right = false, className }: { children: React.ReactNode; right?: boolean; className?: string }) {
  const { styles } = useTheme();
  return (
    <td className={`px-3 py-2 ${right ? "text-right" : ""} ${styles.cardText ?? ""} ${className ?? ""}`}>{children}</td>
  );
}

function Spinner() {
  return (
    <span className="h-5 w-5 inline-block animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />
  );
}

function ErrorBox({ detail, onRetry }: { detail: string; onRetry: () => void }) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  return (
    <div className={`rounded-md px-3 py-4 border text-[11px] break-words ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder} text-center`}>
      <span>{t("platform.mind.loadFailed")}{detail}</span>
      <button
        type="button"
        onClick={onRetry}
        className={`ml-2 rounded border px-2 py-0.5 cursor-pointer ${styles.inputBorder} ${styles.inputText}`}
      >
        {t("platform.mind.retry")}
      </button>
    </div>
  );
}
