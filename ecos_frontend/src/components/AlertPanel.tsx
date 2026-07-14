/**
 * P2-6 AlertPanel — 实时告警面板：规则管理 + 触发 + 历史 + WebSocket 实时推送
 * Connected to: /api/alerts/*, WebSocket /ws → /topic/alerts
 */
import React, { useState, useEffect, useCallback, useRef } from "react";
import { Bell, AlertTriangle, CheckCircle2, XCircle, RefreshCw, Zap, History, Trash2, Plus } from "lucide-react";
import { useLanguage } from "../pages/../components/LanguageContext";
import { useTheme } from "../pages/../components/ThemeContext";

const API = "/api/alerts";

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
  const { locale } = useLanguage();
  const { styles } = useTheme();

  const [rules, setRules] = useState<AlertRule[]>([]);
  const [history, setHistory] = useState<AlertRecord[]>([]);
  const [liveAlerts, setLiveAlerts] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [testValue, setTestValue] = useState("50");
  const [showNewRule, setShowNewRule] = useState(false);
  const [newRule, setNewRule] = useState({ name: "", metric: "", operator: "<", threshold: "80", level: "WARN" });
  const stompRef = useRef<any>(null);

  // ── Fetch rules + history ────────────────
  const fetchAll = useCallback(async () => {
    setLoading(true);
    try {
      const [rRes, hRes] = await Promise.all([
        fetch(`${API}/rules`),
        fetch(`${API}/history?limit=20`),
      ]);
      const rData = await rRes.json();
      const hData = await hRes.json();
      setRules(rData?.data || []);
      setHistory(hData?.data || []);
    } catch (e: any) {
      setError(e?.message || "Failed");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchAll(); }, [fetchAll]);

  // ── WebSocket connection ──────────────────
  useEffect(() => {
    try {
      // Use SockJS + STOMP pattern via simple WebSocket for /topic/alerts
      const connect = () => {
        const wsUrl = `ws://${window.location.hostname}:8080/ws`;
        const socket = new WebSocket(wsUrl);

        socket.onopen = () => {
          // Subscribe to alerts topic via STOMP-like frame
          socket.send("SUBSCRIBE\nid:sub-0\ndestination:/topic/alerts\n\n\0");
        };

        socket.onmessage = (event) => {
          try {
            const data = JSON.parse(event.data);
            // STOMP MESSAGE frame
            if (data && data.ruleName) {
              setLiveAlerts(prev => [data, ...prev].slice(0, 10));
            }
          } catch {
            // Try parsing STOMP frame
            const body = event.data.split("\n\n").pop()?.replace("\0", "") || "";
            try {
              const alert = JSON.parse(body);
              if (alert.ruleName) {
                setLiveAlerts(prev => [alert, ...prev].slice(0, 10));
              }
            } catch {}
          }
        };

        socket.onclose = () => {
          // Reconnect after 5s
          setTimeout(connect, 5000);
        };

        stompRef.current = socket;
      };

      connect();
    } catch {}

    return () => {
      if (stompRef.current) stompRef.current.close();
    };
  }, []);

  // ── Actions ───────────────────────────────
  const triggerAlert = async (ruleId?: number) => {
    try {
      const body: any = { metricValue: testValue };
      if (ruleId) body.ruleId = ruleId;
      await fetch(`${API}/test`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      });
      fetchAll();
    } catch (e: any) {
      setError(e?.message || "Trigger failed");
    }
  };

  const ackAlert = async (id: number) => {
    try {
      await fetch(`${API}/${id}/ack`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ acknowledged_by: "admin" }),
      });
      fetchAll();
    } catch {}
  };

  const createRule = async () => {
    try {
      await fetch(`${API}/rules`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(newRule),
      });
      setShowNewRule(false);
      setNewRule({ name: "", metric: "", operator: "<", threshold: "80", level: "WARN" });
      fetchAll();
    } catch {}
  };

  const deleteRule = async (id: number) => {
    try {
      await fetch(`${API}/rules/${id}`, { method: "DELETE" });
      fetchAll();
    } catch {}
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
            placeholder="Value"
          />
          <button onClick={() => triggerAlert()}
            className={`flex items-center gap-1 px-2.5 py-1.5 rounded text-xs font-medium transition-all bg-amber-500/20 border border-amber-500/30 text-amber-400 hover:bg-amber-500/30`}>
            <Zap className="w-3.5 h-3.5" />
            {locale === "zh" ? "全部触发" : "Trigger All"}
          </button>
          <button onClick={() => setShowNewRule(true)}
            className={`flex items-center gap-1 px-2.5 py-1.5 rounded text-xs font-medium border transition-all ${styles.cardBorder} ${styles.cardBg}`}>
            <Plus className="w-3.5 h-3.5" />
            {locale === "zh" ? "新建规则" : "New Rule"}
          </button>
        </div>
        <div className="flex-1" />
        <button onClick={fetchAll} disabled={loading}
          className={`p-1.5 rounded border transition-all ${styles.cardBorder}`}>
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? "animate-spin" : ""}`} />
        </button>
      </div>

      {error && (
        <div className="mb-3 px-3 py-2 rounded text-xs bg-red-500/10 border border-red-500/30 text-red-400 shrink-0">{error}</div>
      )}

      {/* New Rule Modal */}
      {showNewRule && (
        <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
          <div className={`w-full max-w-sm mx-4 rounded-lg p-5 ${styles.cardBg} border ${styles.cardBorder}`}>
            <h3 className="text-sm font-semibold mb-3">{locale === "zh" ? "新建告警规则" : "New Rule"}</h3>
            <input className={`w-full px-3 py-1.5 rounded border text-xs mb-2 ${styles.inputBg} ${styles.inputBorder}`}
              placeholder="Name" value={newRule.name} onChange={e => setNewRule({ ...newRule, name: e.target.value })} />
            <div className="flex gap-2 mb-2">
              <input className={`flex-1 px-3 py-1.5 rounded border text-xs ${styles.inputBg} ${styles.inputBorder}`}
                placeholder="Metric" value={newRule.metric} onChange={e => setNewRule({ ...newRule, metric: e.target.value })} />
              <select className={`w-20 px-2 py-1.5 rounded border text-xs ${styles.inputBg} ${styles.inputBorder}`}
                value={newRule.operator} onChange={e => setNewRule({ ...newRule, operator: e.target.value })}>
                <option value="<">&lt;</option>
                <option value=">">&gt;</option>
                <option value="<=">≤</option>
                <option value=">=">≥</option>
              </select>
              <input className={`w-20 px-3 py-1.5 rounded border text-xs ${styles.inputBg} ${styles.inputBorder}`}
                placeholder="Threshold" value={newRule.threshold} onChange={e => setNewRule({ ...newRule, threshold: e.target.value })} />
            </div>
            <select className={`w-full px-2 py-1.5 rounded border text-xs mb-3 ${styles.inputBg} ${styles.inputBorder}`}
              value={newRule.level} onChange={e => setNewRule({ ...newRule, level: e.target.value })}>
              <option value="INFO">INFO</option>
              <option value="WARN">WARN</option>
              <option value="CRITICAL">CRITICAL</option>
            </select>
            <div className="flex gap-2">
              <button onClick={createRule}
                className={`flex-1 px-4 py-1.5 rounded text-xs font-medium ${styles.accentBg} ${styles.accentHover} text-white`}>
                {locale === "zh" ? "保存" : "Save"}
              </button>
              <button onClick={() => setShowNewRule(false)}
                className={`flex-1 px-4 py-1.5 rounded border text-xs ${styles.cardBorder}`}>
                {locale === "zh" ? "取消" : "Cancel"}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Live Alerts (WebSocket) */}
      {liveAlerts.length > 0 && (
        <div className="mb-3 shrink-0">
          <h4 className="text-xs font-semibold mb-1.5 opacity-70 flex items-center gap-1">
            <Zap className="w-3 h-3 text-amber-400" />
            {locale === "zh" ? "实时告警" : "Live Alerts"} ({liveAlerts.length})
          </h4>
          <div className="space-y-1">
            {liveAlerts.slice(0, 3).map((a, i) => (
              <div key={i} className={`px-3 py-1.5 rounded text-xs border flex items-center gap-2 animate-pulse ${levelColor(a.level)}`}>
                {levelIcon(a.level)}
                <span className="flex-1 truncate">{a.message}</span>
                <span className="text-[10px] opacity-60">{new Date(a.timestamp).toLocaleTimeString()}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Main grid: Rules + History */}
      <div className="flex-1 grid grid-cols-1 lg:grid-cols-2 gap-3 min-h-0 overflow-auto">
        {/* Rules */}
        <div className={`border rounded-lg p-3 ${styles.cardBorder} ${styles.cardBg}`}>
          <h4 className="text-xs font-semibold mb-2 opacity-70 flex items-center gap-1">
            <Bell className="w-3.5 h-3.5" />
            {locale === "zh" ? "告警规则" : "Rules"} ({rules.length})
          </h4>
          <div className="space-y-1.5">
            {rules.map(r => (
              <div key={r.id} className={`flex items-center gap-2 p-1.5 rounded text-xs border ${levelColor(r.level)}`}>
                {levelIcon(r.level)}
                <div className="flex-1 min-w-0">
                  <div className="font-medium truncate">{r.name}</div>
                  <div className="text-[10px] opacity-60">{r.metric} {r.operator} {r.threshold}</div>
                </div>
                <button onClick={() => triggerAlert(r.id)}
                  className="px-1.5 py-0.5 rounded text-[10px] bg-amber-500/10 border border-amber-500/20 text-amber-400 hover:bg-amber-500/20">
                  {locale === "zh" ? "触发" : "Fire"}
                </button>
                <button onClick={() => deleteRule(r.id)}
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
            {locale === "zh" ? "告警历史" : "History"} ({history.length})
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
                  <button onClick={() => ackAlert(h.id)}
                    className="px-1.5 py-0.5 rounded text-[10px] bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 hover:bg-emerald-500/20">
                    {locale === "zh" ? "确认" : "Ack"}
                  </button>
                ) : (
                  <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                )}
              </div>
            ))}
            {history.length === 0 && (
              <p className="text-xs opacity-40 text-center py-4">{locale === "zh" ? "暂无历史" : "No history"}</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
