/**
 * ECOS 场景工作台 — metrics Tab
 * 从 ScenarioManagementView.tsx L1507-1594 拆分。
 * 引用的父级状态: ['efficiencyData', 'threatRadarData']
 */

import React from 'react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid,
  Tooltip as RechartsTooltip, ResponsiveContainer,
  AreaChart, Area, Cell
} from 'recharts';

import LucideIcon from '../../../components/LucideIcon';
import { useLanguage } from '../../../components/LanguageContext';
import type { BusinessScenario } from '../types';

interface Props {
  threatRadarData: any[];
  efficiencyData: any[];
}

export default function MetricsTab({ threatRadarData, efficiencyData }: Props) {
  const { t } = useLanguage();
  return (
<div className="space-y-4">

  {/* 1. Charts Row */}
  <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
    {/* Chart 1: Radar Area - Security Threats Blocked */}
    <div className="bg-[var(--card,#0F172A)] border border-[var(--card,#1E293B)] p-4 rounded-xl">
      <span className="text-xs font-bold text-[var(--card,#94A3B8)] flex items-center gap-1 mb-4">
        <LucideIcon name="Activity" size={12} className="text-rose-500 animate-pulse" />
        {t('scenario.metrics.radarTitle')}
      </span>

      <div className="h-60 flex items-center justify-center">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={threatRadarData} margin={{ top: 10, right: 10, left: -20, bottom: 5 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
            <XAxis dataKey="name" stroke="#64748b" fontSize={10} tickLine={false} />
            <YAxis stroke="#64748b" fontSize={10} tickLine={false} />
            <RechartsTooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', color: '#f8fafc' }} />
            <Bar dataKey="count" fill="#8884d8" radius={[4, 4, 0, 0]}>
              {threatRadarData.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={entry.color} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
      <p className="text-[10px] text-[var(--card,#64748B)] text-center mt-2">
        {t('scenario.metrics.radarSource')}
      </p>
    </div>

    {/* Chart 2: Area Line - Delay Reschedule duration optimization */}
    <div className="bg-[var(--card,#0F172A)] border border-[var(--card,#1E293B)] p-4 rounded-xl">
      <span className="text-xs font-bold text-[var(--card,#94A3B8)] flex items-center gap-1 mb-4">
        <LucideIcon name="TrendingUp" size={12} className="text-emerald-500" />
        {t('scenario.metrics.trendTitle')}
      </span>

      <div className="h-60">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={efficiencyData} margin={{ top: 10, right: 10, left: -20, bottom: 5 }}>
            <defs>
              <linearGradient id="colorReal" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#10b981" stopOpacity={0.3}/>
                <stop offset="95%" stopColor="#10b981" stopOpacity={0}/>
              </linearGradient>
              <linearGradient id="colorSim" x1="0" y1="0" x2="0" y2="1">
                <stop offset="5%" stopColor="#f43f5e" stopOpacity={0.1}/>
                <stop offset="95%" stopColor="#f43f5e" stopOpacity={0}/>
              </linearGradient>
            </defs>
            <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
            <XAxis dataKey="name" stroke="#64748b" fontSize={10} tickLine={false} />
            <YAxis stroke="#64748b" fontSize={10} tickLine={false} />
            <RechartsTooltip contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', color: '#f8fafc' }} />
            <Area type="monotone" name={t('scenario.metrics.legendManual')} dataKey="durationSimulated" stroke="#f43f5e" strokeWidth={1.5} fillOpacity={1} fill="url(#colorSim)" />
            <Area type="monotone" name={t('scenario.metrics.legendAuto')} dataKey="durationReal" stroke="#10b981" strokeWidth={2} fillOpacity={1} fill="url(#colorReal)" />
          </AreaChart>
        </ResponsiveContainer>
      </div>
      <p className="text-[10px] text-[var(--card,#64748B)] text-center mt-2">
        {t('scenario.metrics.trendFootnote')}
      </p>
    </div>
  </div>

  {/* 2. Executive PM Assessment Notes */}
  <div className="bg-[var(--card,#020617)] p-4 border border-[var(--card,#1E293B)] rounded-xl space-y-2">
    <span className="text-xs font-bold text-white flex items-center gap-1.5">
      <LucideIcon name="NotebookTabs" size={13} className="text-indigo-400" />
      {t('scenario.metrics.execTitle')}
    </span>
    <div className="text-xs text-[var(--card,#CBD5E1)] leading-relaxed space-y-2 font-sans">
      <p>
        {t('scenario.metrics.execDataFlow')}
      </p>
      <p>
        {t('scenario.metrics.execSecurity')}
      </p>
      <p>
        {t('scenario.metrics.execOptimization')}
      </p>
    </div>
  </div>

</div>
  );
}
