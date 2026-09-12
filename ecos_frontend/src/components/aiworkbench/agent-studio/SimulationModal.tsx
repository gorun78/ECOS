/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import * as Icons from 'lucide-react';
import { useTheme } from '../../ThemeContext';
import { useLanguage } from '../../LanguageContext';

const Icon = ({ name, size, className }: { name: string; size?: number; className?: string }) => {
  const Comp = (Icons as any)[name] || (Icons as any).HelpCircle;
  return <Comp size={size} className={className} />;
};

export interface SimulationModalProps {
  sandboxMode: 'chat' | 'simulation';
  onModeChange: (mode: 'chat' | 'simulation') => void;
  simUserId: string;
  simDatasetId: string;
  simQuery: string;
  onSimUserIdChange: (v: string) => void;
  onSimDatasetIdChange: (v: string) => void;
  onSimQueryChange: (v: string) => void;
  isSimulating: boolean;
  simResult: any;
  expandedNodes: Record<string, boolean>;
  onToggleNode: (id: string) => void;
  onRunSimulation: () => void;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

/**
 * 推理干涉沙箱子组件：
 * - 顶部 chat/simulation Tab + SIMULATOR v1.2 徽标
 * - simulation 表单 (userId/datasetId/query) + 运行按钮
 * - simulation 结果渲染（真实响应渲染，失败时显示空状态卡）
 * chat Tab 由父组件 AgentStudioView 渲染（不在本组件内）
 */
export default function SimulationModal({
  sandboxMode,
  onModeChange,
  simUserId,
  simDatasetId,
  simQuery,
  onSimUserIdChange,
  onSimDatasetIdChange,
  onSimQueryChange,
  isSimulating,
  simResult,
  expandedNodes,
  onToggleNode,
  onRunSimulation,
  showToast,
}: SimulationModalProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  // 预设高危场景一键填充
  const applyPreset = (preset: { userId: string; datasetId: string; query: string }) => {
    onSimUserIdChange(preset.userId);
    onSimDatasetIdChange(preset.datasetId);
    onSimQueryChange(preset.query);
  };

  // simulation 模式下的判定是否为通过型（GRANTED / PASSED / ALLOWED）
  const isGranted = (v?: string) =>
    v === 'GRANTED' || v === 'PASSED' || v === 'ALLOWED' || v === 'COMPLETED';

  return (
    <div className="flex-1 flex flex-col min-h-0">
      {/* Header with Tab Selectors */}
      <div className={`p-2 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between shrink-0`}>
        <div className={`flex ${styles.inputBg} p-1 rounded-lg`}>
          <button
            onClick={() => onModeChange('chat')}
            className={`px-3 py-1.5 rounded-md font-bold text-[10px] transition-all cursor-pointer flex items-center gap-1 ${
              sandboxMode === 'chat'
                ? `${styles.cardBg} ${styles.cardText} shadow-xs`
                : styles.cardTextMuted
            }`}
          >
            <Icon name="MessageSquare" size={10} />
            <span>{t('aiworkbench.agent.sim.modeChat')}</span>
          </button>
          <button
            onClick={() => onModeChange('simulation')}
            className={`px-3 py-1.5 rounded-md font-bold text-[10px] transition-all cursor-pointer flex items-center gap-1 ${
              sandboxMode === 'simulation'
                ? `${styles.accentBg} text-white shadow-xs`
                : styles.cardTextMuted
            }`}
          >
            <Icon name="ShieldAlert" size={10} className="text-amber-400" />
            <span>{t('aiworkbench.agent.sim.modeSimulation')}</span>
          </button>
        </div>
        {sandboxMode === 'simulation' && (
          <span className={`px-2 py-0.5 ${styles.warningBg} ${styles.warningText} rounded text-[9px] font-black`}>
            {t('aiworkbench.agent.sim.versionBadge')}
          </span>
        )}
      </div>

      {sandboxMode === 'simulation' && (
        <div className={`flex-1 flex flex-col overflow-hidden ${styles.inputBg}`}>
          {/* Form Inputs Panel */}
          <div className={`p-4 ${styles.cardBg} border-b ${styles.cardBorder} space-y-3 shrink-0`}>
            <h3 className={`text-[11px] font-black ${styles.cardText} flex items-center gap-1`}>
              <Icon name="SlidersHorizontal" size={12} className={styles.accentText} />
              <span>{t('aiworkbench.agent.sim.contextInputs')}</span>
            </h3>

            <div className={`grid grid-cols-2 gap-2 text-[10px] ${styles.cardTextMuted}`}>
              <div className="space-y-1">
                <label className={`block ${styles.cardTextMuted} font-bold`}>
                  {t('aiworkbench.agent.sim.userId')}
                </label>
                <select
                  value={simUserId}
                  onChange={e => onSimUserIdChange(e.target.value)}
                  className={`w-full px-2 py-1.5 ${styles.inputBg} border ${styles.cardBorder} rounded-md font-medium ${styles.cardTextMuted}`}
                >
                  <option value="analyst_li">{t('aiworkbench.agent.sim.userAnalystLi')}</option>
                  <option value="hr_manager">{t('aiworkbench.agent.sim.userHrManager')}</option>
                  <option value="external_auditor">{t('aiworkbench.agent.sim.userExternalAuditor')}</option>
                  <option value="EU_DPO">{t('aiworkbench.agent.sim.userEuDpo')}</option>
                  <option value="admin_guorong">{t('aiworkbench.agent.sim.userAdminGuorong')}</option>
                </select>
              </div>

              <div className="space-y-1">
                <label className={`block ${styles.cardTextMuted} font-bold`}>
                  {t('aiworkbench.agent.sim.datasetId')}
                </label>
                <select
                  value={simDatasetId}
                  onChange={e => onSimDatasetIdChange(e.target.value)}
                  className={`w-full px-2 py-1.5 ${styles.inputBg} border ${styles.cardBorder} rounded-md font-medium ${styles.cardTextMuted}`}
                >
                  <option value="ds_pilots_biography">{t('aiworkbench.agent.sim.dsPilotsBiography')}</option>
                  <option value="ds_flights_clean">{t('aiworkbench.agent.sim.dsFlightsClean')}</option>
                  <option value="ds_ticket_sales">{t('aiworkbench.agent.sim.dsTicketSales')}</option>
                </select>
              </div>
            </div>

            {/* Natural Language Query Input */}
            <div className="space-y-1">
              <label className={`block text-[10px] ${styles.cardTextMuted} font-bold`}>
                {t('aiworkbench.agent.sim.query')}
              </label>
              <textarea
                value={simQuery}
                onChange={e => onSimQueryChange(e.target.value)}
                placeholder={t('aiworkbench.agent.sim.queryPlaceholder')}
                rows={2}
                className={`w-full px-2.5 py-1.5 ${styles.inputBg} border ${styles.cardBorder} rounded-lg text-xs font-medium resize-none focus:outline-hidden focus:${styles.cardBorder}`}
              />
            </div>

            {/* Suggestion tags */}
            <div className="flex flex-wrap gap-1">
              <span className={`text-[9px] ${styles.cardTextMuted} self-center font-bold mr-1`}>
                {t('aiworkbench.agent.sim.highRiskScenarios')}:
              </span>
              <button
                onClick={() => applyPreset({
                  userId: 'analyst_li',
                  datasetId: 'ds_pilots_biography',
                  query: t('aiworkbench.agent.sim.presetSsnQuery'),
                })}
                className={`px-2 py-0.5 ${styles.dangerBg} hover:opacity-80 border ${styles.dangerBorder} rounded text-[9px] ${styles.dangerText} font-bold`}
              >
                {t('aiworkbench.agent.sim.presetSsn')}
              </button>
              <button
                onClick={() => applyPreset({
                  userId: 'EU_DPO',
                  datasetId: 'ds_pilots_biography',
                  query: t('aiworkbench.agent.sim.presetDpoQuery'),
                })}
                className={`px-2 py-0.5 ${styles.successBg} hover:opacity-80 border ${styles.successBorder} rounded text-[9px] ${styles.successText} font-bold`}
              >
                {t('aiworkbench.agent.sim.presetDpo')}
              </button>
              <button
                onClick={() => applyPreset({
                  userId: 'hr_manager',
                  datasetId: 'ds_flights_clean',
                  query: t('aiworkbench.agent.sim.presetHotFlightQuery'),
                })}
                className={`px-2 py-0.5 ${styles.badgeBg} hover:opacity-80 ${styles.accentBorder} border rounded text-[9px] ${styles.accentText} font-bold`}
              >
                {t('aiworkbench.agent.sim.presetNormal')}
              </button>
            </div>

            {/* Simulation Trigger Button */}
            <button
              onClick={onRunSimulation}
              disabled={isSimulating || !simQuery.trim()}
              className={`w-full py-2 ${styles.accentBg} ${styles.accentHover} text-white rounded-lg font-black text-xs transition-all cursor-pointer flex items-center justify-center gap-1.5 disabled:opacity-50`}
            >
              {isSimulating ? (
                <>
                  <span className="w-3.5 h-3.5 border-2 border-white/30 border-t-white rounded-full animate-spin" />
                  <span>{t('aiworkbench.agent.sim.running')}</span>
                </>
              ) : (
                <>
                  <Icon name="Play" size={11} className="fill-current text-emerald-400" />
                  <span>{t('aiworkbench.agent.sim.runSimulator')}</span>
                </>
              )}
            </button>
          </div>

          {/* Simulation Output Area */}
          <div className="flex-1 overflow-y-auto p-4 space-y-4">
            {isSimulating && (
              <div className={`flex flex-col items-center justify-center py-20 ${styles.cardTextMuted} space-y-3`}>
                <Icon name="ShieldAlert" size={32} className={`${styles.accentText} animate-pulse`} />
                <div className="text-center space-y-1">
                  <p className={`font-extrabold ${styles.cardTextMuted}`}>{t('aiworkbench.agent.sim.evaluating')}</p>
                  <p className="text-[10px]">{t('aiworkbench.agent.sim.evalPipeline')}</p>
                </div>
              </div>
            )}

            {!isSimulating && !simResult && (
              <div className={`flex flex-col items-center justify-center py-24 ${styles.cardTextMuted} text-center space-y-2`}>
                <Icon name="Tv" size={28} className={styles.cardTextMuted} />
                <span className={`font-bold ${styles.cardTextMuted}`}>{t('aiworkbench.agent.sim.idleTitle')}</span>
                <p className={`text-[10px] ${styles.cardTextMuted} max-w-xs`}>{t('aiworkbench.agent.sim.idleHint')}</p>
              </div>
            )}

            {!isSimulating && simResult && (
              <div className="space-y-4">
                {/* Failed state — 空卡 (命中 task 要求的 "推理链路调用失败，请检查 security-engine 是否在线") */}
                {!simResult.success && (
                  <div className={`p-3.5 rounded-xl border ${styles.dangerBg} ${styles.dangerBorder} ${styles.dangerText} flex items-start gap-3`}>
                    <span className={`p-1.5 rounded-lg shrink-0 ${styles.dangerBg} ${styles.dangerText}`}>
                      <Icon name="ShieldAlert" size={18} />
                    </span>
                    <div className="space-y-1 flex-1">
                      <p className="font-black text-xs">{t('aiworkbench.agent.sim.failTitle')}</p>
                      <p className="text-[10px] opacity-80">
                        {t('aiworkbench.agent.sim.emptyState')}
                      </p>
                      {simResult.error && (
                        <p className="text-[10px] font-mono mt-1 bg-black/10 p-1.5 rounded">{simResult.error}</p>
                      )}
                    </div>
                  </div>
                )}

                {/* Success state — 真实响应渲染 */}
                {simResult.success && (
                  <>
                    {/* Overall Status Banner */}
                    <div
                      className={`p-3.5 rounded-xl border flex items-center justify-between ${
                        isGranted(simResult.overallVerdict)
                          ? `${styles.successBg} ${styles.successBorder} ${styles.successText}`
                          : `${styles.dangerBg} ${styles.dangerBorder} ${styles.dangerText}`
                      }`}
                    >
                      <div className="flex items-center gap-2.5">
                        <span
                          className={`p-1.5 rounded-lg shrink-0 ${
                            isGranted(simResult.overallVerdict)
                              ? `${styles.successBg} ${styles.successText}`
                              : `${styles.dangerBg} ${styles.dangerText}`
                          }`}
                        >
                          <Icon
                            name={isGranted(simResult.overallVerdict) ? 'CheckCircle2' : 'ShieldAlert'}
                            size={18}
                          />
                        </span>
                        <div>
                          <p className="font-black text-xs">
                            {t('aiworkbench.agent.sim.gateStatus')}: {simResult.overallVerdict}
                          </p>
                          <p className="text-[10px] opacity-80">{t('aiworkbench.agent.sim.launchline')}</p>
                        </div>
                      </div>
                      <span
                        className={`px-2.5 py-1 text-[10px] font-black rounded-md uppercase tracking-wider ${
                          isGranted(simResult.overallVerdict)
                            ? `${styles.successBg} text-white shadow-xs`
                            : `${styles.dangerBg} text-white shadow-xs`
                        }`}
                      >
                        {isGranted(simResult.overallVerdict)
                          ? t('aiworkbench.agent.sim.passed')
                          : t('aiworkbench.agent.sim.intercepted')}
                      </span>
                    </div>

                    {/* Content summary (if present) */}
                    {simResult.summary && (
                      <div className={`p-3 ${styles.cardBg} border ${styles.cardBorder} rounded-xl text-[11px] ${styles.cardTextMuted} leading-relaxed`}>
                        <span className={`text-[9px] uppercase font-black ${styles.accentText} block mb-1`}>
                          {t('aiworkbench.agent.sim.summaryLabel')}
                        </span>
                        {simResult.summary}
                      </div>
                    )}

                    {/* Latency (if present) */}
                    {simResult.latency && (
                      <div className="flex items-center gap-1.5 text-[9px] font-mono">
                        <Icon name="Clock" size={10} className={styles.cardTextMuted} />
                        <span className={styles.cardTextMuted}>{t('aiworkbench.agent.sim.latency')}: {simResult.latency}</span>
                      </div>
                    )}

                    {/* Nodes Section */}
                    {Array.isArray(simResult.nodes) && simResult.nodes.length > 0 && (
                      <div className="space-y-3">
                        <h4 className={`text-[10px] font-extrabold ${styles.cardTextMuted} uppercase tracking-wider flex items-center gap-1`}>
                          <Icon name="Route" size={11} />
                          <span>{t('aiworkbench.agent.sim.tracesLog')}</span>
                        </h4>

                        {simResult.nodes.map((node: any) => {
                          const isExpanded = expandedNodes[node.id];
                          const nodePassed = isGranted(node.verdict);
                          return (
                            <div
                              key={node.id}
                              className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden shadow-xs`}
                            >
                              {/* Node Title Header */}
                              <div
                                onClick={() => onToggleNode(node.id)}
                                className={`p-3 ${styles.inputBg} flex items-center justify-between cursor-pointer select-none border-b ${styles.cardBorder}`}
                              >
                                <div className="flex items-center gap-2">
                                  <span
                                    className={`p-1 rounded-md text-[9px] font-bold ${
                                      nodePassed
                                        ? `${styles.successBg} ${styles.successText}`
                                        : `${styles.dangerBg} ${styles.dangerText}`
                                    }`}
                                  >
                                    <Icon name={nodePassed ? 'Check' : 'X'} size={10} />
                                  </span>
                                  <span className={`font-extrabold ${styles.cardText} text-[11px]`}>{node.name}</span>
                                </div>
                                <div className="flex items-center gap-2">
                                  <span
                                    className={`px-1.5 py-0.5 text-[8px] font-bold rounded uppercase ${
                                      nodePassed
                                        ? `${styles.successBg} ${styles.successText}`
                                        : `${styles.dangerBg} ${styles.dangerText}`
                                    }`}
                                  >
                                    {node.verdict}
                                  </span>
                                  <Icon
                                    name={isExpanded ? 'ChevronDown' : 'ChevronRight'}
                                    size={12}
                                    className={styles.cardTextMuted}
                                  />
                                </div>
                              </div>

                              {/* Expanded Node Content */}
                              {isExpanded && (
                                <div className={`p-3 space-y-3 ${styles.cardBg} text-[10px]`}>
                                  {/* Custom payload (rag / llm / firewall 等结构) */}
                                  <div
                                    className={`${styles.appBg} ${styles.cardTextMuted} p-2.5 rounded-lg font-mono text-[9px] leading-relaxed space-y-1`}
                                  >
                                    <span className={`text-[8px] ${styles.accentText} uppercase font-extrabold block mb-1`}>
                                      {t('aiworkbench.agent.sim.stepLogs')}
                                    </span>
                                    {(node.traces && node.traces.length > 0 ? node.traces : ['—']).map(
                                      (trace: string, tIdx: number) => {
                                        const isErr = String(trace).includes('❌') || String(trace).includes('FAIL');
                                        const isAlert = String(trace).includes('⚠️');
                                        return (
                                          <div
                                            key={tIdx}
                                            className={`flex items-start gap-1 ${
                                              isErr ? styles.dangerText : isAlert ? styles.warningText : ''
                                            }`}
                                          >
                                            <span className={`${styles.cardTextMuted} shrink-0`}>▶</span>
                                            <span>{trace}</span>
                                          </div>
                                        );
                                      }
                                    )}
                                  </div>
                                </div>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    )}
                  </>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
