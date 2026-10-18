/**
 * P2-6 AlertPanel — 告警面板：规则 + 历史 + 确认动作
 *
 * <p>承流现状（台账 D-B5-32，2026-10-19 源级取证）：`/api/alerts/rules`、`/history`、
 * `/test` 三族后端零映射，`/api/alerts` 列表是恒空壳、详情与 ack 自回 notFound；
 * 实时推送亦未接线（gateway `WebSocketConfig` 的 `/ws` 只开 SockJS，且全仓无
 * `/topic/alerts` 发布者，dev/BFF 也无 ws 代理）。故本面板一律显式呈现「未承流 /
 * 未接线」，不以任何空列表或静默成功冒充可用。</p>
 */
import React, { useState, useEffect, useCallback } from "react";
import { Bell, AlertTriangle, CheckCircle2, XCircle, RefreshCw, Zap, History, Trash2, Plus } from "lucide-react";
import { useLanguage } from "../pages/../components/LanguageContext";
import { useTheme } from "../pages/../components/ThemeContext";
import ConfirmDialog from "./common/ConfirmDialog";
import { showToastGlobal } from "./common/Toast";
import {
  fetchAlertRules,
  fetchAlertHistory,
  alertTest,
  alertAck,
  alertCreateRule,
  alertDeleteRule,
  AlertApiError,
} from "../services/alertsApi";

interface AlertRule {
  id: number;
  name: string;
  description: string;
  metric: string;
  operator: string;
  threshold: number;
  level: string;
  enabled: boolean;
}

interface AlertRecord {
  id: number;
  rule_name: string;
  level: string;
  message: string;
  metric_value: number;
  threshold: number;
  acknowledged: boolean;
  created_at: string;
}

export default function AlertPanel() {
  const { t } = useLanguage();
  const { styles } = useTheme();

  const [rules, setRules] = useState<AlertRule[]>([]);
  const [history, setHistory] = useState<AlertRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [notWired, setNotWired] = useState(false);
  const [testValue, setTestValue] = useState("50");
  const [showNewRule, setShowNewRule] = useState(false);
  const [newRule, setNewRule] = useState({ name: "", metric: "", operator: "<", threshold: "80", level: "WARN" });
  const [pendingDelete, setPendingDelete] = useState<{ id: number; name: string } | null>(null);

  // ── Fetch rules + history（逐腿独立归类，未承流≠空列表）────
  /**
   * 「后端未承流」只由 `ENDPOINT_NOT_WIRED` 触发（真实 404 与 200 包壳 `code:404` 已在
   * alertsApi 同码归一）。业务失败（如信封 `code:-1` 系统错）不得冒充未承流横幅。
   */
  const isNotWired = (e: unknown): boolean => e instanceof AlertApiError && e.code === "ENDPOINT_NOT_WIRED";

  const rowsOf = (payload: unknown): unknown[] => (Array.isArray(payload) ? payload : []);

  const fetchAll = useCallback(async () => {
    setLoading(true);
    setError("");
    const settled = await Promise.allSettled([fetchAlertRules(), fetchAlertHistory(20)]);
    const [rRes, hRes] = settled;
    if (rRes.status === "fulfilled") setRules(rowsOf(rRes.value) as AlertRule[]);
    else setRules([]);
    if (hRes.status === "fulfilled") setHistory(rowsOf(hRes.value) as AlertRecord[]);
    else setHistory([]);
    const failures = settled.filter((s) => s.status === "rejected") as PromiseRejectedResult[];
    if (failures.length > 0) {
      setNotWired(failures.every((f) => isNotWired(f.reason)));
      const first = failures.find((f) => !isNotWired(f.reason)) ?? failures[0];
      const msg = first.reason instanceof Error ? first.reason.message : String(first.reason);
      setError(`${t("platform.alert.loadFail")}: ${msg}`);
    } else {
      setNotWired(false);
    }
    setLoading(false);
  }, [t]);

  useEffect(() => { fetchAll(); }, [fetchAll]);

  // ── Actions ───────────────────────────────
  const triggerAlert = async (ruleId?: number) => {
    try {
      const body: Record<string, unknown> = { metricValue: testValue };
      if (ruleId) body.ruleId = ruleId;
      await alertTest(body);
      fetchAll();
    } catch (e: any) {
      setError(e?.message || t("platform.alert.triggerFail"));
    }
  };

  const ackAlert = async (id: number) => {
    try {
      await alertAck(id);
      fetchAll();
    } catch (e: any) {
      showToastGlobal("error", e?.message || t("platform.alert.ackFail"));
    }
  };

  const createRule = async () => {
    try {
      await alertCreateRule(newRule);
      setShowNewRule(false);
      setNewRule({ name: "", metric: "", operator: "<", threshold: "80", level: "WARN" });
      fetchAll();
    } catch (e: any) {
      showToastGlobal("error", e?.message || t("platform.alert.createFail"));
    }
  };

  const deleteRule = async (id: number) => {
    try {
      await alertDeleteRule(id);
      setPendingDelete(null);
      fetchAll();
    } catch (e: any) {
      showToastGlobal("error", e?.message || t("platform.alert.deleteFailed"));
    }
  };

  const levelColor = (level: string) => {
    if (level === "CRITICAL") return "bg-red-500/20 text-red-400 border-red-500/30";
    if (level === "WARN") return "bg-amber-500/20 text-amber-400 border-amber-500/30";
    return "bg-blue-500/20 text-blue-400 border-blue-500/30";
  };

  const levelIcon = (level: string) => {
    if (level === "CRITICAL") return <AlertTriangle className="w-4 h-4 text-red-400" />;
    if (level === "WARN") return <Bell className="w-4 h-4 text-amber-400" />;
    return <Bell className="w-4 h-4 text-blue-400" />;
  };

  // ── Render ─────────────────────────────────
  return (
    <div className="flex-1 flex flex-col min-h-0">
      {/* Toolbar */}
      <div className="flex items-center gap-2 mb-3 shrink-0 flex-wrap">
        <div className="flex items-center gap-1.5">
          <input
            className={`w-20 px-2 py-1.5 rounded border text-xs outline-none ${styles.inputBg} ${styles.inputBorder}`}
            value={testValue}
            onChange={e => setTestValue(e.target.value)}
            aria-label={t("platform.alert.field.testValue")}
            placeholder={t("platform.alert.field.testValue")}
          />
          <button type="button" onClick={() => triggerAlert()}
            className={`flex items-center gap-1 px-2.5 py-1.5 rounded text-xs font-medium transition-all bg-amber-500/20 border border-amber-500/30 text-amber-400 hover:bg-amber-500/30`}>
            <Zap className="w-3.5 h-3.5" />
            {t("platform.alert.triggerAll")}
          </button>
          <button type="button" onClick={() => setShowNewRule(true)}
            className={`flex items-center gap-1 px-2.5 py-1.5 rounded text-xs font-medium border transition-all ${styles.cardBorder} ${styles.cardBg}`}>
            <Plus className="w-3.5 h-3.5" />
            {t("platform.alert.newRule")}
          </button>
        </div>
        <div className="flex-1" />
        <div className={`flex items-center gap-1.5 px-2 py-1 rounded text-xs border shrink-0 ${styles.warningBg} ${styles.warningText} ${styles.cardBorder}`}>
          <AlertTriangle className="w-3 h-3" />
          <span>{t("platform.alert.pushNotWired")}</span>
        </div>
        <button type="button" onClick={fetchAll} disabled={loading}
          className={`p-1.5 rounded border transition-all ${styles.cardBorder}`}>
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
        </button>
      </div>

      {error && (
        <div className="mb-3 px-3 py-2 rounded text-xs bg-red-500/10 border border-red-500/30 text-red-400 shrink-0">{error}</div>
      )}

      {notWired && (
        <div className={`mb-3 px-3 py-2 rounded text-xs border shrink-0 ${styles.warningBg} ${styles.warningText} ${styles.cardBorder}`}>
          {t("platform.alert.restNotWired")}
        </div>
      )}

      {/* New Rule Modal */}
      {showNewRule && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className={`w-full max-w-sm mx-4 rounded-lg p-5 ${styles.cardBg} border ${styles.cardBorder}`}>
            <h3 className="text-sm font-semibold mb-3">{t("platform.alert.newAlertRule")}</h3>
            <input className={`w-full px-3 py-1.5 rounded border text-xs mb-2 ${styles.inputBg} ${styles.inputBorder}`}
              aria-label={t("platform.alert.field.name")}
              placeholder={t("platform.alert.field.name")} value={newRule.name} onChange={e => setNewRule({ ...newRule, name: e.target.value })} />
            <div className="flex gap-2 mb-2">
              <input className={`flex-1 px-3 py-1.5 rounded border text-xs ${styles.inputBg} ${styles.inputBorder}`}
                aria-label={t("platform.alert.field.metric")}
                placeholder={t("platform.alert.field.metric")} value={newRule.metric} onChange={e => setNewRule({ ...newRule, metric: e.target.value })} />
              <select className={`w-20 px-2 py-1.5 rounded border text-xs ${styles.inputBg} ${styles.inputBorder}`}
                aria-label={t("platform.alert.field.operator")}
                value={newRule.operator} onChange={e => setNewRule({ ...newRule, operator: e.target.value })}>
                <option value="<">&lt;</option>
                <option value=">">&gt;</option>
                <option value="<=">≤</option>
                <option value=">=">≥</option>
              </select>
              <input className={`w-20 px-3 py-1.5 rounded border text-xs ${styles.inputBg} ${styles.inputBorder}`}
                aria-label={t("platform.alert.field.threshold")}
                placeholder={t("platform.alert.field.threshold")} value={newRule.threshold} onChange={e => setNewRule({ ...newRule, threshold: e.target.value })} />
            </div>
            <select className={`w-full px-2 py-1.5 rounded border text-xs mb-3 ${styles.inputBg} ${styles.inputBorder}`}
              aria-label={t("platform.alert.field.level")}
              value={newRule.level} onChange={e => setNewRule({ ...newRule, level: e.target.value })}>
              <option value="INFO">INFO</option>
              <option value="WARN">WARN</option>
              <option value="CRITICAL">CRITICAL</option>
            </select>
            <div className="flex gap-2">
              <button type="button" onClick={createRule}
                className={`flex-1 px-4 py-1.5 rounded text-xs font-medium ${styles.accentBg} ${styles.accentHover} text-white`}>
                {t("common.save")}
              </button>
              <button type="button" onClick={() => setShowNewRule(false)}
                className={`flex-1 px-4 py-1.5 rounded border text-xs ${styles.cardBorder}`}>
                {t("common.cancel")}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Main grid: Rules + History */}
      <div className="flex-1 grid grid-cols-1 lg:grid-cols-2 gap-3 min-h-0 overflow-auto">
        {/* Rules */}
        <div className={`border rounded-lg p-3 ${styles.cardBorder} ${styles.cardBg}`}>
          <h4 className="text-xs font-semibold mb-2 opacity-70 flex items-center gap-1">
            <Bell className="w-3.5 h-3.5" />
            {t("platform.alert.rules")} ({rules.length})
          </h4>
          <div className="space-y-1.5">
            {rules.map(r => (
              <div key={r.id} className={`flex items-center gap-2 p-1.5 rounded text-xs border ${levelColor(r.level)}`}>
                {levelIcon(r.level)}
                <div className="flex-1 min-w-0">
                  <div className="font-medium truncate">{r.name}</div>
                  <div className="text-[10px] opacity-60">{r.metric} {r.operator} {r.threshold}</div>
                </div>
                <button type="button" onClick={() => triggerAlert(r.id)}
                  className="px-1.5 py-0.5 rounded text-[10px] bg-amber-500/10 border border-amber-500/20 text-amber-400 hover:bg-amber-500/20">
                  {t("platform.alert.fire")}
                </button>
                <button type="button" onClick={() => setPendingDelete({ id: r.id, name: r.name })}
                  className="p-0.5 rounded hover:bg-red-500/10 text-red-400 opacity-50 hover:opacity-100">
                  <Trash2 className="w-3 h-3" />
                </button>
              </div>
            ))}
          </div>
        </div>

        {/* History */}
        <div className={`border rounded-lg p-3 ${styles.cardBorder} ${styles.cardBg}`}>
          <h4 className="text-xs font-semibold mb-2 opacity-70 flex items-center gap-1">
            <History className="w-3.5 h-3.5" />
            {t("platform.alert.history")} ({history.length})
          </h4>
          <div className="space-y-1">
            {history.map(h => (
              <div key={h.id} className={`flex items-center gap-2 p-1.5 rounded text-xs border ${levelColor(h.level)}`}>
                {levelIcon(h.level)}
                <div className="flex-1 min-w-0">
                  <div className="truncate text-[11px]">{h.message}</div>
                  <div className="text-[10px] opacity-50">{h.created_at?.slice(0, 16)}</div>
                </div>
                {!h.acknowledged ? (
                  <button type="button" onClick={() => ackAlert(h.id)}
                    className="px-1.5 py-0.5 rounded text-[10px] bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 hover:bg-emerald-500/20">
                    {t("platform.alert.ack")}
                  </button>
                ) : (
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                )}
              </div>
            ))}
            {history.length === 0 && (
              <p className="text-xs opacity-40 text-center py-4">{t("platform.alert.noHistory")}</p>
            )}
          </div>
        </div>
      </div>

      <ConfirmDialog
        visible={pendingDelete !== null}
        title={t("platform.alert.confirmDeleteRule")}
        message={pendingDelete
          ? t("platform.alert.confirmDeleteRuleDesc").replace("{name}", pendingDelete.name)
          : ""}
        confirmText={t("common.delete")}
        cancelText={t("common.cancel")}
        variant="danger"
        onConfirm={() => { if (pendingDelete) deleteRule(pendingDelete.id); }}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}
