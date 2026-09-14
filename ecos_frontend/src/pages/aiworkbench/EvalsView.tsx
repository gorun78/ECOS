/**
 * Evals & Monitor view — 评估 (Evals radar) + Agent 指标监控 (Trend + Errors)。
 *
 * 衔接 DashboardView 的 Eval 区（已有）+ AgentMetrics 真接后端 (本次新增)。
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useEffect, useState } from 'react';
import { AIPAgent, AIPModel } from '../../types/aiworkbench';
import { fetchAgentMetrics, fetchAgentErrors } from './api';
import {
  BarChart3, TrendingUp, AlertTriangle, Loader2
} from 'lucide-react';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';

interface EvalsViewProps {
  agents: AIPAgent[];
  models: AIPModel[];
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

interface AgentMetrics {
  agentId: string;
  totalCalls: number;
  successRate: number;
  avgLatencyMs: number;
  p99LatencyMs: number;
  trend24h: number[];
}

interface AgentError {
  id: string;
  timestamp: string;
  agentId: string;
  agentName: string;
  errorMessage: string;
  traceId: string;
  status: string;
}

export default function EvalsView({ agents, models, showToast }: EvalsViewProps): React.JSX.Element {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [selectedAgent, setSelectedAgent] = useState<string>(agents[0]?.id || '');
  const [metrics, setMetrics] = useState<AgentMetrics | null>(null);
  const [errors, setErrors] = useState<AgentError[]>([]);
  const [loading, setLoading] = useState(false);

  // 切换 Agent 时刷新数据
  const activeAgent = agents.find(a => a.id === selectedAgent);

  useEffect(() => {
    if (!selectedAgent) return;
    let cancelled = false;
    setLoading(true);
    const load = async () => {
      try {
        const [m, e] = await Promise.all([
          fetchAgentMetrics(selectedAgent),
          fetchAgentErrors(selectedAgent),
        ]);
        if (cancelled) return;
        setMetrics({
          agentId: selectedAgent,
          totalCalls: m.totalCalls,
          successRate: m.successRate,
          avgLatencyMs: m.avgLatencyMs,
          p99LatencyMs: m.p99LatencyMs,
          trend24h: m.trend24h || [],
        });
        setErrors(e);
      } catch (err: any) {
        if (!cancelled) showToast?.('error', err?.message || 'Evals load failed');
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    load();
    return () => { cancelled = true; };
  }, [selectedAgent, agents, models]);

  const trend = metrics?.trend24h || [];
  const maxTrend = Math.max(...trend, 1);

  return (
    <div className={`h-full w-full flex flex-col overflow-y-auto p-6 ${styles.appBg} ${styles.appText}`}>
      {/* Agent 选择器 */}
      <div className="mb-4 flex items-center gap-3">
        <label className={`text-xs font-semibold ${styles.muted}`}>Agent:</label>
        <select
          value={selectedAgent}
          onChange={(e) => setSelectedAgent(e.target.value)}
          className={`px-2 py-1.5 rounded-md border text-xs ${styles.inputBg} ${styles.inputText} ${styles.inputBorder}`}
          disabled={agents.length === 0}
        >
          {agents.length === 0 && <option value="">— 未加载到 Agent —</option>}
          {agents.map((a) => (
            <option key={a.id} value={a.id}>{a.name}</option>
          ))}
        </select>
        {activeAgent && (
          <span className={`text-[10px] font-mono px-1.5 py-0.5 rounded ${styles.badgeBg} ${styles.muted}`}>
            {activeAgent.modelId}
          </span>
        )}
      </div>

      {loading && (
        <div className="flex items-center gap-2 mb-4 text-xs opacity-60">
          <Loader2 size={14} className="animate-spin" /> 加载中...
        </div>
      )}

      {/* KPI 卡片 */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-4">
        {[
          { label: '24h 调用', value: metrics?.totalCalls?.toLocaleString?.() ?? '—', icon: BarChart3 },
          { label: '成功率', value: metrics ? `${(metrics.successRate * 100).toFixed(1)}%` : '—', icon: TrendingUp },
          { label: 'P50 延迟', value: metrics ? `${metrics.avgLatencyMs.toFixed(0)}ms` : '—', icon: BarChart3 },
          { label: 'P99 延迟', value: metrics ? `${metrics.p99LatencyMs.toFixed(0)}ms` : '—', icon: AlertTriangle },
        ].map((k) => {
          const Icon = k.icon;
          return (
            <div key={k.label} className={`p-3 rounded-lg border ${styles.cardBorder} ${styles.cardBg}`}>
              <div className="flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wide opacity-60">
                <Icon size={12} />
                {k.label}
              </div>
              <div className={`text-lg font-extrabold ${styles.cardText} mt-1`}>{k.value}</div>
            </div>
          );
        })}
      </div>

      {/* 24h 趋势条形 */}
      <div className={`mb-4 p-3 rounded-lg border ${styles.cardBorder} ${styles.cardBg}`}>
        <h3 className={`text-xs font-semibold mb-2 ${styles.cardText}`}>24h 调用趋势</h3>
        {trend.length > 0 ? (
          <div className="flex items-end gap-0.5 h-20">
            {trend.map((v, i) => (
              <div
                key={i}
                className={`flex-1 ${styles.accentBg} opacity-70 rounded-sm`}
                style={{ height: `${Math.max(4, (v / maxTrend) * 100)}%` }}
                title={`hour ${i}: ${v}`}
              />
            ))}
          </div>
        ) : (
          <div className={`text-xs font-mono px-2 py-3 rounded opacity-50 ${styles.cardTextMuted}`}>
            暂无趋势数据
          </div>
        )}
      </div>

      {/* 错误列表 */}
      <div className={`p-3 rounded-lg border ${styles.cardBorder} ${styles.cardBg}`}>
        <h3 className={`text-xs font-semibold mb-2 ${styles.cardText}`}>最近错误 ({errors.length})</h3>
        {errors.length === 0 ? (
          <div className={`text-xs font-mono px-2 py-3 opacity-50 ${styles.cardTextMuted}`}>
            暂无错误
          </div>
        ) : (
          <div className="max-h-64 overflow-y-auto space-y-1.5">
            {errors.slice(0, 30).map((e) => (
              <div key={e.id} className={`text-[11px] px-2 py-1.5 rounded border ${styles.cardBorder} ${styles.cardBg} truncate`}>
                <span className="font-mono opacity-60">{e.timestamp?.slice(11, 19)}</span>{' '}
                <span className={styles.cardText}>{e.agentName}</span>{' '}
                <span className="opacity-70">— {e.errorMessage}</span>
                {e.traceId && (
                  <span className="ml-2 font-mono opacity-40 text-[9px]">#{e.traceId.slice(0, 8)}</span>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Evals 雷达（复用 DashboardView 的 RadarChart，简化为占位说明）*/}
      <div className={`mt-4 p-3 rounded-lg border dashed ${styles.cardBorder} ${styles.cardBg} text-xs`}>
        <div className="opacity-70">Evals 5 维评估雷达图见 Overview 仪表盘 Tab (DashboardView → Evaluation 面板)</div>
      </div>
    </div>
  );
}
