/**
 * DiagnosticsTab — 平台治理「平台自检」页签（2026-10-14 菜单重构，#/platform-governance）。
 *
 * 平台自检全量结果：monitor /health 单次探测（runSystemDiagnostics，
 * 端点 /api/v1/monitor/health；MONITOR_BASE 常量承载服务标识，不另猜服务名）。
 * 三态 loading/error/data，不做自动轮询，失败显式错误框 + 手动「重新探测」。
 * 只消费 monitorClient 既有 4 字段投影（status/version/uptime_ms/database），不重扩投影。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */
import React, { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Activity, ArrowRight, Cpu, Database, RefreshCw, Server, ShieldCheck } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { runSystemDiagnostics, MONITOR_BASE } from "../../api/monitorClient";
import logger from "../../services/logger";

/** 与 monitorClient.runSystemDiagnostics 返回型逐字段对齐（该文件未导出具名 type）。 */
interface DiagnosticsResult {
  database: { status: string };
  uptime_ms: number;
  version: string;
  status: string;
}

/** 服务标识 = monitorClient 的 MONITOR_BASE（数据值，非翻译文案；基址单源不另硬编码）。 */
const SERVICE_ID = MONITOR_BASE;

type Phase =
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "data"; data: DiagnosticsResult };

/** 与 DeploymentTab 同口径的四态 + unknown 映射（本地内联副本，不跨文件 import）。 */
function statusTone(s: string): { className: string; labelKey: string } {
  const u = (s ?? "").toUpperCase();
  switch (u) {
    case "UP":
      return { className: "success", labelKey: "platform.governance.deployment.stateUp" };
    case "DEGRADED":
      return { className: "warning", labelKey: "platform.governance.deployment.stateDegraded" };
    case "DOWN":
      return { className: "danger", labelKey: "platform.governance.deployment.stateDown" };
    case "DISABLED":
      return { className: "info", labelKey: "platform.governance.deployment.stateDisabled" };
    default:
      return { className: "muted", labelKey: "platform.governance.deployment.unknown" };
  }
}

function errMsg(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

export default function DiagnosticsTab() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const navigate = useNavigate();
  const [phase, setPhase] = useState<Phase>({ kind: "loading" });

  const load = useCallback(async () => {
    setPhase({ kind: "loading" });
    try {
      const result: DiagnosticsResult = await runSystemDiagnostics();
      setPhase({ kind: "data", data: result });
    } catch (e) {
      logger.error("governance-diagnostics", e);
      setPhase({ kind: "error", message: errMsg(e) });
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const toneClass = (tone: string) =>
    tone === "success" ? `${styles.successBg} ${styles.successText}`
      : tone === "warning" ? `${styles.warningBg} ${styles.warningText}`
        : tone === "danger" ? `${styles.dangerBg} ${styles.dangerText}`
          : tone === "info" ? `${styles.infoBg} ${styles.infoText}`
            : `${styles.cardTextMuted}`;

  const pill = (raw: string) => {
    const m = statusTone(raw);
    return (
      <span
        className={`inline-flex items-center rounded border px-2 py-0.5 text-[11px] font-mono font-bold ${toneClass(m.className)} ${m.className === "muted" ? styles.cardBorder : ""}`}
      >
        {t(m.labelKey)}
      </span>
    );
  };

  const retryBtn = (
    <button
      type="button"
      onClick={() => void load()}
      className={`inline-flex items-center gap-1 rounded border px-2.5 py-1 text-[11px] font-semibold cursor-pointer ${styles.cardBorder} ${styles.cardTextMuted} hover:opacity-80`}
    >
      <RefreshCw size={12} />
      {t("platform.governance.deployment.retry")}
    </button>
  );

  const statBox = (icon: React.ReactNode, label: string, value: React.ReactNode) => (
    <div className={`flex items-center gap-2 rounded-lg border px-3 py-2.5 ${styles.cardBorder}`}>
      <span className={styles.cardTextMuted}>{icon}</span>
      <div className="min-w-0">
        <p className={`text-[10px] font-bold uppercase ${styles.cardTextMuted}`}>{label}</p>
        <div className="truncate text-[12px] font-mono font-bold">{value}</div>
      </div>
    </div>
  );

  const ctaTile = (to: string, label: string) => (
    <button
      type="button"
      onClick={() => navigate(to)}
      className={`flex items-center justify-between rounded-lg border px-3 py-2.5 text-left cursor-pointer ${styles.cardBorder} ${styles.accentText} hover:opacity-80`}
    >
      <span className="text-[11px] font-semibold">{label}</span>
      <ArrowRight size={14} />
    </button>
  );

  return (
    <div className="flex-1 min-h-0 flex flex-col gap-3 p-4 overflow-y-auto">
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs shrink-0">{t("platform.governance.diagnostics.desc")}</p>
        {retryBtn}
      </div>

      <div className={`rounded-lg border p-4 ${styles.cardBg} ${styles.cardBorder} flex flex-col gap-3`}>
        <div className="flex items-center gap-2">
          <ShieldCheck size={14} className={styles.cardTextMuted} />
          {phase.kind === "data" ? pill(phase.data.status) : <Spinner />}
        </div>

        {phase.kind === "error" && (
          <div className={`rounded-lg border px-3 py-3 text-[11px] ${styles.dangerBg} ${styles.cardBorder} ${styles.dangerText}`}>
            {t("platform.governance.diagnostics.loadFailed")}
            <span className="ml-2 font-mono opacity-80">{phase.message}</span>
          </div>
        )}

        {phase.kind === "data" && (
          <>
            <div className="grid grid-cols-2 gap-2 md:grid-cols-3">
              {statBox(<Cpu size={14} />, t("platform.governance.deployment.version"), phase.data.version)}
              {statBox(
                <Activity size={14} />,
                t("platform.governance.deployment.uptime"),
                <>
                  {phase.data.uptime_ms.toLocaleString()}
                  <span className={`ml-1 text-[10px] ${styles.cardTextMuted}`}>ms</span>
                </>,
              )}
              {statBox(
                <Database size={14} />,
                t("platform.governance.deployment.state"),
                pill(phase.data.database.status),
              )}
              {statBox(<Server size={14} />, t("platform.governance.deployment.service"), SERVICE_ID)}
            </div>

            <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
              {ctaTile("/gateway-diagnostics", t("platform.governance.diagnostics.openGateway"))}
              {ctaTile("/telemetry", t("platform.governance.diagnostics.openTelemetry"))}
            </div>
          </>
        )}
      </div>
    </div>
  );
}

function Spinner() {
  return <span className="inline-block h-3.5 w-3.5 animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />;
}
