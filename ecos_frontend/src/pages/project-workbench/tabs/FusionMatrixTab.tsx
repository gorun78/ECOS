/**
 * ECOS 场景工作台 — fusion Tab（按 §0.6.2 子图分层视图）
 * 从 ScenarioManagementView.tsx L1067-1262 拆分。
 * 引用父级状态: ['activeScenario']
 *
 * 结构依据（架构铁律 §0.6.2）：
 * - **主链（4 节点有向）**：DATASET → OBJECT_TYPE → KNOWLEDGE_BASE → AI_AGENT
 *   边 = 格 ge / 致 zhi / 诚 cheng
 * - **横切层（非链上一环）**：SECURITY_POLICY 包裹整条主链
 * - **出口（链外）**：INTERFACE 从 AI_AGENT 引出
 *
 * 覆盖率展示（§0.6.2.3）——真实连边覆盖率（优先用 `coverage`；缺则 fallback 到 0）。
 */

import React from 'react';

import LucideIcon from '../../../components/LucideIcon';
import { useLanguage } from '../../../components/LanguageContext';
import type { BusinessScenario } from '../types';

interface Props {
  activeScenario: BusinessScenario;
}

/** 主链节点配置（按土D → 金I → 水K → 火W 顺序） */
const CHAIN_NODES = [
  {
    key: 'datasets',
    titleKey: 'scenario.fusion.node.datasets.title',
    layerKey: 'scenario.fusion.node.datasets.layer',
    icon: 'Database' as const,
    badge: 'bg-blue-950 text-blue-400',
  },
  {
    key: 'objectTypes',
    titleKey: 'scenario.fusion.node.objectTypes.title',
    layerKey: 'scenario.fusion.node.objectTypes.layer',
    icon: 'Boxes' as const,
    badge: 'bg-indigo-950 text-indigo-400',
  },
  {
    key: 'knowledgeBases',
    titleKey: 'scenario.fusion.node.knowledgeBases.title',
    layerKey: 'scenario.fusion.node.knowledgeBases.layer',
    icon: 'BookOpen' as const,
    badge: 'bg-amber-950 text-amber-400',
  },
  {
    key: 'aiAgents',
    titleKey: 'scenario.fusion.node.aiAgents.title',
    layerKey: 'scenario.fusion.node.aiAgents.layer',
    icon: 'Bot' as const,
    badge: 'bg-pink-950 text-pink-400',
  },
] as const;

/** 主链边（节点间转化对照 + 契约名） */
const CHAIN_EDGES = [
  { labelKey: 'scenario.fusion.edge.ge.label', hintKey: 'scenario.fusion.edge.ge.hint' },
  { labelKey: 'scenario.fusion.edge.zhi.label', hintKey: 'scenario.fusion.edge.zhi.hint' },
  { labelKey: 'scenario.fusion.edge.cheng.label', hintKey: 'scenario.fusion.edge.cheng.hint' },
] as const;

export default function FusionMatrixTab({ activeScenario }: Props) {
  const { t } = useLanguage();
  const b = activeScenario.bindings;
  const cov = activeScenario.coverage;
  const d2i = cov ? Math.round(cov.d2iCoverage * 100) : 0;
  const k2w = cov ? Math.round(cov.k2wCoverage * 100) : 0;

  const byKey = (key: (typeof CHAIN_NODES)[number]['key']) => b[key];

  return (
    <div className="space-y-4">
      {/* §0.6.2 分层视图：横切外框包主链 + 出口 */}
      <div className="bg-[var(--card,#020617)] border border-[var(--card,#1E293B)] p-4 rounded-xl">
        <span className="text-xs font-extrabold text-[var(--card,#CBD5E1)] flex items-center gap-1.5 mb-3">
          <LucideIcon name="Workflow" size={14} className="text-indigo-400" />
          {t('scenario.fusion.blockTitle')}
        </span>

        {/* 横切外框：SECURITY_POLICY 包裹主链 */}
        <div className="relative rounded-lg border-2 border-dashed border-rose-900/60 p-4">
          {/* 横切标签 */}
          <div className="absolute -top-3 left-4 bg-[var(--card,#020617)] px-2 flex items-center gap-1">
            <LucideIcon name="ShieldAlert" size={12} className="text-rose-400" />
            <span className="text-[10px] font-bold text-rose-300">{t('scenario.fusion.crossLabel')}</span>
          </div>

          {/* 主链 + 出口 */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-3 items-center relative text-center">
            {CHAIN_NODES.map((node, idx) => (
              <React.Fragment key={node.key}>
                <div className="bg-[var(--card,#0F172A)]/90 border border-[var(--card,#1E293B)] p-3 rounded-lg flex flex-col items-center">
                  <div className={`p-2 rounded-full ${node.badge} mb-1.5`}>
                    <LucideIcon name={node.icon} size={14} />
                  </div>
                  <span className="text-[10px] font-bold text-[var(--card,#CBD5E1)]">{idx + 1}. {t(node.titleKey)}</span>
                  <span className="text-[9px] text-[var(--card,#64748B)] font-mono mb-1">{t(node.layerKey)}</span>
                  <div className="mt-1 space-y-1 w-full">
                    {byKey(node.key).map(item => (
                      <span key={item}
                        className="block text-[8px] bg-[var(--card,#0B0F19)] px-1 py-0.5 rounded border border-[var(--card,#1E293B)] font-mono text-[var(--card,#94A3B8)] truncate mx-auto">
                        {item}
                      </span>
                    ))}
                    {byKey(node.key).length === 0 && (
                      <span className="block text-[9px] text-[var(--card,#475569)] italic">{t('scenario.fusion.unbound')}</span>
                    )}
                  </div>
                </div>

                {idx < CHAIN_NODES.length - 1 && (
                  <div className="hidden md:flex flex-col items-center pt-10">
                    <LucideIcon name="ArrowRight" size={16} className="text-indigo-500" />
                    <span className="text-[8px] font-mono text-indigo-400 mt-0.5">{t(CHAIN_EDGES[idx].labelKey)}</span>
                    <span className="text-[8px] text-[var(--card,#64748B)]">{t(CHAIN_EDGES[idx].hintKey)}</span>
                  </div>
                )}
              </React.Fragment>
            ))}
          </div>

          {/* 出口（AI_AGENT 链外出口） + 覆盖率徽标 */}
          <div className="mt-3 grid grid-cols-1 md:grid-cols-3 gap-3 items-start">
            <div className="md:col-start-4 self-start bg-[var(--card,#0F172A)]/80 border border-[var(--card,#1E293B)] p-3 rounded-lg flex items-center gap-2 justify-center">
              <LucideIcon name="ArrowDown" size={12} className="text-violet-400" />
              <LucideIcon name="LayoutGrid" size={12} className="text-violet-400" />
              <span className="text-[10px] font-bold text-[var(--card,#CBD5E1)]">{t('scenario.fusion.outlet')}</span>
              <span className="text-[9px] text-[var(--card,#64748B)] font-mono">INTERFACE</span>
              <span className="text-[9px] text-[var(--card,#94A3B8)] truncate">
                {b.interfaces.length ? b.interfaces[b.interfaces.length - 1] : t('scenario.fusion.unbound')}
              </span>
            </div>
            <div className="md:col-start-1 flex items-center gap-2 p-2 bg-[var(--card,#020617)]/60 rounded border border-[var(--card,#1E293B)]">
              <span className="text-[10px] text-[var(--card,#64748B)]">{t('scenario.fusion.covD2I')}</span>
              <span className={`text-xs font-bold font-mono ${d2i >= 60 ? 'text-emerald-400' : 'text-amber-400'}`}>{d2i}%</span>
            </div>
            <div className="md:col-start-2 flex items-center gap-2 p-2 bg-[var(--card,#020617)]/60 rounded border border-[var(--card,#1E293B)]">
              <span className="text-[10px] text-[var(--card,#64748B)]">{t('scenario.fusion.covK2W')}</span>
              <span className={`text-xs font-bold font-mono ${k2w >= 60 ? 'text-emerald-400' : 'text-amber-400'}`}>{k2w}%</span>
            </div>

            {/* 横切安全策略（chips） */}
            <div className="md:col-span-9 md:col-start-1 flex flex-wrap items-center gap-1.5 p-2 bg-[var(--card,#020617)]/60 rounded border border-[var(--card,#1E293B)]">
              <span className="text-[10px] text-rose-300 font-bold flex items-center gap-1">
                <LucideIcon name="ShieldAlert" size={10} />
                {t('scenario.fusion.crossInline')}
              </span>
              {b.securityPolicies.map(s => (
                <span key={s} className="text-[9px] bg-[var(--card,#0B0F19)] px-2 py-0.5 rounded-full border border-rose-900/40 font-mono text-[var(--card,#94A3B8)]">
                  {s}
                </span>
              ))}
              {b.securityPolicies.length === 0 && <span className="text-[9px] text-[var(--card,#475569)] italic">{t('scenario.fusion.unbound')}</span>}
            </div>
          </div>
        </div>
      </div>

      {/* 场景战略目标与业务痛点（沿用） */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <div className="bg-[var(--card,#0F172A)] border border-[var(--card,#1E293B)] p-4 rounded-xl flex flex-col justify-between">
          <div>
            <span className="text-xs font-bold text-[var(--card,#94A3B8)] flex items-center gap-1">
              <LucideIcon name="Goal" size={12} className="text-blue-500" />
              {t('scenario.fusion.goalTitle')}
            </span>
            <h3 className="text-sm font-bold text-white mt-2 leading-snug">{activeScenario.businessGoal}</h3>
            <p className="text-xs text-[var(--card,#94A3B8)] mt-2 leading-relaxed">
              {t('scenario.fusion.goalDesc')}
            </p>
          </div>

          <div className="mt-4 bg-[var(--card,#020617)]/60 p-3 rounded border border-[var(--card,#1E293B)] text-[11px] leading-relaxed">
            💡 {t('scenario.fusion.orchestrateHint')}
          </div>
        </div>

        <div className="bg-[var(--card,#0F172A)] border border-[var(--card,#1E293B)] p-4 rounded-xl">
          <span className="text-xs font-bold text-[var(--card,#94A3B8)] flex items-center gap-1 mb-3">
            <LucideIcon name="PieChart" size={12} className="text-indigo-400" />
            {t('scenario.fusion.integrityTitle')}
          </span>

          <div className="space-y-4">
            <div>
              <div className="flex justify-between text-xs font-mono mb-1.5">
                <span className="text-[var(--card,#94A3B8)] flex items-center gap-1">
                  <span className="w-1.5 h-1.5 rounded-full bg-blue-500" />
                  {t('scenario.fusion.mappingEdge')}
                </span>
                <span className="font-bold text-white">{activeScenario.metrics.mappingCompleteness}%</span>
              </div>
              <div className="w-full bg-[var(--card,#1E293B)] h-2 rounded-full overflow-hidden">
                <div className="bg-blue-500 h-full rounded-full transition-all duration-500" style={{ width: `${activeScenario.metrics.mappingCompleteness}%` }} />
              </div>
            </div>

            <div>
              <div className="flex justify-between text-xs font-mono mb-1.5">
                <span className="text-[var(--card,#94A3B8)] flex items-center gap-1">
                  <span className="w-1.5 h-1.5 rounded-full bg-pink-500" />
                  {t('scenario.fusion.cognitionEdge')}
                </span>
                <span className="font-bold text-white">{activeScenario.metrics.integrityScore}%</span>
              </div>
              <div className="w-full bg-[var(--card,#1E293B)] h-2 rounded-full overflow-hidden">
                <div className="bg-pink-500 h-full rounded-full transition-all duration-500" style={{ width: `${activeScenario.metrics.integrityScore}%` }} />
              </div>
            </div>

            {cov && (
              <div className="text-[10px] text-[var(--card,#64748B)] pt-1 border-t border-[var(--card,#1E293B)] flex gap-4">
                <span>{t('scenario.fusion.statNodes')} {cov.totalNodes}</span>
                <span>{t('scenario.fusion.statEdges')} {cov.totalLinks}</span>
                <span>{t('scenario.fusion.statSource')} /graph</span>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
