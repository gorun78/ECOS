/**
 * PMO-48-A T5 + PMO-48-B T8 — 数据质量中心 · 6 维度评估 Tab
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * 渲染 2 列：
 *   左 (col-span-8) — 顶：当前选中表健康度区 (T8) + 雷达图 (真实 6 维分，无评分时占位 0.5)
 *   右 (col-span-4) — 6 张维度卡片 (静态 6 维 + 每个分数显示)
 *
 * 数据源:
 *   - GET /api/v1/dq/rules/dimension-registry (T3 已落地)
 *   - GET /api/v1/dq/scores?assetType=TABLE&assetId=xxx (T8, 真实评分)
 *   - POST /api/v1/dq/scores/recompute (T8, 手动触发)
 *
 * T8 接真实评分语义:
 *   - URL param ?table=xxx 命中 → 调 fetchDqScoreAsset('TABLE', tableId)
 *   - 6 维卡片 + 雷达图都展示真实分数 (dimensionScores)
 *   - 无评分时显示「未评分」徽章 + [手动重算] 按钮
 *   - 重算中标题切换为「重算中…」并按主题样式
 *   - 卡片标题下加 score: 0.92 显示
 */

import React, { useCallback, useEffect, useMemo, useState } from "react";
import {
  Radar,
  RadarChart,
  PolarGrid,
  PolarAngleAxis,
  ResponsiveContainer,
} from "recharts";
import {
  Database,
  Clock,
  Hash,
  FileText,
  Fingerprint,
  Layers,
  Loader2,
  AlertCircle,
  RefreshCw,
  Gauge,
} from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import {
  DqDimensionRegistryVO,
  DqAssetScoreVO,
  fetchDqDimensionRegistry,
  fetchDqScoreAsset,
  recomputeDqScore,
} from "./api";
import { useSearchParams } from "react-router-dom";

/** 维度 → 图标映射 (6 个语义化维度, 都用 lucide-react) */
const DIMENSION_ICONS: Record<string, React.ComponentType<{ className?: string }>> = {
  NULL: Database,
  FRESHNESS: Clock,
  RANGE: Hash,
  FORMAT: FileText,
  UNIQUE: Fingerprint,
  CUSTOM: Layers,
  COMPLETENESS: Database,
  ACCURACY: FileText,
  CONSISTENCY: Fingerprint,
  UNIQUENESS: Hash,
  VALIDITY: Layers,
};

/** 等级 → 颜色 (主题语义色; 单行单属性, 不硬编码) */
function gradeStyle(styles: ReturnType<typeof useTheme>["styles"], grade: string) {
  switch ((grade ?? "").toUpperCase()) {
    case "A":
      return `${styles.successBg} ${styles.successText} border ${styles.successBorder}`;
    case "B":
      return `${styles.infoBg} ${styles.infoText} border ${styles.infoBorder}`;
    case "C":
      return `${styles.warningBg} ${styles.warningText} border ${styles.warningBorder}`;
    case "D":
      return `${styles.dangerBg} ${styles.dangerText} border ${styles.dangerBorder}`;
    case "F":
      return `${styles.dangerBg} ${styles.dangerText} border ${styles.dangerBorder}`;
    default:
      return `${styles.badgeBg} ${styles.badgeText}`;
  }
}

/** i18n 字典 key 映射 (per grade) */
function gradeI18nKey(grade: string): string {
  switch ((grade ?? "").toUpperCase()) {
    case "A": return "dw.dqRule.score.gradeA";
    case "B": return "dw.dqRule.score.gradeB";
    case "C": return "dw.dqRule.score.gradeC";
    case "D": return "dw.dqRule.score.gradeD";
    case "F": return "dw.dqRule.score.gradeF";
    default: return "dw.dqRule.score.grade";
  }
}

/** 卡片 i18n 显示中/英文, 6 维静态名 (T3 已 z) */
const DIM_ORDER = ["COMPLETENESS", "ACCURACY", "CONSISTENCY", "UNIQUENESS", "VALIDITY", "FRESHNESS"] as const;

export default function DimensionTab() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [searchParams] = useSearchParams();

  // 静态 6 维 (T3 dimension-registry)
  const [dims, setDims] = useState<DqDimensionRegistryVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // T8 真实评分 (当前选中表)
  const tableId = (searchParams.get("table") ?? "").trim();
  const [assetScore, setAssetScore] = useState<DqAssetScoreVO | null>(null);
  const [scoreLoading, setScoreLoading] = useState(false);
  const [recomputing, setRecomputing] = useState(false);
  const [recomputeMsg, setRecomputeMsg] = useState("");

  const loadRegistry = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const arr = await fetchDqDimensionRegistry();
      if (arr && arr.length > 0) {
        setDims(arr);
      } else {
        setDims([]);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : t("dw.dqRule.loadFailed"));
    } finally {
      setLoading(false);
    }
  }, [t]);

  const loadScore = useCallback(async (silent = false) => {
    if (!tableId) {
      setAssetScore(null);
      return;
    }
    if (!silent) setScoreLoading(true);
    try {
      const vo = await fetchDqScoreAsset("TABLE", tableId);
      setAssetScore(vo);
      setRecomputeMsg("");
    } catch (e) {
      setRecomputeMsg(
        e instanceof Error ? e.message : t("dw.dqRule.score.recomputeFailed"),
      );
    } finally {
      if (!silent) setScoreLoading(false);
    }
  }, [tableId, t]);

  /** 重算: POST /scores/recompute?assetType=TABLE&assetId=xxx; 完成后 silent 重拉 */
  const doRecompute = useCallback(async () => {
    if (!tableId) return;
    setRecomputing(true);
    setRecomputeMsg("");
    try {
      const vo = await recomputeDqScore("TABLE", tableId);
      if (vo) {
        setAssetScore(vo);
        setRecomputeMsg(t("dw.dqRule.score.recomputeSuccess"));
      } else {
        setAssetScore(null);
        setRecomputeMsg(t("dw.dqRule.score.recomputeNoRule"));
      }
    } catch (e) {
      setRecomputeMsg(
        e instanceof Error ? e.message : t("dw.dqRule.score.recomputeFailed"),
      );
    } finally {
      setRecomputing(false);
      // 触发 silent 重新拉 (兜底后端写入又可能被其它端点覆盖)
      void loadScore(true);
    }
  }, [tableId, loadScore, t]);

  // 入口: 拉静态 6 维 + 拉当前资产评分
  useEffect(() => {
    void loadRegistry();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    void loadScore();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tableId]);

  // 雷达 + 卡片共用: 真实维度分 (有评分用 dimensionScores; 无评分用 0.5 占位)
  const radarData = useMemo(() => {
    const real = assetScore?.dimensionScores ?? null;
    return DIM_ORDER.map((d) => {
      const realScore = real ? real[d] : undefined;
      return {
        dimension: d,
        value:
          realScore !== undefined && realScore !== null
            ? realScore
            : 0.5, // 占位
      };
    });
  }, [assetScore]);

  const totalWeight = dims.reduce((s, d) => s + (d.defaultWeight ?? 0), 0);

  /** 6 张卡片：取 dims 中前 6 项 (按 DIM_ORDER 排)。 */
  const orderedDims = useMemo(
    () =>
      DIM_ORDER.map((code) =>
        dims.find((x) => (x.dimension ?? "").toUpperCase() === code),
      ).filter((x): x is DqDimensionRegistryVO => Boolean(x)),
    [dims],
  );

  /** 评分徽章区 */
  const scoreBadge = useMemo(() => {
    if (!tableId) return null;
    if (assetScore) {
      const g = (assetScore.grade ?? "").toUpperCase();
      return (
        <span
          className={`px-2 py-0.5 rounded-full text-[11px] font-bold border ${gradeStyle(styles, g)}`}
        >
          {t(gradeI18nKey(g)).replace(`A `, "").replace(`B `, "").replace(`C `, "").replace(`D `, "").replace(`F `, "")}
        </span>
      );
    }
    if (scoreLoading) {
      return (
        <span
          className={`px-2 py-0.5 rounded-full text-[11px] font-mono ${styles.badgeBg} ${styles.badgeText}`}
        >
          <Loader2 className="w-3 h-3 inline mr-1 animate-spin" />
        </span>
      );
    }
    // 未评分
    return (
      <span
        className={`px-2 py-0.5 rounded-full text-[11px] font-mono ${styles.appBg} ${styles.cardTextMuted}`}
      >
        {t("dw.dqRule.score.neverEvaluated")}
      </span>
    );
  }, [styles, t, tableId, assetScore, scoreLoading]);

  return (
    <div className="h-full overflow-y-auto p-1">
      {/* 顶部标题 + 刷新 */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <Gauge className={`w-4 h-4 ${styles.accentText}`} />
          <span className={`text-sm font-bold ${styles.cardText}`}>{t("dw.dqRule.tabDimension")}</span>
        </div>
        <button
          onClick={() => void loadRegistry()}
          className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold border ${styles.cardBorder} ${styles.cardTextMuted} hover:bg-black/5 dark:hover:bg-white/5 rounded-lg transition cursor-pointer`}
        >
          <RefreshCw className="w-3.5 h-3.5" />
          {t("dw.dqRule.refresh")}
        </button>
      </div>

      {/* 顶：当前选中表健康度区 (T8) */}
      {tableId ? (
        <div className={`mb-4 border ${styles.cardBorder} rounded-xl ${styles.cardBg} p-4 flex items-center gap-4 flex-wrap`}>
          <div className="flex-1 min-w-[220px] flex flex-col gap-1">
            <div className={`text-[10px] font-mono uppercase tracking-wider ${styles.cardTextMuted}`}>
              {t("dw.dqRule.score.title")}
            </div>
            <div className={`flex items-center gap-2`}>
              <span className={`font-mono text-sm ${styles.cardText}`}>{tableId}</span>
              {scoreBadge}
            </div>
            {assetScore ? (
              <div className={`mt-1 flex flex-wrap gap-x-4 gap-y-1 text-[11px] ${styles.cardTextMuted}`}>
                <span>{`${t("dw.dqRule.score.rolledScore")}: ${assetScore.rolledScore.toFixed(3)}`}</span>
                <span>{`${t("dw.dqRule.score.lastEvaluated")}: ${formatTs(assetScore.lastEvaluatedAt)}`}</span>
              </div>
            ) : (
              <div className={`mt-1 text-[11px] ${styles.cardTextMuted}`}>
                {scoreLoading
                  ? t("dw.dqRule.loading")
                  : recomputeMsg || t("dw.dqRule.score.neverEvaluated")}
              </div>
            )}
          </div>
          <button
            onClick={() => void doRecompute()}
            disabled={recomputing}
            className={`flex items-center gap-1.5 px-3 py-1.5 ${styles.accentBg} ${styles.accentHover} ${styles.cardText} text-xs font-bold rounded-lg shadow-xs transition disabled:opacity-50 cursor-pointer`}
          >
            <RefreshCw className={`w-3.5 h-3.5 ${recomputing ? "animate-spin" : ""}`} />
            {recomputing
              ? t("dw.dqRule.score.recomputing")
              : t("dw.dqRule.score.manualRecompute")}
          </button>
        </div>
      ) : (
        <div className={`mb-4 text-xs ${styles.cardTextMuted} flex items-center gap-2`}>
          <AlertCircle className="w-3.5 h-3.5 opacity-60" />
          {t("dw.dqRule.score.pickTableHint")}
        </div>
      )}

      {loading ? (
        <div className="py-24 flex flex-col items-center justify-center">
          <Loader2 className={`w-6 h-6 animate-spin ${styles.accentText}`} />
          <span className={`mt-3 text-xs ${styles.cardTextMuted}`}>{t("dw.dqRule.loading")}</span>
        </div>
      ) : orderedDims.length === 0 ? (
        <div className="py-24 flex flex-col items-center justify-center">
          <AlertCircle className={`w-8 h-8 ${styles.warningText} opacity-60`} />
          <span className={`mt-3 text-sm font-semibold ${styles.cardText}`}>{t("dw.dqRule.dimensionEmpty")}</span>
          {error ? <span className="mt-1 text-xs text-danger-500">{error}</span> : null}
        </div>
      ) : (
        <div className="grid grid-cols-12 gap-4">
          {/* 雷达图卡片 (col-span-8) */}
          <div className={`col-span-8 border ${styles.cardBorder} rounded-xl ${styles.cardBg} p-4`}>
            <div className="flex flex-col items-center justify-center">
              <div className={`text-[10px] font-mono uppercase tracking-wider ${styles.cardTextMuted} mb-2`}>
                {t("dw.dqRule.radarTitle")}
              </div>
              <div className="w-full h-[360px]">
                <ResponsiveContainer width="100%" height="100%">
                  <RadarChart data={radarData} cx="50%" cy="50%" outerRadius="72%">
                    <PolarGrid stroke={styles.cardBorder} />
                    <PolarAngleAxis
                      dataKey="dimension"
                      tick={{ fill: "currentColor", fontSize: 11, fontFamily: "ui-monospace, monospace" }}
                    />
                    <Radar
                      dataKey="value"
                      stroke="var(--ecos-accent, #3b82f6)"
                      fill="var(--ecos-accent, #3b82f6)"
                      fillOpacity={0.25}
                    />
                  </RadarChart>
                </ResponsiveContainer>
              </div>
              <div className={`mt-2 text-[11px] ${styles.cardTextMuted}`}>
                {t("dw.dqRule.totalWeight").replace("{total}", totalWeight.toFixed(2))}
              </div>
            </div>
          </div>

          {/* 6 张维度卡片 (col-span-4, 1×6 竖向 / 2×3 网格) */}
          <div className="col-span-4 grid grid-cols-1 gap-3 content-start">
            {orderedDims.map((d) => {
              const code = d.dimension ?? "";
              const score = assetScore?.dimensionScores?.[code];
              const Icon = DIMENSION_ICONS[code] ?? Layers;
              return (
                <div
                  key={code}
                  className={`border ${styles.cardBorder} rounded-xl ${styles.cardBg} p-3 flex flex-col gap-1.5 hover:shadow-sm transition shadow-3xs`}
                >
                  <div className="flex items-center justify-between">
                    <Icon className={`w-4 h-4 ${styles.accentText}`} />
                    <span className={`px-1.5 py-0.5 rounded ${styles.badgeBg} ${styles.badgeText} font-mono text-[10px] font-bold`}>
                      {d.defaultWeight}
                    </span>
                  </div>
                  <div>
                    <div className={`font-bold text-xs ${styles.cardText}`}>{d.name}</div>
                    <div className={`mt-0.5 font-mono text-[10px] ${styles.cardTextMuted} uppercase tracking-wider truncate`}>
                      {code}
                    </div>
                  </div>
                  {/* T8 真实评分 0.0-1.0 (有评分时) */}
                  {score !== undefined && score !== null ? (
                    <div className={`font-mono text-[11px] ${styles.accentText} font-bold`}>
                      {`score: ${score.toFixed(3)}`}
                    </div>
                  ) : (
                    <div className={`font-mono text-[11px] ${styles.cardTextMuted}`}>
                      {`score: ${((d.defaultWeight ?? 0) / 100).toFixed(3)} (w)`}
                    </div>
                  )}
                  {d.ruleTypes && d.ruleTypes.length > 0 ? (
                    <div className="flex flex-wrap gap-1">
                      {d.ruleTypes.slice(0, 4).map((rt) => (
                        <span
                          key={rt}
                          className={`px-1.5 py-0.5 rounded ${styles.appBg} ${styles.cardTextMuted} font-mono text-[9px]`}
                        >
                          {rt}
                        </span>
                      ))}
                    </div>
                  ) : null}
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

/** 时间格式化 (本地化由后端/前端 i18n 负责, 这里只输出 ISO 短格式) */
function formatTs(iso?: string): string {
  if (!iso) return "-";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString();
}
