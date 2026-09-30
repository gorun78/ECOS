/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

// Analytics (distribution aggregation) tab of BusinessObjectExplorer.
// Extracted verbatim by H6-T4.

import React from 'react';
import LucideIcon from './LucideIcon';
import { useTheme } from '../../components/ThemeContext';

interface ExplorerAnalyticsProps {
  analyticsData: any;
  processedCount: number;
  onDrillFilter: (propertyId: string, value: string) => void;
}

export function ExplorerAnalytics({ analyticsData, processedCount, onDrillFilter }: ExplorerAnalyticsProps) {
  const { styles } = useTheme();

  return (
    <div className={`flex-1 ${styles.cardBg} p-6 space-y-6 overflow-y-auto`}>
      <div className="space-y-1">
        <h3 className={`text-xs font-semibold ${styles.cardText} flex items-center gap-1.5`}>
          <LucideIcon name="AreaChart" size={14} className="text-blue-600" />
          分布聚合分析 (Categorical Distribution)
        </h3>
        <p className={`text-[10px] ${styles.cardTextMuted}`}>
          智能分析当前筛选规则下的数据集。基于标准关键枚举属性<strong>「{analyticsData ? (analyticsData as any).property?.displayName : ''}」</strong>进行快速分组及分布统计。
        </p>
      </div>

      {processedCount === 0 ? (
        <div className={`text-center py-20 ${styles.cardTextMuted}`}>暂无数据用以绘图统计。</div>
      ) : (
        <div className="grid grid-cols-2 gap-8 items-start">
          {/* Custom visual distribution bars */}
          <div className={`border ${styles.appBorder} rounded-xl p-5 space-y-3 shadow-3xs ${styles.appBg}`}>
            <h4 className={`text-[11px] font-semibold ${styles.cardText}`}>条形占比统计图 (点击柱体可直接追加筛选)</h4>
            <div className="space-y-3 pt-2">
              {(analyticsData as any).data?.map((item: any) => (
                <div
                  key={item.name}
                  onClick={() => onDrillFilter((analyticsData as any).property.id, item.name)}
                  className="group cursor-pointer space-y-1"
                >
                  <div className="flex justify-between text-[11px]">
                    <span className={`font-medium ${styles.cardText} group-hover:text-blue-600 font-mono transition-colors`}>{item.name}</span>
                    <span className={`${styles.cardTextMuted} font-mono`}><strong>{item.count}</strong> 个 ({item.percentage}%)</span>
                  </div>
                  <div className={`h-4 w-full ${styles.appBg} rounded overflow-hidden flex`}>
                    <div
                      style={{ width: `${item.percentage}%` }}
                      className="bg-blue-600 group-hover:bg-blue-500 transition-all rounded-r duration-500"
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Summary table list */}
          <div className={`border ${styles.appBorder} rounded-xl p-5 space-y-3 ${styles.cardBg}`}>
            <h4 className={`text-[11px] font-semibold ${styles.cardText}`}>分组计数表</h4>
            <table className="w-full text-left border-collapse text-[11px]">
              <thead>
                <tr className={`border-b ${styles.divider} ${styles.cardTextMuted}`}>
                  <th className="pb-2">分组类别</th>
                  <th className="pb-2 text-right">实例数</th>
                  <th className="pb-2 text-right">所占比例</th>
                </tr>
              </thead>
              <tbody className={`divide-y ${styles.divider} ${styles.sidebarText}`}>
                {(analyticsData as any).data?.map((item: any) => (
                  <tr key={item.name} className="hover:bg-blue-50/20">
                    <td className={`py-2 font-mono ${styles.cardText} font-medium`}>{item.name}</td>
                    <td className={`py-2 text-right font-mono font-semibold ${styles.text}`}>{item.count}</td>
                    <td className={`py-2 text-right font-mono ${styles.cardTextMuted}`}>{item.percentage}%</td>
                  </tr>
                ))}
                <tr className={`border-t ${styles.appBorder} ${styles.text} font-bold`}>
                  <td className="py-2">总计 (Total)</td>
                  <td className="py-2 text-right font-mono">{processedCount}</td>
                  <td className="py-2 text-right font-mono">100.0%</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
