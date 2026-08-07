/**
 * AgentMonitor — T9-3 Agent监控面板
 * Metrics cards, Canvas trend chart (24h/7d/30d), and error list.
 * @license Apache-2.0
 */

import React, { useState, useEffect, useRef, useCallback } from 'react';
import * as Icons from 'lucide-react';
import { useTheme } from '../../ThemeContext';
import type { AIPAgent, AIPAgentMetrics, AIPAgentError } from '../../../types/aiworkbench';
import { fetchManagedAgents, fetchAgentMetrics, fetchAgentErrors } from '../../../pages/aiworkbench/api';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

interface AgentMonitorProps {
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

const CHART_COLORS = {
  line: '#6366f1',
  fill: 'rgba(99, 102, 241, 0.08)',
  grid: 'rgba(148, 163, 184, 0.15)',
  text: '#94a3b8',
};

type TrendPeriod = '24h' | '7d' | '30d';

function drawTrendChart(
  canvas: HTMLCanvasElement,
  data: number[],
  periodLabel: string,
  isDark: boolean
) {
  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  const dpr = window.devicePixelRatio || 1;
  const rect = canvas.getBoundingClientRect();
  const w = rect.width;
  const h = rect.height;
  canvas.width = w * dpr;
  canvas.height = h * dpr;
  canvas.style.width = `${w}px`;
  canvas.style.height = `${h}px`;
  ctx.scale(dpr, dpr);

  ctx.clearRect(0, 0, w, h);

  const pad = { top: 12, right: 16, bottom: 24, left: 40 };
  const chartW = w - pad.left - pad.right;
  const chartH = h - pad.top - pad.bottom;

  if (data.length < 2) {
    ctx.fillStyle = isDark ? '#94a3b8' : '#64748b';
    ctx.font = '11px sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText('数据不足，无法绘制趋势图', w / 2, h / 2);
    return;
  }

  const minVal = Math.min(...data);
  const maxVal = Math.max(...data);
  const range = maxVal - minVal || 1;

  // Grid lines
  ctx.strokeStyle = isDark ? 'rgba(148,163,184,0.12)' : 'rgba(148,163,184,0.15)';
  ctx.lineWidth = 0.5;
  for (let i = 0; i <= 4; i++) {
    const y = pad.top + (chartH / 4) * i;
    ctx.beginPath();
    ctx.moveTo(pad.left, y);
    ctx.lineTo(w - pad.right, y);
    ctx.stroke();

    // Y-axis labels
    const val = maxVal - (range / 4) * i;
    ctx.fillStyle = isDark ? '#94a3b8' : '#64748b';
    ctx.font = '9px sans-serif';
    ctx.textAlign = 'right';
    ctx.fillText(Math.round(val).toString(), pad.left - 6, y + 3);
  }

  // X-axis labels
  ctx.fillStyle = isDark ? '#94a3b8' : '#64748b';
  ctx.font = '9px sans-serif';
  ctx.textAlign = 'center';
  const xStep = chartW / (data.length - 1);
  for (let i = 0; i < data.length; i++) {
    const x = pad.left + xStep * i;
    if (i === 0) ctx.fillText(periodLabel === '24h' ? '0h' : periodLabel === '7d' ? 'Day1' : 'Day1', x, h - 4);
    else if (i === data.length - 1) ctx.fillText(periodLabel === '24h' ? 'Now' : periodLabel === '7d' ? 'Day7' : 'Day30', x, h - 4);
  }

  // Area fill
  ctx.beginPath();
  ctx.moveTo(pad.left, pad.top + chartH);
  for (let i = 0; i < data.length; i++) {
    const x = pad.left + xStep * i;
    const y = pad.top + chartH - ((data[i] - minVal) / range) * chartH;
    ctx.lineTo(x, y);
  }
  ctx.lineTo(pad.left + xStep * (data.length - 1), pad.top + chartH);
  ctx.closePath();
  ctx.fillStyle = CHART_COLORS.fill;
  ctx.fill();

  // Line
  ctx.beginPath();
  ctx.strokeStyle = CHART_COLORS.line;
  ctx.lineWidth = 2;
  for (let i = 0; i < data.length; i++) {
    const x = pad.left + xStep * i;
    const y = pad.top + chartH - ((data[i] - minVal) / range) * chartH;
    if (i === 0) ctx.moveTo(x, y);
    else ctx.lineTo(x, y);
  }
  ctx.stroke();

  // Data points
  for (let i = 0; i < data.length; i++) {
    const x = pad.left + xStep * i;
    const y = pad.top + chartH - ((data[i] - minVal) / range) * chartH;
    ctx.beginPath();
    ctx.arc(x, y, 3, 0, Math.PI * 2);
    ctx.fillStyle = CHART_COLORS.line;
    ctx.fill();
    ctx.strokeStyle = isDark ? '#1e293b' : '#fff';
    ctx.lineWidth = 1.5;
    ctx.stroke();
  }
}

export default function AgentMonitor({ showToast }: AgentMonitorProps) {
  const { styles } = useTheme();
  const [agents, setAgents] = useState<AIPAgent[]>([]);
  const [selectedAgentId, setSelectedAgentId] = useState<string>('');
  const [metrics, setMetrics] = useState<AIPAgentMetrics | null>(null);
  const [errors, setErrors] = useState<AIPAgentError[]>([]);
  const [loading, setLoading] = useState(false);
  const [period, setPeriod] = useState<TrendPeriod>('24h');
  const [expandedErrors, setExpandedErrors] = useState<Set<string>>(new Set());

  const canvasRef = useRef<HTMLCanvasElement>(null);

  // Load agent list
  useEffect(() => {
    fetchManagedAgents().then(data => {
      setAgents(data);
      if (data.length > 0 && !selectedAgentId) {
        setSelectedAgentId(data[0].id);
      }
    }).catch(() => {});
  }, []);

  // Load metrics + errors when agent changes
  useEffect(() => {
    if (!selectedAgentId) return;
    setLoading(true);
    setMetrics(null);
    setErrors([]);

    Promise.all([
      fetchAgentMetrics(selectedAgentId).catch((): null => null),
      fetchAgentErrors(selectedAgentId).catch((): AIPAgentError[] => []),
    ]).then(([m, errs]) => {
      setMetrics(m);
      setErrors(errs as AIPAgentError[]);
      setLoading(false);
    });
  }, [selectedAgentId]);

  // Draw chart when metrics or period changes
  useEffect(() => {
    if (!canvasRef.current || !metrics) return;
    const trendData = period === '24h' ? metrics.trend24h
      : period === '7d' ? metrics.trend7d
      : metrics.trend30d;
    if (trendData.length > 0) {
      drawTrendChart(canvasRef.current, trendData, period, false);
    } else {
      // Draw with empty state
      const ctx = canvasRef.current.getContext('2d');
      if (ctx) {
        const dpr = window.devicePixelRatio || 1;
        const rect = canvasRef.current.getBoundingClientRect();
        canvasRef.current.width = rect.width * dpr;
        canvasRef.current.height = rect.height * dpr;
        ctx.scale(dpr, dpr);
        ctx.clearRect(0, 0, rect.width, rect.height);
        ctx.fillStyle = '#94a3b8';
        ctx.font = '11px sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText('暂无趋势数据', rect.width / 2, rect.height / 2);
      }
    }
  }, [metrics, period]);

  // Handle resize
  useEffect(() => {
    const handleResize = () => {
      if (!canvasRef.current || !metrics) return;
      const trendData = period === '24h' ? metrics.trend24h
        : period === '7d' ? metrics.trend7d
        : metrics.trend30d;
      if (trendData.length > 0) {
        drawTrendChart(canvasRef.current, trendData, period, false);
      }
    };
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [metrics, period]);

  const toggleErrorExpand = (id: string) => {
    setExpandedErrors(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  const formatMs = (ms: number) => {
    if (ms >= 1000) return `${(ms / 1000).toFixed(1)}s`;
    return `${Math.round(ms)}ms`;
  };

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* Agent Selector + Header */}
      <div className={`p-4 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center gap-3`}>
        <label className={`text-[11px] font-bold ${styles.cardTextMuted} shrink-0`}>选择Agent：</label>
        <select
          value={selectedAgentId}
          onChange={e => setSelectedAgentId(e.target.value)}
          className={`flex-1 px-3 py-1.5 border ${styles.inputBorder} rounded-lg text-xs ${styles.cardBg} ${styles.cardText}`}
        >
          {agents.length === 0 && <option value="">暂无可监控的Agent</option>}
          {agents.map(a => (
            <option key={a.id} value={a.id}>
              {a.name} ({a.status === 'active' ? '在线' : '下线'})
            </option>
          ))}
        </select>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {!selectedAgentId ? (
          <div className="flex flex-col items-center justify-center py-16 gap-3">
            <Icon name="Activity" size={32} className={styles.cardTextMuted} />
            <p className={`text-xs ${styles.cardTextMuted}`}>请先选择一个Agent进行监控</p>
          </div>
        ) : loading ? (
          <div className="space-y-4 animate-pulse">
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              {[1, 2, 3, 4].map(i => (
                <div key={i} className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4`}>
                  <div className={`h-3 w-16 ${styles.inputBg} rounded mb-2`} />
                  <div className={`h-6 w-20 ${styles.inputBg} rounded`} />
                </div>
              ))}
            </div>
            <div className={`h-48 ${styles.cardBg} border ${styles.cardBorder} rounded-xl`} />
          </div>
        ) : (
          <>
            {/* Metrics Cards */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4`}>
                <p className={`text-[10px] ${styles.cardTextMuted} font-semibold uppercase tracking-wider`}>总调用量</p>
                <p className={`text-xl font-black ${styles.cardText} mt-1`}>
                  {metrics ? metrics.totalCalls.toLocaleString() : '—'}
                </p>
                <div className="flex items-center gap-1 mt-1">
                  <Icon name="BarChart3" size={11} className={styles.accentText} />
                  <span className={`text-[10px] ${styles.cardTextMuted}`}>累计</span>
                </div>
              </div>

              <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4`}>
                <p className={`text-[10px] ${styles.cardTextMuted} font-semibold uppercase tracking-wider`}>成功率</p>
                <p className={`text-xl font-black mt-1 ${metrics ? (metrics.successRate >= 95 ? 'text-green-500' : metrics.successRate >= 80 ? 'text-amber-500' : 'text-red-500') : styles.cardText}`}>
                  {metrics ? `${metrics.successRate.toFixed(1)}%` : '—'}
                </p>
                <div className="flex items-center gap-1 mt-1">
                  <Icon name="CheckCircle2" size={11} className="text-green-500" />
                  <span className={`text-[10px] ${styles.cardTextMuted}`}>最近1小时</span>
                </div>
              </div>

              <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4`}>
                <p className={`text-[10px] ${styles.cardTextMuted} font-semibold uppercase tracking-wider`}>平均延迟</p>
                <p className={`text-xl font-black ${styles.cardText} mt-1`}>
                  {metrics ? formatMs(metrics.avgLatencyMs) : '—'}
                </p>
                <div className="flex items-center gap-1 mt-1">
                  <Icon name="Clock" size={11} className={styles.accentText} />
                  <span className={`text-[10px] ${styles.cardTextMuted}`}>Avg</span>
                </div>
              </div>

              <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4`}>
                <p className={`text-[10px] ${styles.cardTextMuted} font-semibold uppercase tracking-wider`}>P99延迟</p>
                <p className={`text-xl font-black ${styles.cardText} mt-1`}>
                  {metrics ? formatMs(metrics.p99LatencyMs) : '—'}
                </p>
                <div className="flex items-center gap-1 mt-1">
                  <Icon name="Gauge" size={11} className="text-amber-500" />
                  <span className={`text-[10px] ${styles.cardTextMuted}`}>P99</span>
                </div>
              </div>
            </div>

            {/* Trend Chart */}
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4`}>
              <div className="flex items-center justify-between mb-3">
                <h3 className={`text-xs font-extrabold ${styles.cardTextMuted} uppercase tracking-wider flex items-center gap-1.5`}>
                  <Icon name="TrendingUp" size={12} className={styles.accentText} />
                  调用趋势
                </h3>
                <div className="flex gap-1">
                  {(['24h', '7d', '30d'] as TrendPeriod[]).map(p => (
                    <button
                      key={p}
                      onClick={() => setPeriod(p)}
                      className={`px-2.5 py-1 rounded text-[10px] font-semibold transition-colors cursor-pointer ${
                        period === p
                          ? `${styles.accentBg} text-white`
                          : `${styles.cardTextMuted} hover:${styles.inputBg}`
                      }`}
                    >
                      {p}
                    </button>
                  ))}
                </div>
              </div>
              <div className="w-full h-52 relative">
                <canvas ref={canvasRef} className="w-full h-full" />
              </div>
            </div>

            {/* Error List */}
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4`}>
              <h3 className={`text-xs font-extrabold ${styles.cardTextMuted} uppercase tracking-wider flex items-center gap-1.5 mb-3`}>
                <Icon name="AlertTriangle" size={12} className="text-red-500" />
                最近错误 ({errors.length})
              </h3>
              {errors.length === 0 ? (
                <div className={`text-center py-8 ${styles.cardTextMuted}`}>
                  <Icon name="CheckCircle2" size={24} className="text-green-500 mx-auto mb-2" />
                  <p className="text-[11px]">暂无错误记录</p>
                </div>
              ) : (
                <div className="space-y-2 max-h-64 overflow-y-auto">
                  {errors.map(err => {
                    const isExpanded = expandedErrors.has(err.id);
                    return (
                      <div
                        key={err.id}
                        className={`border ${err.status === 'unresolved' ? 'border-red-200 bg-red-500/5' : err.status === 'investigating' ? 'border-amber-200 bg-amber-500/5' : 'border-green-200 bg-green-500/5'} rounded-lg p-3 cursor-pointer`}
                        onClick={() => toggleErrorExpand(err.id)}
                      >
                        <div className="flex items-center justify-between">
                          <div className="flex items-center gap-2 min-w-0">
                            <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${
                              err.status === 'unresolved' ? 'bg-red-500' :
                              err.status === 'investigating' ? 'bg-amber-500' : 'bg-green-500'
                            }`} />
                            <span className={`text-[11px] font-bold ${styles.cardText} truncate`}>
                              {err.errorMessage}
                            </span>
                          </div>
                          <div className="flex items-center gap-2 shrink-0 ml-2">
                            <span className={`text-[9px] ${styles.cardTextMuted} font-mono`}>
                              {err.timestamp?.slice(0, 16).replace('T', ' ')}
                            </span>
                            <Icon name={isExpanded ? 'ChevronUp' : 'ChevronDown'} size={11} className={styles.cardTextMuted} />
                          </div>
                        </div>
                        {isExpanded && (
                          <div className={`mt-2 pt-2 border-t ${styles.cardBorder} space-y-1 text-[10px]`}>
                            <div className="flex gap-2">
                              <span className={`${styles.cardTextMuted} font-semibold`}>Agent:</span>
                              <span className={styles.cardText}>{err.agentName || err.agentId}</span>
                            </div>
                            <div className="flex gap-2">
                              <span className={`${styles.cardTextMuted} font-semibold`}>Trace ID:</span>
                              <span className={`${styles.cardText} font-mono`}>{err.traceId || 'N/A'}</span>
                            </div>
                            <div className="flex gap-2">
                              <span className={`${styles.cardTextMuted} font-semibold`}>状态:</span>
                              <span className={`${
                                err.status === 'unresolved' ? 'text-red-500' :
                                err.status === 'investigating' ? 'text-amber-500' : 'text-green-500'
                              } font-semibold`}>
                                {err.status === 'unresolved' ? '未解决' : err.status === 'investigating' ? '调查中' : '已解决'}
                              </span>
                            </div>
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {/* Last Updated */}
            {metrics?.lastUpdated && (
              <p className={`text-center text-[9px] ${styles.cardTextMuted}`}>
                最后更新: {metrics.lastUpdated.slice(0, 19).replace('T', ' ')}
              </p>
            )}
          </>
        )}
      </div>
    </div>
  );
}
