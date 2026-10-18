/**
 * DeploymentTab — 平台治理「部署情况」页签（2026-10-14 菜单重构）。
 *
 * 各服务/引擎健康矩阵：逐行真实探测（fail-closed，探测失败显式 DOWN 不清空），
 * 四态映射 stateUp/stateDegraded/stateDown/stateDisabled（未知→unknown）。
 * 版本/运行时长只在真实下发时渲染，否则占位破折号（无健康 VO 无该字段，不臆造）。
 *
 * 探测面（gateway 聚合宿主，均三滤波器放行）：
 *   kernel     apiHealth() —— httpClient 单源（基址 "/api" 只在 httpClient 定义）
 *   cognitive  GET /api/v1/engine/cognitive/health
 *   data       GET /api/v1/engine/data/health
 *   ontology   GET /api/v1/engine/ontology/health
 *   knowledge  GET /api/v1/engine/knowledge/health
 *   security   GET /api/v1/engine/security/health
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */
import React, { useCallback, useEffect, useState } from "react";
import { RefreshCw, Server } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { apiHealth } from "../../api";
import { fetchEngineHealth } from "../../services/cognitiveClient";
import { fetchOntologyEngineHealth } from "../../services/ontology/ontologyEngineApi";
import { fetchDataEngineHealth } from "../../api/dataClient";
import { fetchKnowledgeEngineHealth } from "../../api/knowledgeClient";
import { fetchSecurityEngineHealth } from "../../api/securityClient";
import logger from "../../services/logger";

interface RowState {
  loading: boolean;
  error: string | null;
}

interface Row extends RowState {
  status: string | null; // 原始状态串（UP/DOWN/DISABLED/...），null = 未取到
  components: Record<string, string>; // 组件级明细（如 neo4j/db），可空
  version: string | null;
  uptimeMs: number | null;
}

interface RowDef {
  key: string; // 同时作为服务标签（术语串，不翻译）
  /** 单源探测通道（基址统一在 client/apiFetchData/httpClient 内定义，本文件零 "/api" 字面量） */
  probe: () => Promise<unknown>;
}

const ROWS: ReadonlyArray<RowDef> = [
  {
    key: "kernel",
    // apiHealth 单源：其返回值即后端 /api/health 的 status（{status} 口径），包一层统一探测 body 形态
    probe: async (): Promise<unknown> => ({ status: await apiHealth() }),
  },
  { key: "cognitive", probe: fetchEngineHealth },
  { key: "data", probe: fetchDataEngineHealth },
  { key: "ontology", probe: fetchOntologyEngineHealth },
  { key: "knowledge", probe: fetchKnowledgeEngineHealth },
  { key: "security", probe: fetchSecurityEngineHealth },
];

type RowMap = Record<string, Row>;

function errMsg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

/** components 明细 → 整体降级判定：主状态 UP 但任一组件 DOWN ⇒ 展示降级。 */
function effectiveStatus(status: string, components: Record<string, string>): string {
  const s = (status ?? "").toUpperCase();
  if (s === "UP") {
    const vals = Object.values(components);
    if (vals.some((v) => v.toUpperCase() === "DOWN")) return "DEGRADED";
  }
  return s || "UNKNOWN";
}

export default function DeploymentTab() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [rows, setRows] = useState<RowMap>(() =>
    Object.fromEntries(ROWS.map((r: RowDef): [string, Row] => [r.key, {
      loading: true, error: null, status: null, components: {}, version: null, uptimeMs: null,
    }])),
  );

  const probe = useCallback(async (def: RowDef) => {
    setRows((p) => ({ ...p, [def.key]: { ...p[def.key], loading: true, error: null } }));
    let payload: Record<string, unknown>;
    try {
      const raw = await def.probe();
      payload = (raw && typeof raw === "object" && !Array.isArray(raw)
        ? raw
        : {}) as Record<string, unknown>;
    } catch (e) {
      logger.error("governance-deployment-probe", e);
      setRows((p) => ({
        ...p,
        [def.key]: { ...p[def.key], loading: false, error: errMsg(e), status: "DOWN", components: {}, version: null, uptimeMs: null },
      }));
      return;
    }
    const components: Record<string, string> = {};
    const compRaw = payload?.components;
    if (compRaw && typeof compRaw === "object") {
      for (const [k, v] of Object.entries(compRaw as Record<string, unknown>)) {
        if (v && typeof v === "object" && "status" in (v as Record<string, unknown>)) {
          components[k] = String((v as Record<string, unknown>).status);
        } else if (typeof v === "string") {
          components[k] = v;
        }
      }
    }
    const rawStatus =
      typeof payload.status === "string" && payload.status ? payload.status : "";
    setRows((p) => ({
      ...p,
      [def.key]: {
        loading: false,
        error: null,
        status: rawStatus ? effectiveStatus(rawStatus, components) : null,
        components,
        version: typeof payload.version === "string" && payload.version ? payload.version : null,
        uptimeMs: typeof payload.uptimeMs === "number" ? payload.uptimeMs : null,
      },
    }));
  }, []);

  const probeAll = useCallback(() => {
    ROWS.forEach((r) => void probe(r));
  }, [probe]);

  useEffect(() => {
    probeAll();
  }, [probeAll]);

  const statePill = (row: Row, key: string) => {
    if (row.loading) return <Spinner />;
    const s = (row.status ?? "").toUpperCase();
    const tone =
      s === "UP" ? `${styles.successBg} ${styles.successText}`
      : s === "DEGRADED" ? `${styles.warningBg} ${styles.warningText}`
      : s === "DISABLED" ? `${styles.infoBg} ${styles.infoText}`
      : `${styles.dangerBg} ${styles.dangerText}`;
    const labelKey =
      s === "UP" ? "platform.governance.deployment.stateUp"
      : s === "DEGRADED" ? "platform.governance.deployment.stateDegraded"
      : s === "DOWN" ? "platform.governance.deployment.stateDown"
      : s === "DISABLED" ? "platform.governance.deployment.stateDisabled"
      : "platform.governance.deployment.unknown";
    return (
      <span className={`inline-flex items-center gap-1 rounded border px-1.5 py-0.5 text-[10px] font-mono font-bold ${tone} ${styles.cardBorder}`} title={row.error ?? undefined} data-row={key}>
        {t(labelKey)}
      </span>
    );
  };

  return (
    <div className="flex-1 min-h-0 flex flex-col gap-3 p-4 overflow-y-auto">
      <div className={`flex items-center justify-between gap-2`}>
        <p className="text-xs shrink-0">{t("platform.governance.deployment.desc")}</p>
        <button
          type="button"
          onClick={probeAll}
          className={`inline-flex items-center gap-1 rounded border px-2.5 py-1 text-[11px] font-semibold cursor-pointer shrink-0 ${styles.cardBorder} ${styles.cardTextMuted} hover:opacity-80`}
        >
          <RefreshCw size={12} />
          {t("platform.governance.deployment.retry")}
        </button>
      </div>

      <div className={`rounded-lg border overflow-hidden ${styles.cardBg} ${styles.cardBorder}`}>
        <table className="w-full text-[11px]">
          <thead>
            <tr className={`border-b text-left ${styles.cardBorder} ${styles.cardTextMuted}`}>
              <th className="px-3 py-2 font-bold uppercase">{t("platform.governance.deployment.service")}</th>
              <th className="px-3 py-2 font-bold uppercase">{t("platform.governance.deployment.state")}</th>
              <th className="px-3 py-2 font-bold uppercase">{t("platform.governance.deployment.version")}</th>
              <th className="px-3 py-2 font-bold uppercase">{t("platform.governance.deployment.uptime")}</th>
            </tr>
          </thead>
          <tbody>
            {ROWS.map((def) => {
              const row = rows[def.key];
              return (
                <tr key={def.key} className={`border-b last:border-b-0 ${styles.cardBorder}`}>
                  <td className="px-3 py-2">
                    <span className="inline-flex items-center gap-1.5 font-mono font-bold">
                      <Server size={12} className={styles.cardTextMuted} />
                      {def.key}
                    </span>
                  </td>
                  <td className="px-3 py-2">
                    {statePill(row, def.key)}
                    {Object.keys(row.components).length > 0 && (
                      <span className={`ml-2 text-[10px] font-mono ${styles.cardTextMuted}`}>
                        {Object.entries(row.components).map(([k, v]) => `${k}:${v}`).join(" ")}
                      </span>
                    )}
                  </td>
                  <td className="px-3 py-2 font-mono">{row.version ?? "—"}</td>
                  <td className="px-3 py-2 font-mono">
                    {row.uptimeMs == null ? "—" : `${Math.round(row.uptimeMs / 60000)}m`}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function Spinner() {
  return <span className="inline-block h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />;
}
