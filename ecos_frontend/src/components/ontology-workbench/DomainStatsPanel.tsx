/**
 * DomainStatsPanel — 域统计面板
 *
 * 对应设计文档第5章组件树中的 DomainStatsPanel：
 * ┌─────────────────┐
 * │ 统计卡片         │
 * │                 │
 * │ 实体总数: 42    │
 * │ 关系总数: 87    │
 * │ 业务域数: 6     │
 * └─────────────────┘
 *
 * Props:
 * - totalEntities: 实体总数
 * - totalRelationships: 关系总数
 * - totalDomains: 业务域数量
 *
 * 样式: Tailwind + dark theme
 * 卡片: bg-[#141924], rounded-lg, 深色主题
 *
 * @license Apache-2.0
 */

import React from 'react';
import { Box, GitBranch, Building2, Network } from 'lucide-react';

// ── Props ──

export interface DomainStatsPanelProps {
  /** 实体总数 */
  totalEntities: number;
  /** 关系总数 */
  totalRelationships: number;
  /** 业务域数量 */
  totalDomains: number;
}

// ── 单个统计卡片 ──

interface StatCardProps {
  icon: React.FC<{ size?: number; className?: string }>;
  label: string;
  value: number;
  /** 主题色: indigo | emerald | amber | purple */
  color: 'indigo' | 'emerald' | 'amber' | 'purple';
}

const COLOR_MAP: Record<StatCardProps['color'], { bg: string; text: string; border: string; icon: string }> = {
  indigo: {
    bg: 'bg-indigo-500/10',
    text: 'text-indigo-400',
    border: 'border-indigo-500/20',
    icon: 'text-indigo-400',
  },
  emerald: {
    bg: 'bg-emerald-500/10',
    text: 'text-emerald-400',
    border: 'border-emerald-500/20',
    icon: 'text-emerald-400',
  },
  amber: {
    bg: 'bg-amber-500/10',
    text: 'text-amber-400',
    border: 'border-amber-500/20',
    icon: 'text-amber-400',
  },
  purple: {
    bg: 'bg-purple-500/10',
    text: 'text-purple-400',
    border: 'border-purple-500/20',
    icon: 'text-purple-400',
  },
};

function StatCard({ icon: Icon, label, value, color }: StatCardProps) {
  const c = COLOR_MAP[color];

  return (
    <div className={`rounded-lg border ${c.border} ${c.bg} p-4 flex items-center gap-3 transition-colors hover:border-opacity-60`}>
      {/* 图标 */}
      <div className={`p-2 rounded-lg ${c.bg} ${c.icon}`}>
        <Icon size={18} />
      </div>

      {/* 标签 + 数值 */}
      <div className="flex-1 min-w-0">
        <p className="text-[10px] text-slate-500 uppercase tracking-wider font-medium">
          {label}
        </p>
        <p className={`text-xl font-bold ${c.text} tabular-nums`}>
          {value.toLocaleString()}
        </p>
      </div>
    </div>
  );
}

// ── 主组件 ──

export default function DomainStatsPanel({
  totalEntities,
  totalRelationships,
  totalDomains,
}: DomainStatsPanelProps) {
  // 计算健康度评分（简单的加权评分，范围 0-100）
  const healthScore = React.useMemo(() => {
    if (totalEntities === 0) return 0;
    // 关系/实体比: 理想 > 1.0
    const relRatio = totalRelationships / Math.max(totalEntities, 1);
    // 域内实体密度
    const entitiesPerDomain = totalDomains > 0 ? totalEntities / totalDomains : 0;

    // 基础分: 有数据就给 40
    let score = 40;
    // 关系丰富度: relRatio 贡献最高 30 分
    score += Math.min(relRatio * 20, 30);
    // 域内密度贡献最高 30 分（4-8 实体/域为理想）
    if (entitiesPerDomain >= 3 && entitiesPerDomain <= 10) {
      score += 30;
    } else if (entitiesPerDomain > 0) {
      score += Math.min(entitiesPerDomain * 3, 30);
    }

    return Math.round(Math.min(score, 100));
  }, [totalEntities, totalRelationships, totalDomains]);

  // 健康度颜色
  const healthColor = healthScore >= 70
    ? 'text-emerald-400'
    : healthScore >= 40
      ? 'text-amber-400'
      : 'text-red-400';

  const healthBg = healthScore >= 70
    ? 'bg-emerald-500/10 border-emerald-500/20'
    : healthScore >= 40
      ? 'bg-amber-500/10 border-amber-500/20'
      : 'bg-red-500/10 border-red-500/20';

  return (
    <div className="p-4 space-y-4">
      {/* 面板标题 */}
      <div className="flex items-center gap-2">
        <Network size={15} className="text-indigo-400" />
        <h3 className="text-sm font-bold text-white">图谱统计</h3>
      </div>

      {/* 统计卡片网格 */}
      <div className="grid grid-cols-1 gap-2.5">
        <StatCard
          icon={Box}
          label="实体总数"
          value={totalEntities}
          color="indigo"
        />
        <StatCard
          icon={GitBranch}
          label="关系总数"
          value={totalRelationships}
          color="emerald"
        />
        <StatCard
          icon={Building2}
          label="业务域数"
          value={totalDomains}
          color="amber"
        />
      </div>

      {/* 健康度评分 */}
      <div className={`rounded-lg border ${healthBg} p-4`}>
        <p className="text-[10px] text-slate-500 uppercase tracking-wider font-medium mb-2">
          健康度评分
        </p>
        <div className="flex items-end gap-2">
          <span className={`text-2xl font-bold ${healthColor}`}>
            {healthScore}
          </span>
          <span className="text-xs text-slate-500 mb-1">/ 100</span>
        </div>

        {/* 进度条 */}
        <div className="mt-2 w-full h-1.5 rounded-full bg-slate-700 overflow-hidden">
          <div
            className={`h-full rounded-full transition-all duration-500 ${
              healthScore >= 70
                ? 'bg-emerald-500'
                : healthScore >= 40
                  ? 'bg-amber-500'
                  : 'bg-red-500'
            }`}
            style={{ width: `${healthScore}%` }}
          />
        </div>

        {/* 评分说明 */}
        <p className="text-[9px] text-slate-600 mt-1.5 leading-relaxed">
          {healthScore >= 70
            ? '知识图谱结构良好，实体与关系分布合理'
            : healthScore >= 40
              ? '图谱结构基本合理，建议补充更多关系连线'
              : '图谱数据量较少，建议添加更多实体和关系'
          }
        </p>
      </div>

      {/* 数据指标明细 */}
      {totalEntities > 0 && (
        <div className="pt-2 border-t border-[#1E293B]">
          <h4 className="text-[10px] font-semibold text-slate-500 uppercase tracking-wider mb-2">
            指标明细
          </h4>
          <div className="space-y-1.5 text-xs">
            {/* 关系密度 */}
            <div className="flex items-center justify-between">
              <span className="text-slate-400">关系密度</span>
              <span className="text-slate-300 font-mono tabular-nums">
                {(totalRelationships / Math.max(totalEntities, 1)).toFixed(2)}
              </span>
            </div>
            {/* 域均实体 */}
            <div className="flex items-center justify-between">
              <span className="text-slate-400">域均实体</span>
              <span className="text-slate-300 font-mono tabular-nums">
                {totalDomains > 0
                  ? (totalEntities / totalDomains).toFixed(1)
                  : 'N/A'}
              </span>
            </div>
            {/* 实体密集度（max possible edges） */}
            <div className="flex items-center justify-between">
              <span className="text-slate-400">图密度</span>
              <span className="text-slate-300 font-mono tabular-nums">
                {totalEntities > 1
                  ? ((totalRelationships / (totalEntities * (totalEntities - 1))) * 100).toFixed(1) + '%'
                  : 'N/A'}
              </span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
