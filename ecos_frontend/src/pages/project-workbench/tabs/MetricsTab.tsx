/**
 * ECOS 项目工作台 — metrics Tab
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
import type { BusinessScenario } from '../types';

interface Props {
  threatRadarData: any[];
  efficiencyData: any[];
}

export default function MetricsTab({ threatRadarData, efficiencyData }: Props) {
  return (
<div className="space-y-4">

  {/* 1. Charts Row */}
  <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
    {/* Chart 1: Radar Area - Security Threats Blocked */}
    <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl">
      <span className="text-xs font-bold text-slate-400 flex items-center gap-1 mb-4">
        <LucideIcon name="Activity" size={12} className="text-rose-500 animate-pulse" />
        本月安全拦截审计分类雷达 (Blocked Policy Violations)
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
      <p className="text-[10px] text-slate-500 text-center mt-2">
        * 数据由底层安全审计控制器 `app.get('/api/security/audit-logs')` 实时导出并汇总。
      </p>
    </div>

    {/* Chart 2: Area Line - Delay Reschedule duration optimization */}
    <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl">
      <span className="text-xs font-bold text-slate-400 flex items-center gap-1 mb-4">
        <LucideIcon name="TrendingUp" size={12} className="text-emerald-500" />
        雷雨天气航班改派决策时效趋势评估 (Decision Efficiency (Minutes))
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
            <Area type="monotone" name="传统手工改错核对时效" dataKey="durationSimulated" stroke="#f43f5e" strokeWidth={1.5} fillOpacity={1} fill="url(#colorSim)" />
            <Area type="monotone" name="ECOS 本体自动化审批时效" dataKey="durationReal" stroke="#10b981" strokeWidth={2} fillOpacity={1} fill="url(#colorReal)" />
          </AreaChart>
        </ResponsiveContainer>
      </div>
      <p className="text-[10px] text-slate-500 text-center mt-2">
        * 引入双引擎自动核对签名流程后，暑假恶劣天气调配平均时效已从 <b>45 分钟</b> 暴降至 <b>3.5 秒级</b>。
      </p>
    </div>
  </div>

  {/* 2. Executive PM Assessment Notes */}
  <div className="bg-slate-950 p-4 border border-slate-800 rounded-xl space-y-2">
    <span className="text-xs font-bold text-white flex items-center gap-1.5">
      <LucideIcon name="NotebookTabs" size={13} className="text-indigo-400" />
      企业高级运营总监综合评估结论 (Corporate Executive Assessment)
    </span>
    <div className="text-xs text-slate-300 leading-relaxed space-y-2 font-sans">
      <p>
        1. <b>数据流动性良好</b>：本月对账主链路（Doris 清洗物理宽表 `ds_flights_clean` 到本体逻辑 `AviationFlight`）数据映射一致性维持在 <b>98%</b> 极佳水平。未发生逻辑契约破坏导致的写入异常。
      </p>
      <p>
        2. <b>安全边界坚不可摧</b>：由于强制绑定了 `gr-pii` 与 `gr-approval` 零信任阻断规则，外部维修承包商(EXTERNAL_CONTRACTOR)从非白名单网关尝试暴力导出的明文飞行员 SSN 及薪酬记录已被 RUST 防护层全部 100% 成功脱敏。
      </p>
      <p>
        3. <b>待优化项</b>：eVTOL 低空场景仍处于 `DRAFT` 阶段。下一步需建立 eVTOL 新型无人飞行器的物理测绘遥感表，并设计「城市气象实体（MeteoEntity）」和「航站停机位（Vertiport）」的多对多本体对齐。
      </p>
    </div>
  </div>

</div>
  );
}
