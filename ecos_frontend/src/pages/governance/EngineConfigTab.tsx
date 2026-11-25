/**
 * EngineConfigTab — 平台治理「引擎配置」页签（route #/platform-governance?tab=config）。
 *
 * 消费契约（只读，写入事实源在后端；写操作去系统配置管理页）：
 *   GET /api/v1/system/config         → 扁平行（group/key/value/type/default_value/description/sort_order）
 *   GET /api/v1/system/config/audit   → 消费态（is_consumed / consumed_by），**只有这个口有**
 * 两路并行拉；配置腿失败即整页错误态（fail-closed），消费态腿失败降级为「—」并显式提示，
 * 绝不用「否」冒充「未检测到」。
 *
 * 2026-11-25 改版要点（原实现：21 组 × 平铺 4 列表格，188 行无层级、档位列恒为 all、
 * 消费态恒显示「否」）：
 *   - 页头指标条：配置项 / 分组 / 已消费 / 偏离默认 四数（全部真实计算）
 *   - 工具条：搜索（键名/标签/描述/值）+ 三态筛选（全部 / 已消费 / 偏离默认）+ 刷新
 *   - 分组卡片：可折叠、组头带条目数与 Δ/消费徽标，默认展开首组
 *   - 行内主信息三层：键名（等宽）→ 中文标签 → 说明/默认值；档位降级为行内小注
 *   - 值渲染按类型分派（ConfigValueCell）：布尔/枚举/密文打码/数值
 *
 * 文案只用 platform.governance.config.* 键；颜色只用 useTheme() token。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { AlertTriangle, ExternalLink, RefreshCw, Search, SlidersHorizontal, X } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import ConfigGroupCard from "./ConfigGroupCard";
import {
  computeStats,
  searchConfigs,
  sortGroups,
  sortItems,
  type ConsumptionMap,
  type SysConfigGrouped,
  type SysConfigItem,
} from "./engineConfigModel";
import {
  fetchSysConfigRowsStrict,
  fetchConfigConsumptionStrict,
  type ConfigConsumptionEntry,
  type SysConfigRow,
} from "../../api/dictConfigClient";
import logger from "../../services/logger";

type FilterId = "all" | "consumed" | "override";

const FILTERS: ReadonlyArray<{ id: FilterId; labelKey: string }> = [
  { id: "all", labelKey: "platform.governance.config.filter.all" },
  { id: "consumed", labelKey: "platform.governance.config.filter.consumed" },
  { id: "override", labelKey: "platform.governance.config.filter.override" },
];

function errMsg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

/** 传输行 → 归一 item，并按 group 聚合（分组顺序与组内顺序都排稳）。 */
function normalize(rows: SysConfigRow[]): SysConfigGrouped[] {
  const byGroup = new Map<string, SysConfigItem[]>();
  for (const row of rows) {
    const key = String(row.config_key ?? "").trim();
    if (!key) continue; // 无键行无法定位，直接跳过而不是造一个假键
    const group = String(row.config_group ?? "").trim() || "global";
    const item: SysConfigItem = {
      key,
      value: row.config_value === null || row.config_value === undefined ? "" : String(row.config_value),
      label: String(row.config_label_en ?? row.config_label ?? key),
      labelZh: String(row.config_label ?? key),
      description: String(row.description ?? ""),
      descriptionZh: String(row.description ?? ""),
      group,
      type: String(row.config_type ?? "string"),
      options: Array.isArray(row.config_options) ? (row.config_options as unknown[]).map(String) : undefined,
      impactScope: row.impact_scope ? String(row.impact_scope) : undefined,
      edition: row.edition ? String(row.edition) : undefined,
      default_value: row.default_value === null || row.default_value === undefined ? "" : String(row.default_value),
      sort_order: typeof row.sort_order === "number" ? row.sort_order : Number.MAX_SAFE_INTEGER,
    };
    const bucket = byGroup.get(group);
    if (bucket) bucket.push(item);
    else byGroup.set(group, [item]);
  }
  const groups: SysConfigGrouped[] = [];
  for (const [group, items] of byGroup) {
    groups.push({ group, items: sortItems(items) });
  }
  return sortGroups(groups);
}

/** 审计条目 → 消费态 Map；consumed_by 可能是 "A, B" 串或 null。 */
function toConsumptionMap(entries: Map<string, ConfigConsumptionEntry>): ConsumptionMap {
  const out: Record<string, { consumed: boolean; consumedBy: string[] }> = {};
  for (const [key, entry] of entries) {
    const consumedBy = String(entry.consumed_by ?? "")
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
    out[key] = { consumed: entry.is_consumed === true, consumedBy };
  }
  return out;
}

type Phase =
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "data"; groups: SysConfigGrouped[]; consumption: ConsumptionMap; consumptionAvailable: boolean; auditError: string | null };

export default function EngineConfigTab() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const navigate = useNavigate();

  const [phase, setPhase] = useState<Phase>({ kind: "loading" });
  const [keyword, setKeyword] = useState("");
  const [filter, setFilter] = useState<FilterId>("all");
  /** 手动折叠/展开覆盖：未在表里的组按「首组展开、其余收起」的默认策略 */
  const [toggled, setToggled] = useState<Record<string, boolean>>({});
  const keywordRef = useRef<HTMLInputElement | null>(null);

  const load = useCallback(async () => {
    setPhase({ kind: "loading" });
    // 两腿并行：配置腿失败 = 整页失败；消费态腿失败 = 降级（不可推断为「未消费」）
    const [configResult, auditResult] = await Promise.allSettled([
      fetchSysConfigRowsStrict(),
      fetchConfigConsumptionStrict(),
    ]);

    if (configResult.status === "rejected") {
      logger.error("governance-config", configResult.reason);
      setPhase({ kind: "error", message: errMsg(configResult.reason) });
      return;
    }

    const groups = normalize(configResult.value);
    if (auditResult.status === "fulfilled") {
      setPhase({
        kind: "data",
        groups,
        consumption: toConsumptionMap(auditResult.value),
        consumptionAvailable: true,
        auditError: null,
      });
      return;
    }

    logger.error("governance-config-audit", auditResult.reason);
    setPhase({
      kind: "data",
      groups,
      consumption: {},
      consumptionAvailable: false,
      auditError: errMsg(auditResult.reason),
    });
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const groups = phase.kind === "data" ? phase.groups : [];
  const consumption: ConsumptionMap = phase.kind === "data" ? phase.consumption : {};

  const stats = useMemo(() => computeStats(groups, consumption), [groups, consumption]);

  /** 搜索 + 筛选合成一条管线：先按筛选条件裁条目，再按关键字裁，空组自动剔除 */
  const visible = useMemo(() => {
    const base: SysConfigGrouped[] = filter === "all"
      ? groups
      : groups
          .map((g) => ({
            group: g.group,
            items: g.items.filter((item) => {
              if (filter === "consumed") return consumption[item.key]?.consumed === true;
              // override 筛选复用模型层 deltaState，避免此处重复口径
              return item.default_value !== "" && item.value !== item.default_value;
            }),
          }))
          .filter((g) => g.items.length > 0);
    return searchConfigs(base, keyword);
  }, [groups, consumption, filter, keyword]);

  const isGroupOpen = useCallback(
    (group: string, index: number): boolean => {
      const override = toggled[group];
      if (override !== undefined) return override;
      // 有搜索/筛选时全部展开（用户正在找东西，不该再点开）；否则只展开首组
      if (keyword.trim() !== "" || filter !== "all") return true;
      return index === 0;
    },
    [toggled, keyword, filter],
  );

  const toggleGroup = useCallback((group: string, current: boolean) => {
    setToggled((prev) => ({ ...prev, [group]: !current }));
  }, []);

  // ── 四态 ─────────────────────────────────────────────

  if (phase.kind === "loading") {
    return (
      <div className="flex-1 min-h-0 flex flex-col items-center justify-center gap-2 p-4">
        <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />
        <span className={`text-[11px] ${styles.cardTextMuted}`}>{t("platform.dictionary.loading")}</span>
      </div>
    );
  }

  if (phase.kind === "error") {
    return (
      <div className="flex-1 min-h-0 flex flex-col gap-3 p-4">
        <div className={`rounded-lg border p-4 flex flex-col gap-2 ${styles.dangerBg} ${styles.dangerBorder}`}>
          <div className={`flex items-center gap-2 ${styles.dangerText}`}>
            <AlertTriangle size={14} />
            <span className="text-xs font-bold">{t("platform.governance.config.loadFailed")}</span>
          </div>
          <span className="text-[11px] font-mono break-words">{phase.message}</span>
          <button
            type="button"
            onClick={() => void load()}
            className={`inline-flex items-center gap-1 self-start rounded border px-2.5 py-1 text-[11px] font-semibold cursor-pointer hover:opacity-80 ${styles.dangerBorder} ${styles.dangerText}`}
          >
            <RefreshCw size={12} />
            {t("platform.governance.deployment.retry")}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1 min-h-0 flex flex-col gap-3 p-4 overflow-y-auto">
      {/* 指标条 */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
        <StatTile label={t("platform.governance.config.statItems")} value={stats.items} />
        <StatTile label={t("platform.governance.config.statGroups")} value={stats.groups} />
        <StatTile
          label={t("platform.governance.config.statConsumed")}
          value={phase.consumptionAvailable ? stats.consumed : "—"}
          tone={phase.consumptionAvailable ? "success" : "muted"}
        />
        <StatTile
          label={t("platform.governance.config.statOverrides")}
          value={stats.overrides}
          tone={stats.overrides > 0 ? "warning" : "muted"}
        />
      </div>

      {/* 工具条：搜索 + 筛选 + 刷新 + 写入口 */}
      <div className="flex flex-wrap items-center gap-2">
        <label
          className={`inline-flex items-center gap-1.5 rounded border px-2 py-1 flex-1 min-w-[180px] ${styles.inputBg} ${styles.inputBorder}`}
        >
          <Search size={12} className={styles.cardTextMuted} />
          <input
            ref={keywordRef}
            type="search"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder={t("platform.governance.config.search")}
            className={`flex-1 min-w-0 bg-transparent text-[11px] outline-none ${styles.inputText} ${styles.placeholderText}`}
          />
          {keyword && (
            <button
              type="button"
              onClick={() => {
                setKeyword("");
                keywordRef.current?.focus();
              }}
              className={`shrink-0 cursor-pointer ${styles.cardTextMuted} hover:opacity-80`}
              aria-label={t("platform.governance.config.filter.all")}
            >
              <X size={12} />
            </button>
          )}
        </label>

        <div className="flex items-center gap-1" role="group" aria-label={t("platform.governance.config.filter")}>
          {FILTERS.map(({ id, labelKey }) => {
            const active = filter === id;
            return (
              <button
                key={id}
                type="button"
                aria-pressed={active}
                onClick={() => setFilter(id)}
                className={`shrink-0 rounded border px-2 py-1 text-[11px] font-semibold cursor-pointer transition-colors ${active ? `${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}` : `${styles.cardBorder} ${styles.cardTextMuted} hover:opacity-80`}`}
              >
                {t(labelKey)}
              </button>
            );
          })}
        </div>

        <button
          type="button"
          onClick={() => void load()}
          className={`inline-flex items-center gap-1 rounded border px-2.5 py-1 text-[11px] font-semibold cursor-pointer shrink-0 hover:opacity-80 ${styles.cardBorder} ${styles.cardTextMuted}`}
        >
          <RefreshCw size={12} />
          {t("platform.governance.deployment.retry")}
        </button>

        <button
          type="button"
          onClick={() => navigate("/system-config")}
          className={`inline-flex items-center gap-1 rounded border px-2.5 py-1 text-[11px] font-semibold cursor-pointer shrink-0 hover:opacity-80 ${styles.cardBorder} ${styles.accentText}`}
        >
          <ExternalLink size={12} />
          {t("platform.governance.config.openManager")}
        </button>
      </div>

      {/* 说明 + 消费态降级提示 */}
      <div className="flex flex-col gap-1">
        <p className={`text-[11px] ${styles.cardTextMuted}`}>{t("platform.governance.config.readOnly")}</p>
        {!phase.consumptionAvailable && (
          <p className={`inline-flex items-center gap-1.5 rounded border px-2 py-1 text-[11px] ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
            <AlertTriangle size={11} className="shrink-0" />
            <span className="min-w-0 break-words">
              {t("platform.governance.config.auditUnavailable")}
              {phase.auditError && <span className="ml-1 font-mono opacity-80">{phase.auditError}</span>}
            </span>
          </p>
        )}
      </div>

      {/* 分组卡片区：空态 / 无匹配 / 数据 */}
      {visible.groups.length === 0 ? (
        <div className={`rounded-lg border p-8 text-center text-xs ${styles.cardBg} ${styles.cardBorder} ${styles.cardTextMuted}`}>
          {keyword.trim() !== ""
            ? t("platform.governance.config.noMatch").replace("{q}", keyword.trim())
            : t("platform.governance.config.empty")}
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {visible.groups.map((g, i) => {
            const open = isGroupOpen(g.group, i);
            return (
              <ConfigGroupCard
                key={g.group}
                group={g}
                consumption={consumption}
                consumptionAvailable={phase.consumptionAvailable}
                expanded={open}
                onToggle={() => toggleGroup(g.group, open)}
              />
            );
          })}
        </div>
      )}

      {/* 命中统计（搜索/筛选生效时才有意义） */}
      {(keyword.trim() !== "" || filter !== "all") && (
        <p className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
          {visible.matchedItems} / {stats.items}
        </p>
      )}
    </div>
  );
}

/** 指标卡：数字为主、标签为辅；色调只区分语义，不改布局 */
function StatTile({
  label,
  value,
  tone = "muted",
}: {
  label: string;
  value: number | string;
  tone?: "muted" | "success" | "warning";
}) {
  const { styles } = useTheme();
  const toneText =
    tone === "success" ? styles.successText : tone === "warning" ? styles.warningText : styles.cardText;
  return (
    <div className={`rounded-lg border px-3 py-2 flex flex-col gap-0.5 min-w-0 ${styles.cardBg} ${styles.cardBorder}`}>
      <span className={`text-[10px] font-bold uppercase truncate ${styles.cardTextMuted}`}>{label}</span>
      <span className={`text-base font-mono font-bold tabular-nums leading-tight ${toneText}`}>{value}</span>
    </div>
  );
}
